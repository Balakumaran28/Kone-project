package com.liftcare.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BuildingCreateRequest(
        @NotBlank(message = "Building code is required")
        @Size(max = 50, message = "Building code cannot exceed 50 characters")
        String buildingCode,

        @NotBlank(message = "Building name is required")
        @Size(max = 150, message = "Building name cannot exceed 150 characters")
        String name,

        @NotBlank(message = "Address is required")
        @Size(max = 255, message = "Address cannot exceed 255 characters")
        String address,

        @Size(max = 100, message = "City cannot exceed 100 characters")
        String city,

        @Size(max = 100, message = "Local node ID cannot exceed 100 characters")
        String localNodeId
) {}
