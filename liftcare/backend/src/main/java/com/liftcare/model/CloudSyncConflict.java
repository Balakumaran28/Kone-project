package com.liftcare.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "cloud_sync_conflicts")
public class CloudSyncConflict {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conflict_id", nullable = false, unique = true, length = 64)
    private String conflictId;

    @Column(name = "ticket_number", nullable = false, length = 50)
    private String ticketNumber;

    @Column(name = "building_code", nullable = false, length = 50)
    private String buildingCode;

    @Column(name = "conflict_type", nullable = false, length = 50)
    private String conflictType;

    @Column(name = "local_state", nullable = false, columnDefinition = "TEXT")
    private String localState;

    @Column(name = "cloud_state", nullable = false, columnDefinition = "TEXT")
    private String cloudState;

    @Column(name = "resolution_action", nullable = false, length = 50)
    private String resolutionAction = "PENDING_REVIEW"; // PENDING_REVIEW, ACCEPTED_LOCAL, ACCEPTED_CLOUD, RESOLVED_MANUALLY

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public CloudSyncConflict() {}

    public CloudSyncConflict(String conflictId, String ticketNumber, String buildingCode,
                             String conflictType, String localState, String cloudState) {
        this.conflictId = conflictId;
        this.ticketNumber = ticketNumber;
        this.buildingCode = buildingCode;
        this.conflictType = conflictType;
        this.localState = localState;
        this.cloudState = cloudState;
        this.resolutionAction = "PENDING_REVIEW";
    }

    public Long getId() { return id; }
    public String getConflictId() { return conflictId; }
    public void setConflictId(String conflictId) { this.conflictId = conflictId; }
    public String getTicketNumber() { return ticketNumber; }
    public void setTicketNumber(String ticketNumber) { this.ticketNumber = ticketNumber; }
    public String getBuildingCode() { return buildingCode; }
    public void setBuildingCode(String buildingCode) { this.buildingCode = buildingCode; }
    public String getConflictType() { return conflictType; }
    public void setConflictType(String conflictType) { this.conflictType = conflictType; }
    public String getLocalState() { return localState; }
    public void setLocalState(String localState) { this.localState = localState; }
    public String getCloudState() { return cloudState; }
    public void setCloudState(String cloudState) { this.cloudState = cloudState; }
    public String getResolutionAction() { return resolutionAction; }
    public void setResolutionAction(String resolutionAction) { this.resolutionAction = resolutionAction; }
    public String getResolutionNotes() { return resolutionNotes; }
    public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }
}
