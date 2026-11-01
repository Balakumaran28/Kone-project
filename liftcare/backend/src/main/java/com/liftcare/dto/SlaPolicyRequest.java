package com.liftcare.dto;

import com.liftcare.model.SeverityLevel;
import com.liftcare.model.UserRole;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SlaPolicyRequest(
        @NotBlank(message = "Policy name is required")
        @Size(max = 120, message = "Policy name cannot exceed 120 characters")
        String name,

        Long buildingId,

        @NotNull(message = "Severity level is required")
        SeverityLevel severity,

        Long issueCategoryId,

        Boolean active,

        @NotNull(message = "Response target is required")
        @Min(value = 1, message = "Response target duration must be at least 1 minute")
        Integer responseTargetMinutes,

        @NotNull(message = "Resolution target is required")
        @Min(value = 1, message = "Resolution target duration must be at least 1 minute")
        Integer resolutionTargetMinutes,

        @NotNull(message = "Warning threshold is required")
        @Min(value = 0, message = "Warning threshold cannot be negative")
        Integer warningThresholdMinutes,

        UserRole tier1Role,
        UserRole tier2Role,
        UserRole tier3Role
) {}
