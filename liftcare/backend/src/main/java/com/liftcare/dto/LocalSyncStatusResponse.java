package com.liftcare.dto;

import java.time.Instant;

public record LocalSyncStatusResponse(
        String buildingCode,
        String cloudConnectionStatus, // ONLINE, OFFLINE, SYNCING, ERROR, UNKNOWN
        Instant lastSuccessfulSyncAt,
        Instant lastSyncAttemptAt,
        int pendingEventsCount,
        int failedEventsCount,
        String lastSyncError,
        boolean localOperationsAvailable
) {}
