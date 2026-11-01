package com.liftcare.dto;

import java.time.Instant;
import java.util.List;

/**
 * High-performance aggregated telemetry and operational overview for the Control-Room Dashboard.
 * Includes real-time SLA compliance, breach counters, and escalation tier breakdowns.
 */
public record DashboardSummaryResponse(
        long totalActiveIncidents,
        long criticalIncidents,
        long highSeverityIncidents,
        long awaitingAcknowledgementIncidents,
        long assignedIncidents,
        long inProgressIncidents,
        long activeEntrapments,
        long elevatorsOutOfService,
        long elevatorsUnderMaintenance,
        long elevatorsActive,
        long totalElevators,
        long totalBuildings,
        Instant timestamp,
        String localNodeId,
        List<IncidentResponse> emergencyIncidents,
        long approachingDeadlinesCount,
        long responseSlaBreachedCount,
        long resolutionSlaBreachedCount,
        long escalatedTier1Count,
        long escalatedTier2Count,
        long escalatedTier3Count,
        long missingSlaPolicyCount
) {
    public DashboardSummaryResponse(
            long totalActiveIncidents,
            long criticalIncidents,
            long highSeverityIncidents,
            long awaitingAcknowledgementIncidents,
            long assignedIncidents,
            long inProgressIncidents,
            long activeEntrapments,
            long elevatorsOutOfService,
            long elevatorsUnderMaintenance,
            long elevatorsActive,
            long totalElevators,
            long totalBuildings,
            Instant timestamp,
            String localNodeId,
            List<IncidentResponse> emergencyIncidents) {
        this(
                totalActiveIncidents, criticalIncidents, highSeverityIncidents,
                awaitingAcknowledgementIncidents, assignedIncidents, inProgressIncidents,
                activeEntrapments, elevatorsOutOfService, elevatorsUnderMaintenance,
                elevatorsActive, totalElevators, totalBuildings, timestamp, localNodeId,
                emergencyIncidents, 0, 0, 0, 0, 0, 0, 0
        );
    }
}
