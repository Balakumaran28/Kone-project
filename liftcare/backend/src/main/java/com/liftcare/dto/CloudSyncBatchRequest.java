package com.liftcare.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.Instant;
import java.util.List;

public record CloudSyncBatchRequest(
        @NotBlank(message = "Building code is required")
        String buildingCode,

        @NotBlank(message = "Batch ID is required")
        String batchId,

        @NotEmpty(message = "Events list cannot be empty")
        List<@Valid SyncEventItemDto> events,

        Instant sentAt
) {}
