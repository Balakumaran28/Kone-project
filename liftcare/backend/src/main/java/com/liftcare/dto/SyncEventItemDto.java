package com.liftcare.dto;

import jakarta.validation.constraints.NotBlank;

public record SyncEventItemDto(
        @NotBlank(message = "Event ID is required")
        String eventId,

        @NotBlank(message = "Event type is required")
        String eventType,

        @NotBlank(message = "Aggregate type is required")
        String aggregateType,

        @NotBlank(message = "Aggregate ID is required")
        String aggregateId,

        @NotBlank(message = "Payload is required")
        String payload,

        @NotBlank(message = "Idempotency key is required")
        String idempotencyKey
) {}
