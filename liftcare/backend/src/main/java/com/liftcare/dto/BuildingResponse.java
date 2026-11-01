package com.liftcare.dto;

import com.liftcare.model.Building;

import java.time.Instant;

public record BuildingResponse(
        Long id,
        String buildingCode,
        String name,
        String address,
        String city,
        String localNodeId,
        boolean active,
        long elevatorCount,
        Instant createdAt,
        Instant updatedAt
) {
    public static BuildingResponse from(Building building, long elevatorCount) {
        return new BuildingResponse(
                building.getId(),
                building.getBuildingCode(),
                building.getName(),
                building.getAddress(),
                building.getCity(),
                building.getLocalNodeId(),
                building.isActive(),
                elevatorCount,
                building.getCreatedAt(),
                building.getUpdatedAt()
        );
    }

    public static BuildingResponse from(Building building) {
        return from(building, 0);
    }
}
