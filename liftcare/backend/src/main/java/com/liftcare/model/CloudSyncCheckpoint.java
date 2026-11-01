package com.liftcare.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "cloud_sync_checkpoints")
public class CloudSyncCheckpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "building_code", nullable = false, unique = true, length = 50)
    private String buildingCode;

    @Column(name = "last_successful_sync_at")
    private Instant lastSuccessfulSyncAt;

    @Column(name = "last_sync_attempt_at")
    private Instant lastSyncAttemptAt;

    @Column(name = "pending_events_count", nullable = false)
    private int pendingEventsCount = 0;

    @Column(name = "failed_events_count", nullable = false)
    private int failedEventsCount = 0;

    @Column(name = "cloud_connection_status", nullable = false, length = 30)
    private String cloudConnectionStatus = "UNKNOWN"; // ONLINE, OFFLINE, SYNCING, ERROR, UNKNOWN

    @Column(name = "last_sync_error", columnDefinition = "TEXT")
    private String lastSyncError;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    protected void onPersist() {
        this.updatedAt = Instant.now();
    }

    public CloudSyncCheckpoint() {}

    public CloudSyncCheckpoint(String buildingCode) {
        this.buildingCode = buildingCode;
        this.cloudConnectionStatus = "UNKNOWN";
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getBuildingCode() { return buildingCode; }
    public void setBuildingCode(String buildingCode) { this.buildingCode = buildingCode; }
    public Instant getLastSuccessfulSyncAt() { return lastSuccessfulSyncAt; }
    public void setLastSuccessfulSyncAt(Instant lastSuccessfulSyncAt) { this.lastSuccessfulSyncAt = lastSuccessfulSyncAt; }
    public Instant getLastSyncAttemptAt() { return lastSyncAttemptAt; }
    public void setLastSyncAttemptAt(Instant lastSyncAttemptAt) { this.lastSyncAttemptAt = lastSyncAttemptAt; }
    public int getPendingEventsCount() { return pendingEventsCount; }
    public void setPendingEventsCount(int pendingEventsCount) { this.pendingEventsCount = pendingEventsCount; }
    public int getFailedEventsCount() { return failedEventsCount; }
    public void setFailedEventsCount(int failedEventsCount) { this.failedEventsCount = failedEventsCount; }
    public String getCloudConnectionStatus() { return cloudConnectionStatus; }
    public void setCloudConnectionStatus(String cloudConnectionStatus) { this.cloudConnectionStatus = cloudConnectionStatus; }
    public String getLastSyncError() { return lastSyncError; }
    public void setLastSyncError(String lastSyncError) { this.lastSyncError = lastSyncError; }
    public Instant getUpdatedAt() { return updatedAt; }
}
