package com.liftcare.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BuildingRegistrationRequest(
        @NotBlank(message = "Building code is required")
        @Size(max = 50, message = "Building code cannot exceed 50 characters")
        String buildingCode,

        @NotBlank(message = "Building name is required")
        @Size(max = 100, message = "Building name cannot exceed 100 characters")
        String buildingName,

        @Size(min = 16, max = 128, message = "API key must be between 16 and 128 characters if provided")
        String apiKey
) {}
