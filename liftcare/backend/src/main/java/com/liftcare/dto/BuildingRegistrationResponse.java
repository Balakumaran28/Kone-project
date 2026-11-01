package com.liftcare.dto;

import java.time.Instant;

public record BuildingRegistrationResponse(
        Long id,
        String buildingCode,
        String buildingName,
        String apiKey,
        String status,
        Instant registeredAt,
        Instant lastSyncedAt
) {}
