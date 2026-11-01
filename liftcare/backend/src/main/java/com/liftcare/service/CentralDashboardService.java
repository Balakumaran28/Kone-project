package com.liftcare.service;

import com.liftcare.dto.BuildingSyncTelemetryDto;
import com.liftcare.dto.CentralDashboardSummaryResponse;
import com.liftcare.dto.ConflictResolutionRequest;
import com.liftcare.exception.ResourceNotFoundException;
import com.liftcare.model.CloudIncidentReplica;
import com.liftcare.model.CloudRegisteredBuilding;
import com.liftcare.model.CloudSyncConflict;
import com.liftcare.repository.CloudIncidentReplicaRepository;
import com.liftcare.repository.CloudRegisteredBuildingRepository;
import com.liftcare.repository.CloudSyncConflictRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class CentralDashboardService {

    private final CloudRegisteredBuildingRepository buildingRepository;
    private final CloudIncidentReplicaRepository replicaRepository;
    private final CloudSyncConflictRepository conflictRepository;

    private static final Set<String> ACTIVE_STATUSES = Set.of("REPORTED", "ACKNOWLEDGED", "ASSIGNED", "IN_PROGRESS");
    private static final Duration STALE_THRESHOLD = Duration.ofMinutes(15);

    public CentralDashboardService(CloudRegisteredBuildingRepository buildingRepository,
                                   CloudIncidentReplicaRepository replicaRepository,
                                   CloudSyncConflictRepository conflictRepository) {
        this.buildingRepository = buildingRepository;
        this.replicaRepository = replicaRepository;
        this.conflictRepository = conflictRepository;
    }

    @Transactional(readOnly = true)
    public CentralDashboardSummaryResponse getSummary() {
        List<CloudRegisteredBuilding> buildings = buildingRepository.findAll();
        long activeBuildings = buildings.stream().filter(b -> "ACTIVE".equalsIgnoreCase(b.getStatus())).count();
        long errorBuildings = buildings.stream().filter(b -> "FAILED".equalsIgnoreCase(b.getLastSyncStatus())).count();

        long totalIncidents = replicaRepository.count();
        long activeIncidents = replicaRepository.countByStatusIn(ACTIVE_STATUSES);
        long criticalIncidents = replicaRepository.countBySeverity("CRITICAL");
        long entrapments = replicaRepository.countByEntrapmentReportedTrue();
        long slaBreaches = replicaRepository.countByResponseSlaBreachedTrue() + replicaRepository.countByResolutionSlaBreachedTrue();
        long pendingConflicts = conflictRepository.countByResolutionAction("PENDING_REVIEW");

        Instant now = Instant.now();
        boolean hasStaleData = false;
        List<BuildingSyncTelemetryDto> telemetryList = new ArrayList<>();

        for (CloudRegisteredBuilding b : buildings) {
            long openCount = replicaRepository.countByBuildingCodeAndStatusIn(b.getBuildingCode(), ACTIVE_STATUSES);
            long critCount = replicaRepository.countByBuildingCodeAndSeverity(b.getBuildingCode(), "CRITICAL");

            boolean isStale = b.getLastSyncedAt() == null || Duration.between(b.getLastSyncedAt(), now).compareTo(STALE_THRESHOLD) > 0;
            if (isStale && "ACTIVE".equalsIgnoreCase(b.getStatus())) {
                hasStaleData = true;
            }

            telemetryList.add(new BuildingSyncTelemetryDto(
                    b.getBuildingCode(),
                    b.getBuildingName(),
                    b.getStatus(),
                    b.getLastSyncedAt(),
                    b.getLastSyncStatus(),
                    b.getLastSyncError(),
                    openCount,
                    critCount
            ));
        }

        return new CentralDashboardSummaryResponse(
                buildings.size(),
                activeBuildings,
                errorBuildings,
                totalIncidents,
                activeIncidents,
                criticalIncidents,
                entrapments,
                slaBreaches,
                pendingConflicts,
                telemetryList,
                hasStaleData,
                now
        );
    }

    @Transactional(readOnly = true)
    public List<CloudIncidentReplica> getIncidentReplicas(String buildingCode, String status, String severity) {
        List<CloudIncidentReplica> list = (buildingCode != null && !buildingCode.isBlank())
                ? replicaRepository.findByBuildingCodeOrderByReportedAtDesc(buildingCode.trim().toUpperCase())
                : replicaRepository.findAllByOrderByReportedAtDesc();

        return list.stream()
                .filter(r -> status == null || status.isBlank() || status.equalsIgnoreCase(r.getStatus()))
                .filter(r -> severity == null || severity.isBlank() || severity.equalsIgnoreCase(r.getSeverity()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CloudSyncConflict> getConflicts(String status) {
        if (status != null && !status.isBlank()) {
            return conflictRepository.findByResolutionActionOrderByCreatedAtDesc(status.trim().toUpperCase());
        }
        return conflictRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public CloudSyncConflict resolveConflict(String conflictId, ConflictResolutionRequest request, String resolvedBy) {
        CloudSyncConflict conflict = conflictRepository.findByConflictId(conflictId)
                .orElseThrow(() -> new ResourceNotFoundException("Conflict record not found with id: " + conflictId));

        conflict.setResolutionAction(request.resolutionAction().trim().toUpperCase());
        conflict.setResolutionNotes(request.resolutionNotes());
        conflict.setResolvedAt(Instant.now());
        conflict.setResolvedBy(resolvedBy);

        return conflictRepository.save(conflict);
    }
}
