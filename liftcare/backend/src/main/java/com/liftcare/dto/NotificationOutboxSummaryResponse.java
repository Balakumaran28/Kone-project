package com.liftcare.dto;

import java.util.Map;

public record NotificationOutboxSummaryResponse(
        long totalNotifications,
        long pendingCount,
        long processingCount,
        long sentCount,
        long retryWaitCount,
        long failedCount,
        Map<String, Long> countByDeliveryState
) {}
