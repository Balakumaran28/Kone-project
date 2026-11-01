package com.liftcare.model;

/**
 * Lifecycle state of an elevator incident report.
 */
public enum IncidentStatus {
    REPORTED,
    ACKNOWLEDGED,
    ASSIGNED,
    IN_PROGRESS,
    RESOLVED,
    CLOSED
}
