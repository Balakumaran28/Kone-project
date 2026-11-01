package com.liftcare.dto;

import jakarta.validation.constraints.NotBlank;

public record ConflictResolutionRequest(
        @NotBlank(message = "Resolution action is required")
        String resolutionAction, // ACCEPTED_LOCAL, ACCEPTED_CLOUD, RESOLVED_MANUALLY

        String resolutionNotes
) {}
