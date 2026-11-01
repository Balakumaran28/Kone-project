package com.liftcare.dto;

import com.liftcare.model.Elevator;
import com.liftcare.model.ElevatorOperationalStatus;

import java.time.Instant;

public record ElevatorResponse(
        Long id,
        String elevatorCode,
        Long buildingId,
        String buildingCode,
        String buildingName,
        String locationDescription,
        String manufacturer,
        String model,
        String serviceProvider,
        Integer totalFloors,
        ElevatorOperationalStatus operationalStatus,
        Instant createdAt,
        Instant updatedAt
) {
    public static ElevatorResponse from(Elevator elevator) {
        return new ElevatorResponse(
                elevator.getId(),
                elevator.getElevatorCode(),
                elevator.getBuilding() != null ? elevator.getBuilding().getId() : null,
                elevator.getBuilding() != null ? elevator.getBuilding().getBuildingCode() : null,
                elevator.getBuilding() != null ? elevator.getBuilding().getName() : null,
                elevator.getLocationDescription(),
                elevator.getManufacturer(),
                elevator.getModel(),
                elevator.getServiceProvider(),
                elevator.getTotalFloors(),
                elevator.getOperationalStatus(),
                elevator.getCreatedAt(),
                elevator.getUpdatedAt()
        );
    }
}
