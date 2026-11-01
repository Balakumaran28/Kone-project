package com.liftcare.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "cloud_incident_replicas")
public class CloudIncidentReplica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_number", nullable = false, unique = true, length = 50)
    private String ticketNumber;

    @Column(name = "building_code", nullable = false, length = 50)
    private String buildingCode;

    @Column(name = "building_name", length = 100)
    private String buildingName;

    @Column(name = "elevator_code", nullable = false, length = 50)
    private String elevatorCode;

    @Column(name = "category_name", nullable = false, length = 50)
    private String categoryName;

    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "entrapment_reported", nullable = false)
    private boolean entrapmentReported = false;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "reported_at", nullable = false)
    private Instant reportedAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "assigned_technician_name", length = 100)
    private String assignedTechnicianName;

    @Column(name = "escalation_level", nullable = false)
    private int escalationLevel = 0;

    @Column(name = "response_sla_breached", nullable = false)
    private boolean responseSlaBreached = false;

    @Column(name = "resolution_sla_breached", nullable = false)
    private boolean resolutionSlaBreached = false;

    @Column(name = "local_version", nullable = false)
    private int localVersion = 1;

    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt;

    @PrePersist
    @PreUpdate
    protected void onPersist() {
        this.lastSyncedAt = Instant.now();
    }

    public CloudIncidentReplica() {}

    public Long getId() { return id; }
    public String getTicketNumber() { return ticketNumber; }
    public void setTicketNumber(String ticketNumber) { this.ticketNumber = ticketNumber; }
    public String getBuildingCode() { return buildingCode; }
    public void setBuildingCode(String buildingCode) { this.buildingCode = buildingCode; }
    public String getBuildingName() { return buildingName; }
    public void setBuildingName(String buildingName) { this.buildingName = buildingName; }
    public String getElevatorCode() { return elevatorCode; }
    public void setElevatorCode(String elevatorCode) { this.elevatorCode = elevatorCode; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isEntrapmentReported() { return entrapmentReported; }
    public void setEntrapmentReported(boolean entrapmentReported) { this.entrapmentReported = entrapmentReported; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Instant getReportedAt() { return reportedAt; }
    public void setReportedAt(Instant reportedAt) { this.reportedAt = reportedAt; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(Instant acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
    public String getAssignedTechnicianName() { return assignedTechnicianName; }
    public void setAssignedTechnicianName(String assignedTechnicianName) { this.assignedTechnicianName = assignedTechnicianName; }
    public int getEscalationLevel() { return escalationLevel; }
    public void setEscalationLevel(int escalationLevel) { this.escalationLevel = escalationLevel; }
    public boolean isResponseSlaBreached() { return responseSlaBreached; }
    public void setResponseSlaBreached(boolean responseSlaBreached) { this.responseSlaBreached = responseSlaBreached; }
    public boolean isResolutionSlaBreached() { return resolutionSlaBreached; }
    public void setResolutionSlaBreached(boolean resolutionSlaBreached) { this.resolutionSlaBreached = resolutionSlaBreached; }
    public int getLocalVersion() { return localVersion; }
    public void setLocalVersion(int localVersion) { this.localVersion = localVersion; }
    public Instant getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(Instant lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }
}
