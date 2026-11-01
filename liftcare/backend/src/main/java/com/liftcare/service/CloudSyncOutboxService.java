package com.liftcare.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liftcare.model.CloudSyncOutbox;
import com.liftcare.model.Elevator;
import com.liftcare.model.Incident;
import com.liftcare.repository.CloudSyncOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class CloudSyncOutboxService {

    private static final Logger log = LoggerFactory.getLogger(CloudSyncOutboxService.class);

    private final CloudSyncOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public CloudSyncOutboxService(CloudSyncOutboxRepository outboxRepository,
                                  ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void queueIncidentCreated(Incident incident) {
        if (incident == null || incident.getBuilding() == null) return;
        try {
            Map<String, Object> payloadMap = buildIncidentPayload(incident, "INCIDENT_CREATED");
            String payloadJson = objectMapper.writeValueAsString(payloadMap);
            String eventId = UUID.randomUUID().toString();
            String idempotencyKey = "SYNC-INC-CREATE-" + incident.getTicketNumber();

            if (!outboxRepository.existsByIdempotencyKey(idempotencyKey)) {
                CloudSyncOutbox outbox = new CloudSyncOutbox(
                        eventId,
                        incident.getBuilding().getBuildingCode(),
                        "INCIDENT_CREATED",
                        "INCIDENT",
                        incident.getTicketNumber(),
                        payloadJson,
                        idempotencyKey
                );
                outboxRepository.save(outbox);
                log.info("Queued cloud sync outbox item for incident created ticket='{}'", incident.getTicketNumber());
            }
        } catch (Exception ex) {
            log.error("Failed to queue incident creation in cloud sync outbox: {}", ex.getMessage());
        }
    }

    @Transactional
    public void queueIncidentUpdated(Incident incident, String updateAction) {
        if (incident == null || incident.getBuilding() == null) return;
        try {
            Map<String, Object> payloadMap = buildIncidentPayload(incident, updateAction);
            String payloadJson = objectMapper.writeValueAsString(payloadMap);
            String eventId = UUID.randomUUID().toString();
            String idempotencyKey = String.format("SYNC-INC-UPD-%s-%s-%d",
                    incident.getTicketNumber(), updateAction, System.currentTimeMillis());

            CloudSyncOutbox outbox = new CloudSyncOutbox(
                    eventId,
                    incident.getBuilding().getBuildingCode(),
                    "INCIDENT_UPDATED",
                    "INCIDENT",
                    incident.getTicketNumber(),
                    payloadJson,
                    idempotencyKey
            );
            outboxRepository.save(outbox);
            log.info("Queued cloud sync outbox item for incident update ticket='{}' action='{}'",
                    incident.getTicketNumber(), updateAction);
        } catch (Exception ex) {
            log.error("Failed to queue incident update in cloud sync outbox: {}", ex.getMessage());
        }
    }

    @Transactional
    public void queueElevatorStatusChanged(Elevator elevator) {
        if (elevator == null || elevator.getBuilding() == null) return;
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("elevatorCode", elevator.getElevatorCode());
            payload.put("buildingCode", elevator.getBuilding().getBuildingCode());
            payload.put("operationalStatus", elevator.getOperationalStatus().name());
            payload.put("updatedAt", Instant.now().toString());

            String payloadJson = objectMapper.writeValueAsString(payload);
            String eventId = UUID.randomUUID().toString();
            String idempotencyKey = String.format("SYNC-ELV-%s-%s-%d",
                    elevator.getElevatorCode(), elevator.getOperationalStatus().name(), System.currentTimeMillis());

            CloudSyncOutbox outbox = new CloudSyncOutbox(
                    eventId,
                    elevator.getBuilding().getBuildingCode(),
                    "ELEVATOR_STATUS_CHANGED",
                    "ELEVATOR",
                    elevator.getElevatorCode(),
                    payloadJson,
                    idempotencyKey
            );
            outboxRepository.save(outbox);
            log.info("Queued cloud sync outbox item for elevator status change code='{}' status={}",
                    elevator.getElevatorCode(), elevator.getOperationalStatus());
        } catch (Exception ex) {
            log.error("Failed to queue elevator status change in cloud sync outbox: {}", ex.getMessage());
        }
    }

    private Map<String, Object> buildIncidentPayload(Incident incident, String action) {
        Map<String, Object> map = new HashMap<>();
        map.put("ticketNumber", incident.getTicketNumber());
        map.put("buildingCode", incident.getBuilding() != null ? incident.getBuilding().getBuildingCode() : "UNKNOWN");
        map.put("buildingName", incident.getBuilding() != null ? incident.getBuilding().getName() : "");
        map.put("elevatorCode", incident.getElevator() != null ? incident.getElevator().getElevatorCode() : "UNKNOWN");
        map.put("categoryName", incident.getIssueCategory() != null ? incident.getIssueCategory().getName() : "General");
        map.put("severity", incident.getSeverity() != null ? incident.getSeverity().name() : "MEDIUM");
        map.put("status", incident.getStatus() != null ? incident.getStatus().name() : "REPORTED");
        map.put("entrapmentReported", incident.isEntrapmentReported());
        map.put("description", incident.getDescription());
        map.put("reportedAt", incident.getReportedAt() != null ? incident.getReportedAt().toString() : Instant.now().toString());
        map.put("acknowledgedAt", incident.getAcknowledgedAt() != null ? incident.getAcknowledgedAt().toString() : null);
        map.put("resolvedAt", incident.getResolvedAt() != null ? incident.getResolvedAt().toString() : null);
        map.put("closedAt", incident.getStatus() != null && incident.getStatus().name().equals("CLOSED") ? Instant.now().toString() : null);
        map.put("assignedTechnicianName", incident.getAssignedTo() != null ? incident.getAssignedTo().getFullName() : null);
        map.put("escalationLevel", incident.getEscalationLevel());
        map.put("responseSlaBreached", incident.isResponseSlaBreached());
        map.put("resolutionSlaBreached", incident.isResolutionSlaBreached());
        map.put("action", action);
        map.put("updatedAt", incident.getUpdatedAt() != null ? incident.getUpdatedAt().toString() : Instant.now().toString());
        return map;
    }
}
