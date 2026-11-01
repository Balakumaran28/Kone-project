package com.liftcare.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable audit trail recording state transitions, technician assignments,
 * remarks, elevator status changes, and escalation steps for an elevator incident.
 */
@Entity
@Table(name = "incident_history", indexes = {
        @Index(name = "idx_history_incident_date", columnList = "incident_id, changed_at"),
        @Index(name = "idx_history_incident_action", columnList = "incident_id, action"),
        @Index(name = "idx_history_incident_actor", columnList = "incident_id, changed_by_id")
})
public class IncidentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Incident relationship is required")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @NotBlank(message = "Action description is required")
    @Size(max = 80, message = "Action cannot exceed 80 characters")
    @Column(nullable = false, length = 80)
    private String action;

    @Size(max = 1000, message = "Previous value cannot exceed 1000 characters")
    @Column(name = "previous_value", length = 1000)
    private String previousValue;

    @Size(max = 1000, message = "New value cannot exceed 1000 characters")
    @Column(name = "new_value", length = 1000)
    private String newValue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by_id")
    private User changedBy;

    @NotNull(message = "Actor type is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 20)
    private ActorType actorType = ActorType.USER;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    @Size(max = 1000, message = "Remarks cannot exceed 1000 characters")
    @Column(length = 1000)
    private String remarks;

    public IncidentHistory() {
    }

    public IncidentHistory(Incident incident, String action, String previousValue, String newValue, User changedBy, String remarks) {
        this(incident, action, previousValue, newValue, changedBy, changedBy != null ? ActorType.USER : ActorType.SYSTEM, remarks);
    }

    public IncidentHistory(Incident incident, String action, String previousValue, String newValue, User changedBy, ActorType actorType, String remarks) {
        this.incident = incident;
        this.action = action;
        this.previousValue = previousValue;
        this.newValue = newValue;
        this.changedBy = changedBy;
        this.actorType = actorType != null ? actorType : (changedBy != null ? ActorType.USER : ActorType.SYSTEM);
        this.remarks = remarks;
    }

    @PrePersist
    protected void onCreate() {
        if (this.changedAt == null) {
            this.changedAt = Instant.now();
        }
        if (this.actorType == null) {
            this.actorType = (this.changedBy != null) ? ActorType.USER : ActorType.SYSTEM;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Incident getIncident() {
        return incident;
    }

    public void setIncident(Incident incident) {
        this.incident = incident;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getPreviousValue() {
        return previousValue;
    }

    public void setPreviousValue(String previousValue) {
        this.previousValue = previousValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public void setNewValue(String newValue) {
        this.newValue = newValue;
    }

    public User getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(User changedBy) {
        this.changedBy = changedBy;
        if (changedBy != null && this.actorType == null) {
            this.actorType = ActorType.USER;
        }
    }

    public ActorType getActorType() {
        return actorType;
    }

    public void setActorType(ActorType actorType) {
        this.actorType = actorType;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(Instant changedAt) {
        this.changedAt = changedAt;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IncidentHistory that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "IncidentHistory{" +
                "id=" + id +
                ", incidentId=" + (incident != null ? incident.getId() : null) +
                ", action='" + action + '\'' +
                ", actorType=" + actorType +
                ", changedById=" + (changedBy != null ? changedBy.getId() : null) +
                ", changedAt=" + changedAt +
                '}';
    }
}
