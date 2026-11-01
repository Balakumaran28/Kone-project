package com.liftcare.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IncidentNoteRequest(
        @NotBlank(message = "Note content is required")
        @Size(max = 1000, message = "Note cannot exceed 1000 characters")
        String notes,

        @Size(max = 50, message = "Note type cannot exceed 50 characters")
        String noteType
) {}
