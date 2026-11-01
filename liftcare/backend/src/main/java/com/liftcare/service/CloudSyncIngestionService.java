package com.liftcare.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liftcare.dto.CloudSyncBatchRequest;
import com.liftcare.dto.CloudSyncBatchResponse;
import com.liftcare.dto.SyncEventItemDto;
import com.liftcare.exception.InvalidRequestException;
import com.liftcare.model.CloudIncidentReplica;
import com.liftcare.model.CloudRegisteredBuilding;
import com.liftcare.model.CloudSyncConflict;
import com.liftcare.model.CloudSyncEvent;
import com.liftcare.repository.CloudIncidentReplicaRepository;
import com.liftcare.repository.CloudRegisteredBuildingRepository;
import com.liftcare.repository.CloudSyncConflictRepository;
import com.liftcare.repository.CloudSyncEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class CloudSyncIngestionService {

    private static final Logger log = LoggerFactory.getLogger(CloudSyncIngestionService.class);

    private final CloudRegisteredBuildingRepository buildingRepository;
    private final CloudSyncEventRepository eventRepository;
    private final CloudIncidentReplicaRepository replicaRepository;
    private final CloudSyncConflictRepository conflictRepository;
    private final ObjectMapper objectMapper;

    public CloudSyncIngestionService(CloudRegisteredBuildingRepository buildingRepository,
                                     CloudSyncEventRepository eventRepository,
                                     CloudIncidentReplicaRepository replicaRepository,
                                     CloudSyncConflictRepository conflictRepository,
                                     ObjectMapper objectMapper) {
        this.buildingRepository = buildingRepository;
        this.eventRepository = eventRepository;
        this.replicaRepository = replicaRepository;
        this.conflictRepository = conflictRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CloudSyncBatchResponse ingestBatch(String authenticatedBuildingCode, CloudSyncBatchRequest request) {
        String reqBuildingCode = request.buildingCode().trim().toUpperCase();
        if (!reqBuildingCode.equalsIgnoreCase(authenticatedBuildingCode.trim())) {
            throw new InvalidRequestException(String.format(
                    "Tenant isolation breach: Authenticated building '%s' cannot submit data for building '%s'",
                    authenticatedBuildingCode, reqBuildingCode));
        }

        CloudRegisteredBuilding building = buildingRepository.findByBuildingCode(reqBuildingCode)
                .orElseThrow(() -> new InvalidRequestException("Unregistered building code: " + reqBuildingCode));

        if (!"ACTIVE".equalsIgnoreCase(building.getStatus())) {
            throw new InvalidRequestException("Building registration is suspended for code: " + reqBuildingCode);
        }

        List<String> processedEventIds = new ArrayList<>();
        List<String> failedEventIds = new ArrayList<>();
        int duplicateCount = 0;
        int conflictCount = 0;
        int processedCount = 0;

        for (SyncEventItemDto item : request.events()) {
            try {
                // 1. Idempotency check: has this exact event or idempotency key been processed?
                if (eventRepository.existsByIdempotencyKey(item.idempotencyKey()) ||
                        eventRepository.findByEventId(item.eventId()).isPresent()) {
                    duplicateCount++;
                    processedEventIds.add(item.eventId());
                    log.debug("Duplicate sync event detected: eventId='{}' key='{}'", item.eventId(), item.idempotencyKey());
                    continue;
                }

                // 2. Process aggregate event
                if ("INCIDENT".equalsIgnoreCase(item.aggregateType())) {
                    boolean conflict = processIncidentEvent(reqBuildingCode, item);
                    if (conflict) {
                        conflictCount++;
                    }
                }

                // 3. Record ingested event
                CloudSyncEvent event = new CloudSyncEvent(
                        item.eventId(),
                        reqBuildingCode,
                        item.eventType(),
                        item.aggregateType(),
                        item.aggregateId(),
                        item.payload(),
                        item.idempotencyKey(),
                        conflictCount > 0 ? "CONFLICT" : "PROCESSED"
                );
                eventRepository.save(event);

                processedCount++;
                processedEventIds.add(item.eventId());
            } catch (Exception ex) {
                log.error("Failed to process sync event id='{}': {}", item.eventId(), ex.getMessage());
                failedEventIds.add(item.eventId());
            }
        }

        // Update building telemetry
        building.setLastSyncedAt(Instant.now());
        building.setLastSyncStatus(failedEventIds.isEmpty() ? "SUCCESS" : "PARTIAL");
        building.setLastSyncError(failedEventIds.isEmpty() ? null : "Failed event count: " + failedEventIds.size());
        buildingRepository.save(building);

        String overallStatus = failedEventIds.isEmpty() ? "SUCCESS" :
                (processedCount > 0 || duplicateCount > 0 ? "PARTIAL" : "FAILED");

        return new CloudSyncBatchResponse(
                request.batchId(),
                reqBuildingCode,
                request.events().size(),
                processedCount,
                duplicateCount,
                conflictCount,
                failedEventIds.size(),
                overallStatus,
                processedEventIds,
                failedEventIds,
                Instant.now()
        );
    }

    private boolean processIncidentEvent(String buildingCode, SyncEventItemDto item) {
        try {
            Map<String, Object> data = objectMapper.readValue(item.payload(), new TypeReference<>() {});
            String ticketNumber = (String) data.get("ticketNumber");
            if (ticketNumber == null || ticketNumber.isBlank()) return false;

            Optional<CloudIncidentReplica> existingOpt = replicaRepository.findByTicketNumber(ticketNumber);

            if (existingOpt.isPresent()) {
                CloudIncidentReplica replica = existingOpt.get();
                String incomingStatus = (String) data.get("status");

                // Conflict rule: If cloud replica is CLOSED and incoming event is an older REPORTED or ACKNOWLEDGED
                if ("CLOSED".equalsIgnoreCase(replica.getStatus()) &&
                        ("REPORTED".equalsIgnoreCase(incomingStatus) || "ACKNOWLEDGED".equalsIgnoreCase(incomingStatus))) {
                    CloudSyncConflict conflict = new CloudSyncConflict(
                            UUID.randomUUID().toString(),
                            ticketNumber,
                            buildingCode,
                            "STALE_STATUS_ON_CLOSED_TICKET",
                            item.payload(),
                            objectMapper.writeValueAsString(replica)
                    );
                    conflictRepository.save(conflict);
                    log.warn("Recorded synchronization conflict for closed ticket='{}'", ticketNumber);
                    return true;
                }

                // Upsert replica with incoming authoritative building data
                updateReplicaFromMap(replica, data);
                replica.setLocalVersion(replica.getLocalVersion() + 1);
                replicaRepository.save(replica);
            } else {
                // Create new replica
                CloudIncidentReplica replica = new CloudIncidentReplica();
                replica.setTicketNumber(ticketNumber);
                replica.setBuildingCode(buildingCode);
                updateReplicaFromMap(replica, data);
                replicaRepository.save(replica);
            }
            return false;
        } catch (Exception ex) {
            log.error("Failed to parse incident payload for eventId='{}': {}", item.eventId(), ex.getMessage());
            return false;
        }
    }

    private void updateReplicaFromMap(CloudIncidentReplica replica, Map<String, Object> data) {
        if (data.containsKey("buildingName") && data.get("buildingName") != null)
            replica.setBuildingName((String) data.get("buildingName"));
        if (data.containsKey("elevatorCode") && data.get("elevatorCode") != null)
            replica.setElevatorCode((String) data.get("elevatorCode"));
        if (data.containsKey("categoryName") && data.get("categoryName") != null)
            replica.setCategoryName((String) data.get("categoryName"));
        if (data.containsKey("severity") && data.get("severity") != null)
            replica.setSeverity((String) data.get("severity"));
        if (data.containsKey("status") && data.get("status") != null)
            replica.setStatus((String) data.get("status"));
        if (data.containsKey("entrapmentReported"))
            replica.setEntrapmentReported(Boolean.TRUE.equals(data.get("entrapmentReported")));
        if (data.containsKey("description") && data.get("description") != null)
            replica.setDescription((String) data.get("description"));
        if (data.containsKey("reportedAt") && data.get("reportedAt") != null)
            replica.setReportedAt(Instant.parse((String) data.get("reportedAt")));
        if (data.containsKey("acknowledgedAt") && data.get("acknowledgedAt") != null)
            replica.setAcknowledgedAt(Instant.parse((String) data.get("acknowledgedAt")));
        if (data.containsKey("resolvedAt") && data.get("resolvedAt") != null)
            replica.setResolvedAt(Instant.parse((String) data.get("resolvedAt")));
        if (data.containsKey("closedAt") && data.get("closedAt") != null)
            replica.setClosedAt(Instant.parse((String) data.get("closedAt")));
        if (data.containsKey("assignedTechnicianName"))
            replica.setAssignedTechnicianName((String) data.get("assignedTechnicianName"));
        if (data.containsKey("escalationLevel") && data.get("escalationLevel") != null)
            replica.setEscalationLevel(((Number) data.get("escalationLevel")).intValue());
        if (data.containsKey("responseSlaBreached"))
            replica.setResponseSlaBreached(Boolean.TRUE.equals(data.get("responseSlaBreached")));
        if (data.containsKey("resolutionSlaBreached"))
            replica.setResolutionSlaBreached(Boolean.TRUE.equals(data.get("resolutionSlaBreached")));

        // Ensure non-null column safety defaults
        if (replica.getElevatorCode() == null) replica.setElevatorCode("UNKNOWN");
        if (replica.getCategoryName() == null) replica.setCategoryName("General");
        if (replica.getSeverity() == null) replica.setSeverity("MEDIUM");
        if (replica.getStatus() == null) replica.setStatus("REPORTED");
        if (replica.getDescription() == null) replica.setDescription("Incident synced from building " + replica.getBuildingCode());
        if (replica.getReportedAt() == null) replica.setReportedAt(Instant.now());
    }
}
