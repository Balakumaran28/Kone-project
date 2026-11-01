package com.liftcare.dto;

import com.liftcare.model.ElevatorOperationalStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ElevatorUpdateRequest(
        @NotBlank(message = "Elevator code is required")
        @Size(max = 50, message = "Elevator code cannot exceed 50 characters")
        String elevatorCode,

        @Size(max = 255, message = "Location description cannot exceed 255 characters")
        String locationDescription,

        @Size(max = 100, message = "Manufacturer cannot exceed 100 characters")
        String manufacturer,

        @Size(max = 100, message = "Model cannot exceed 100 characters")
        String model,

        @Size(max = 150, message = "Service provider cannot exceed 150 characters")
        String serviceProvider,

        @Positive(message = "Total floors must be a positive integer")
        Integer totalFloors,

        ElevatorOperationalStatus operationalStatus
) {}
