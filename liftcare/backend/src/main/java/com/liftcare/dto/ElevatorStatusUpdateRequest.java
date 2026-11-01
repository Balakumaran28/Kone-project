package com.liftcare.dto;

import com.liftcare.model.ElevatorOperationalStatus;
import jakarta.validation.constraints.NotNull;

public record ElevatorStatusUpdateRequest(
        @NotNull(message = "Operational status is required")
        ElevatorOperationalStatus operationalStatus
) {}
