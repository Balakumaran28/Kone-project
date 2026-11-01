package com.liftcare.dto;

import java.time.Instant;
import java.util.Map;

public record HealthResponse(
        String status,
        String service,
        String version,
        String currentModule,
        String environment,
        String nodeId,
        Instant timestamp,
        DatabaseStatus database,
        Map<String, String> capabilities
) {
}
