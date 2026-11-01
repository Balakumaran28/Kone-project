package com.liftcare.service;

import com.liftcare.dto.DashboardSummaryResponse;
import com.liftcare.dto.IncidentResponse;
import com.liftcare.exception.ResourceNotFoundException;
import com.liftcare.model.ElevatorOperationalStatus;
import com.liftcare.model.Incident;
import com.liftcare.model.IncidentStatus;
import com.liftcare.model.SeverityLevel;
import com.liftcare.repository.BuildingRepository;
import com.liftcare.repository.ElevatorRepository;
import com.liftcare.repository.IncidentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    public static final List<IncidentStatus> ACTIVE_STATUSES = List.of(
            IncidentStatus.REPORTED,
            IncidentStatus.ACKNOWLEDGED,
            IncidentStatus.ASSIGNED,
            IncidentStatus.IN_PROGRESS
    );

    private final IncidentRepository incidentRepository;
    private final ElevatorRepository elevatorRepository;
    private final BuildingRepository buildingRepository;

    @Value("${liftcare.node-id:building-local-node-01}")
    private String localNodeId;

    public DashboardService(
            IncidentRepository incidentRepository,
            ElevatorRepository elevatorRepository,
            BuildingRepository buildingRepository) {
        this.incidentRepository = incidentRepository;
        this.elevatorRepository = elevatorRepository;
        this.buildingRepository = buildingRepository;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getDashboardSummary(Long buildingId) {
        if (buildingId != null && !buildingRepository.existsById(buildingId)) {
            throw new ResourceNotFoundException("Building not found with id: " + buildingId);
        }

        long totalActive;
        long critical;
        long high;
        long awaitingAck;
        long assigned;
        long inProgress;
        long activeEntrapments;
        long outOfServiceElevators;
        long maintenanceElevators;
        long activeElevators;
        long totalElevators;
        long totalBuildings;

        long approachingDeadlines;
        long responseBreached;
        long resolutionBreached;
        long escTier1;
        long escTier2;
        long escTier3;
        long missingPolicy;

        List<Incident> entrapments;
        List<Incident> criticals;

        if (buildingId != null) {
            totalActive = incidentRepository.countByBuildingIdAndStatusIn(buildingId, ACTIVE_STATUSES);
            critical = incidentRepository.countByBuildingIdAndStatusInAndSeverity(buildingId, ACTIVE_STATUSES, SeverityLevel.CRITICAL);
            high = incidentRepository.countByBuildingIdAndStatusInAndSeverity(buildingId, ACTIVE_STATUSES, SeverityLevel.HIGH);
            awaitingAck = incidentRepository.countByBuildingIdAndStatus(buildingId, IncidentStatus.REPORTED);
            assigned = incidentRepository.countByBuildingIdAndStatus(buildingId, IncidentStatus.ASSIGNED);
            inProgress = incidentRepository.countByBuildingIdAndStatus(buildingId, IncidentStatus.IN_PROGRESS);
            activeEntrapments = incidentRepository.countByBuildingIdAndStatusInAndEntrapmentReportedTrue(buildingId, ACTIVE_STATUSES);

            outOfServiceElevators = elevatorRepository.countByBuildingIdAndOperationalStatus(buildingId, ElevatorOperationalStatus.OUT_OF_SERVICE);
            maintenanceElevators = elevatorRepository.countByBuildingIdAndOperationalStatus(buildingId, ElevatorOperationalStatus.MAINTENANCE);
            activeElevators = elevatorRepository.countByBuildingIdAndOperationalStatus(buildingId, ElevatorOperationalStatus.ACTIVE);
            totalElevators = elevatorRepository.countByBuildingId(buildingId);
            totalBuildings = 1;

            approachingDeadlines = incidentRepository.countByBuildingIdAndStatusInAndSlaWarningSentTrue(buildingId, ACTIVE_STATUSES);
            responseBreached = incidentRepository.countByBuildingIdAndStatusInAndResponseSlaBreachedTrue(buildingId, ACTIVE_STATUSES);
            resolutionBreached = incidentRepository.countByBuildingIdAndStatusInAndResolutionSlaBreachedTrue(buildingId, ACTIVE_STATUSES);
            escTier1 = incidentRepository.countByBuildingIdAndStatusInAndEscalationLevel(buildingId, ACTIVE_STATUSES, 1);
            escTier2 = incidentRepository.countByBuildingIdAndStatusInAndEscalationLevel(buildingId, ACTIVE_STATUSES, 2);
            escTier3 = incidentRepository.countByBuildingIdAndStatusInAndEscalationLevel(buildingId, ACTIVE_STATUSES, 3);
            missingPolicy = incidentRepository.countByBuildingIdAndStatusInAndSlaPolicyIsNull(buildingId, ACTIVE_STATUSES);

            entrapments = incidentRepository.findByBuildingIdAndStatusInAndEntrapmentReportedTrueOrderByReportedAtAsc(buildingId, ACTIVE_STATUSES);
            criticals = incidentRepository.findByBuildingIdAndStatusInAndSeverityOrderByReportedAtAsc(buildingId, ACTIVE_STATUSES, SeverityLevel.CRITICAL);
        } else {
            totalActive = incidentRepository.countByStatusIn(ACTIVE_STATUSES);
            critical = incidentRepository.countByStatusInAndSeverity(ACTIVE_STATUSES, SeverityLevel.CRITICAL);
            high = incidentRepository.countByStatusInAndSeverity(ACTIVE_STATUSES, SeverityLevel.HIGH);
            awaitingAck = incidentRepository.countByStatus(IncidentStatus.REPORTED);
            assigned = incidentRepository.countByStatus(IncidentStatus.ASSIGNED);
            inProgress = incidentRepository.countByStatus(IncidentStatus.IN_PROGRESS);
            activeEntrapments = incidentRepository.countByStatusInAndEntrapmentReportedTrue(ACTIVE_STATUSES);

            outOfServiceElevators = elevatorRepository.countByOperationalStatus(ElevatorOperationalStatus.OUT_OF_SERVICE);
            maintenanceElevators = elevatorRepository.countByOperationalStatus(ElevatorOperationalStatus.MAINTENANCE);
            activeElevators = elevatorRepository.countByOperationalStatus(ElevatorOperationalStatus.ACTIVE);
            totalElevators = elevatorRepository.count();
            totalBuildings = buildingRepository.countByActiveTrue();

            approachingDeadlines = incidentRepository.countByStatusInAndSlaWarningSentTrue(ACTIVE_STATUSES);
            responseBreached = incidentRepository.countByStatusInAndResponseSlaBreachedTrue(ACTIVE_STATUSES);
            resolutionBreached = incidentRepository.countByStatusInAndResolutionSlaBreachedTrue(ACTIVE_STATUSES);
            escTier1 = incidentRepository.countByStatusInAndEscalationLevel(ACTIVE_STATUSES, 1);
            escTier2 = incidentRepository.countByStatusInAndEscalationLevel(ACTIVE_STATUSES, 2);
            escTier3 = incidentRepository.countByStatusInAndEscalationLevel(ACTIVE_STATUSES, 3);
            missingPolicy = incidentRepository.countByStatusInAndSlaPolicyIsNull(ACTIVE_STATUSES);

            entrapments = incidentRepository.findByStatusInAndEntrapmentReportedTrueOrderByReportedAtAsc(ACTIVE_STATUSES);
            criticals = incidentRepository.findByStatusInAndSeverityOrderByReportedAtAsc(ACTIVE_STATUSES, SeverityLevel.CRITICAL);
        }

        // Combine emergency incidents (entrapments first, then other critical incidents), deduplicating
        Set<Long> seenIds = new LinkedHashSet<>();
        List<IncidentResponse> emergencyResponses = new ArrayList<>();

        for (Incident inc : entrapments) {
            if (seenIds.add(inc.getId())) {
                emergencyResponses.add(IncidentResponse.from(inc));
            }
        }
        for (Incident inc : criticals) {
            if (seenIds.add(inc.getId())) {
                emergencyResponses.add(IncidentResponse.from(inc));
            }
        }

        return new DashboardSummaryResponse(
                totalActive,
                critical,
                high,
                awaitingAck,
                assigned,
                inProgress,
                activeEntrapments,
                outOfServiceElevators,
                maintenanceElevators,
                activeElevators,
                totalElevators,
                totalBuildings,
                Instant.now(),
                localNodeId,
                emergencyResponses,
                approachingDeadlines,
                responseBreached,
                resolutionBreached,
                escTier1,
                escTier2,
                escTier3,
                missingPolicy
        );
    }
}
