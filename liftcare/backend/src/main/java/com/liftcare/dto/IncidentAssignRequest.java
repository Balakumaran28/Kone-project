package com.liftcare.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record IncidentAssignRequest(
        @NotNull(message = "Technician user ID is required")
        Long technicianId,

        @Size(max = 1000, message = "Remarks cannot exceed 1000 characters")
        String remarks
) {}
