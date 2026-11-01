package com.liftcare.dto;

import java.time.Instant;
import java.util.List;

public record CloudSyncBatchResponse(
        String batchId,
        String buildingCode,
        int totalReceived,
        int processedCount,
        int duplicateCount,
        int conflictCount,
        int rejectedCount,
        String status, // SUCCESS, PARTIAL, FAILED
        List<String> processedEventIds,
        List<String> failedEventIds,
        Instant syncedAt
) {}
