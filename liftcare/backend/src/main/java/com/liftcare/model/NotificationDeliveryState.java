package com.liftcare.model;

/**
 * State machine for durable server-side notification outbox records.
 */
public enum NotificationDeliveryState {
    PENDING,
    PROCESSING,
    SENT,
    RETRY_WAIT,
    FAILED
}
