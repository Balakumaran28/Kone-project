package com.liftcare.dto;

import com.liftcare.model.ElevatorOperationalStatus;
import com.liftcare.model.IncidentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record IncidentStatusUpdateRequest(
        @NotNull(message = "Incident status is required")
        IncidentStatus status,

        @Size(max = 1000, message = "Remarks or resolution notes cannot exceed 1000 characters")
        String remarks,

        ElevatorOperationalStatus elevatorStatus
) {
    public IncidentStatusUpdateRequest(IncidentStatus status, String remarks) {
        this(status, remarks, null);
    }
}
