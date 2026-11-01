package com.liftcare.dto;

import java.time.Instant;
import java.util.List;

public record CentralDashboardSummaryResponse(
        long totalRegisteredBuildings,
        long activeBuildingsCount,
        long buildingsWithSyncErrorsCount,
        long totalIncidents,
        long activeIncidents,
        long criticalIncidents,
        long entrapmentsReported,
        long slaBreachedIncidents,
        long pendingConflictsCount,
        List<BuildingSyncTelemetryDto> buildings,
        boolean isStaleDataWarning,
        Instant generatedAt
) {}
