package com.liftcare.dto;

import java.time.Instant;

public record DatabaseStatus(
        String status,
        String databaseProductName,
        String databaseProductVersion,
        String url,
        String message,
        Instant checkedAt
) {
    public static DatabaseStatus connected(String product, String version, String url) {
        return new DatabaseStatus("CONNECTED", product, version, url, "Database connection verified and active.", Instant.now());
    }

    public static DatabaseStatus disconnected(String url, String message) {
        return new DatabaseStatus("DISCONNECTED", null, null, url, message, Instant.now());
    }
}
