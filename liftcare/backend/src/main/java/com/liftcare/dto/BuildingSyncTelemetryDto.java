package com.liftcare.dto;

import java.time.Instant;

public record BuildingSyncTelemetryDto(
        String buildingCode,
        String buildingName,
        String status,
        Instant lastSyncedAt,
        String lastSyncStatus,
        String lastSyncError,
        long openIncidentsCount,
        long criticalIncidentsCount
) {}
