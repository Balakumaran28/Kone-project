package com.liftcare.dto;

import com.liftcare.model.Incident;
import com.liftcare.model.IncidentStatus;
import com.liftcare.model.SeverityLevel;

import java.time.Instant;

public record IncidentResponse(
        Long id,
        String ticketNumber,
        Long buildingId,
        String buildingCode,
        String buildingName,
        Long elevatorId,
        String elevatorCode,
        Long reportedById,
        String reportedByName,
        String reportedByEmail,
        Long assignedToId,
        String assignedToName,
        String assignedToEmail,
        Long issueCategoryId,
        String issueCategoryCode,
        String issueCategoryName,
        String description,
        SeverityLevel severity,
        IncidentStatus status,
        boolean entrapmentReported,
        Instant reportedAt,
        Instant updatedAt,
        Instant resolvedAt,
        Instant acknowledgedAt,
        Instant responseDeadline,
        Instant resolutionDeadline,
        int escalationLevel,
        boolean responseSlaBreached,
        boolean resolutionSlaBreached,
        String responseSlaState,
        String resolutionSlaState
) {
    // Backward-compatible 22-argument constructor for legacy callers
    public IncidentResponse(
            Long id,
            String ticketNumber,
            Long buildingId,
            String buildingCode,
            String buildingName,
            Long elevatorId,
            String elevatorCode,
            Long reportedById,
            String reportedByName,
            String reportedByEmail,
            Long assignedToId,
            String assignedToName,
            String assignedToEmail,
            Long issueCategoryId,
            String issueCategoryCode,
            String issueCategoryName,
            String description,
            SeverityLevel severity,
            IncidentStatus status,
            boolean entrapmentReported,
            Instant reportedAt,
            Instant updatedAt,
            Instant resolvedAt) {
        this(
                id, ticketNumber, buildingId, buildingCode, buildingName,
                elevatorId, elevatorCode, reportedById, reportedByName, reportedByEmail,
                assignedToId, assignedToName, assignedToEmail,
                issueCategoryId, issueCategoryCode, issueCategoryName,
                description, severity, status, entrapmentReported,
                reportedAt, updatedAt, resolvedAt,
                null, null, null, 0, false, false, "NOT_APPLICABLE", "NOT_APPLICABLE"
        );
    }

    public static IncidentResponse from(Incident incident) {
        Instant now = Instant.now();
        String respState = incident.calculateResponseSlaState(now).name();
        String resolState = incident.calculateResolutionSlaState(now).name();

        return new IncidentResponse(
                incident.getId(),
                incident.getTicketNumber(),
                incident.getBuilding() != null ? incident.getBuilding().getId() : null,
                incident.getBuilding() != null ? incident.getBuilding().getBuildingCode() : null,
                incident.getBuilding() != null ? incident.getBuilding().getName() : null,
                incident.getElevator() != null ? incident.getElevator().getId() : null,
                incident.getElevator() != null ? incident.getElevator().getElevatorCode() : null,
                incident.getReportedBy() != null ? incident.getReportedBy().getId() : null,
                incident.getReportedBy() != null ? incident.getReportedBy().getFullName() : null,
                incident.getReportedBy() != null ? incident.getReportedBy().getEmail() : null,
                incident.getAssignedTo() != null ? incident.getAssignedTo().getId() : null,
                incident.getAssignedTo() != null ? incident.getAssignedTo().getFullName() : null,
                incident.getAssignedTo() != null ? incident.getAssignedTo().getEmail() : null,
                incident.getIssueCategory() != null ? incident.getIssueCategory().getId() : null,
                incident.getIssueCategory() != null ? incident.getIssueCategory().getCode() : null,
                incident.getIssueCategory() != null ? incident.getIssueCategory().getName() : null,
                incident.getDescription(),
                incident.getSeverity(),
                incident.getStatus(),
                incident.isEntrapmentReported(),
                incident.getReportedAt(),
                incident.getUpdatedAt(),
                incident.getResolvedAt(),
                incident.getAcknowledgedAt(),
                incident.getResponseDeadline(),
                incident.getResolutionDeadline(),
                incident.getEscalationLevel(),
                incident.isResponseSlaBreached(),
                incident.isResolutionSlaBreached(),
                respState,
                resolState
        );
    }
}
