package com.liftcare.dto;

import com.liftcare.model.ActorType;
import com.liftcare.model.IncidentHistory;

import java.time.Instant;

public record IncidentHistoryResponse(
        Long id,
        Long incidentId,
        String action,
        String previousValue,
        String newValue,
        Long changedById,
        String changedByName,
        String changedByEmail,
        String changedByRole,
        String actorType,
        Instant changedAt,
        String remarks
) {
    public static IncidentHistoryResponse from(IncidentHistory h) {
        String actorTypeStr = h.getActorType() != null ? h.getActorType().name() : (h.getChangedBy() != null ? "USER" : "SYSTEM");
        String actorName = h.getChangedBy() != null
                ? h.getChangedBy().getFullName()
                : ("SYSTEM".equals(actorTypeStr) ? "System Automation" : "Unknown");
        String actorEmail = h.getChangedBy() != null ? h.getChangedBy().getEmail() : null;
        String actorRole = h.getChangedBy() != null && h.getChangedBy().getRole() != null
                ? h.getChangedBy().getRole().name()
                : ("SYSTEM".equals(actorTypeStr) ? "SYSTEM" : null);

        return new IncidentHistoryResponse(
                h.getId(),
                h.getIncident() != null ? h.getIncident().getId() : null,
                h.getAction(),
                h.getPreviousValue(),
                h.getNewValue(),
                h.getChangedBy() != null ? h.getChangedBy().getId() : null,
                actorName,
                actorEmail,
                actorRole,
                actorTypeStr,
                h.getChangedAt(),
                h.getRemarks()
        );
    }
}
