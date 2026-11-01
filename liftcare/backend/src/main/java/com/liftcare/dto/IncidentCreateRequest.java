package com.liftcare.dto;

import com.liftcare.model.SeverityLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record IncidentCreateRequest(
        @NotNull(message = "Building ID is required")
        Long buildingId,

        @NotNull(message = "Elevator ID is required")
        Long elevatorId,

        @NotNull(message = "Issue category ID is required")
        Long issueCategoryId,

        @NotBlank(message = "Incident description is required")
        @Size(max = 2000, message = "Description cannot exceed 2000 characters")
        String description,

        SeverityLevel severity,

        Boolean entrapmentReported
) {}
