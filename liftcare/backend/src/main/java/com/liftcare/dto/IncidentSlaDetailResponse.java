package com.liftcare.dto;

import com.liftcare.model.Incident;
import com.liftcare.model.SeverityLevel;
import com.liftcare.model.SlaState;

import java.time.Duration;
import java.time.Instant;

public record IncidentSlaDetailResponse(
        Long incidentId,
        String ticketNumber,
        Long slaPolicyId,
        String slaPolicyName,
        SeverityLevel severity,
        Instant reportedAt,
        Instant acknowledgedAt,
        Instant resolvedAt,
        Instant responseDeadline,
        Instant resolutionDeadline,
        Integer responseTargetMinutes,
        Integer resolutionTargetMinutes,
        Integer warningThresholdMinutes,
        SlaState responseSlaState,
        SlaState resolutionSlaState,
        int escalationLevel,
        Instant lastEscalatedAt,
        boolean responseSlaBreached,
        boolean resolutionSlaBreached,
        boolean slaWarningSent,
        Long responseRemainingSeconds,
        Long resolutionRemainingSeconds
) {
    public static IncidentSlaDetailResponse from(Incident i, Instant now) {
        Instant refTime = now != null ? now : Instant.now();
        SlaState respState = i.calculateResponseSlaState(refTime);
        SlaState resolState = i.calculateResolutionSlaState(refTime);

        Long respRemaining = null;
        if (i.getResponseDeadline() != null) {
            respRemaining = Duration.between(refTime, i.getResponseDeadline()).toSeconds();
        }

        Long resolRemaining = null;
        if (i.getResolutionDeadline() != null) {
            resolRemaining = Duration.between(refTime, i.getResolutionDeadline()).toSeconds();
        }

        return new IncidentSlaDetailResponse(
                i.getId(),
                i.getTicketNumber(),
                i.getSlaPolicy() != null ? i.getSlaPolicy().getId() : null,
                i.getSlaPolicy() != null ? i.getSlaPolicy().getName() : (i.getResponseDeadline() != null ? "Custom/Snapshot Policy" : "None (Policy Missing)"),
                i.getSeverity(),
                i.getReportedAt(),
                i.getAcknowledgedAt(),
                i.getResolvedAt(),
                i.getResponseDeadline(),
                i.getResolutionDeadline(),
                i.getSlaResponseTargetMinutes(),
                i.getSlaResolutionTargetMinutes(),
                i.getSlaWarningThresholdMinutes(),
                respState,
                resolState,
                i.getEscalationLevel(),
                i.getLastEscalatedAt(),
                i.isResponseSlaBreached(),
                i.isResolutionSlaBreached(),
                i.isSlaWarningSent(),
                respRemaining,
                resolRemaining
        );
    }
}
