package com.liftcare.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents an elevator fault, defect, or emergency incident recorded on the local node.
 */
@Entity
@Table(name = "incidents", indexes = {
        @Index(name = "idx_incidents_ticket", columnList = "ticket_number", unique = true),
        @Index(name = "idx_incidents_bld_status", columnList = "building_id, status"),
        @Index(name = "idx_incidents_elevator", columnList = "elevator_id"),
        @Index(name = "idx_incidents_severity", columnList = "severity"),
        @Index(name = "idx_incidents_reported_at", columnList = "reported_at")
})
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Ticket number is required")
    @Size(max = 60, message = "Ticket number cannot exceed 60 characters")
    @Column(name = "ticket_number", nullable = false, unique = true, length = 60)
    private String ticketNumber;

    @NotNull(message = "Building relationship is required")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    @NotNull(message = "Elevator relationship is required")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "elevator_id", nullable = false)
    private Elevator elevator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_by_id")
    private User reportedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_id")
    private User assignedTo;

    @NotNull(message = "Issue category is required")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issue_category_id", nullable = false)
    private IssueCategory issueCategory;

    @NotBlank(message = "Incident description is required")
    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    @Column(nullable = false, length = 2000)
    private String description;

    @NotNull(message = "Severity level is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeverityLevel severity = SeverityLevel.MEDIUM;

    @NotNull(message = "Incident status is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IncidentStatus status = IncidentStatus.REPORTED;

    @Column(name = "entrapment_reported", nullable = false)
    private boolean entrapmentReported = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sla_policy_id")
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.SET_NULL)
    private SlaPolicy slaPolicy;

    @Column(name = "response_deadline")
    private Instant responseDeadline;

    @Column(name = "resolution_deadline")
    private Instant resolutionDeadline;

    @Column(name = "sla_response_target_minutes")
    private Integer slaResponseTargetMinutes;

    @Column(name = "sla_resolution_target_minutes")
    private Integer slaResolutionTargetMinutes;

    @Column(name = "sla_warning_threshold_minutes")
    private Integer slaWarningThresholdMinutes;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "escalation_level", nullable = false)
    private int escalationLevel = 0;

    @Column(name = "last_escalated_at")
    private Instant lastEscalatedAt;

    @Column(name = "response_sla_breached", nullable = false)
    private boolean responseSlaBreached = false;

    @Column(name = "resolution_sla_breached", nullable = false)
    private boolean resolutionSlaBreached = false;

    @Column(name = "sla_warning_sent", nullable = false)
    private boolean slaWarningSent = false;

    @Column(name = "reported_at", nullable = false, updatable = false)
    private Instant reportedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public Incident() {
    }

    public Incident(String ticketNumber, Building building, Elevator elevator, User reportedBy,
                    IssueCategory issueCategory, String description, SeverityLevel severity,
                    boolean entrapmentReported) {
        this.ticketNumber = ticketNumber;
        this.building = building;
        this.elevator = elevator;
        this.reportedBy = reportedBy;
        this.issueCategory = issueCategory;
        this.description = description;
        this.severity = severity != null ? severity : SeverityLevel.MEDIUM;
        this.status = IncidentStatus.REPORTED;
        this.entrapmentReported = entrapmentReported;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.reportedAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = IncidentStatus.REPORTED;
        }
        if (this.severity == null) {
            this.severity = SeverityLevel.MEDIUM;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTicketNumber() {
        return ticketNumber;
    }

    public void setTicketNumber(String ticketNumber) {
        this.ticketNumber = ticketNumber;
    }

    public Building getBuilding() {
        return building;
    }

    public void setBuilding(Building building) {
        this.building = building;
    }

    public Elevator getElevator() {
        return elevator;
    }

    public void setElevator(Elevator elevator) {
        this.elevator = elevator;
    }

    public User getReportedBy() {
        return reportedBy;
    }

    public void setReportedBy(User reportedBy) {
        this.reportedBy = reportedBy;
    }

    public User getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(User assignedTo) {
        this.assignedTo = assignedTo;
    }

    public IssueCategory getIssueCategory() {
        return issueCategory;
    }

    public void setIssueCategory(IssueCategory issueCategory) {
        this.issueCategory = issueCategory;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public SeverityLevel getSeverity() {
        return severity;
    }

    public void setSeverity(SeverityLevel severity) {
        this.severity = severity;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public void setStatus(IncidentStatus status) {
        this.status = status;
        if (status == IncidentStatus.RESOLVED && this.resolvedAt == null) {
            this.resolvedAt = Instant.now();
        }
    }

    public boolean isEntrapmentReported() {
        return entrapmentReported;
    }

    public void setEntrapmentReported(boolean entrapmentReported) {
        this.entrapmentReported = entrapmentReported;
    }

    public Instant getReportedAt() {
        return reportedAt;
    }

    public void setReportedAt(Instant reportedAt) {
        this.reportedAt = reportedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public SlaPolicy getSlaPolicy() {
        return slaPolicy;
    }

    public void setSlaPolicy(SlaPolicy slaPolicy) {
        this.slaPolicy = slaPolicy;
    }

    public Instant getResponseDeadline() {
        return responseDeadline;
    }

    public void setResponseDeadline(Instant responseDeadline) {
        this.responseDeadline = responseDeadline;
    }

    public Instant getResolutionDeadline() {
        return resolutionDeadline;
    }

    public void setResolutionDeadline(Instant resolutionDeadline) {
        this.resolutionDeadline = resolutionDeadline;
    }

    public Integer getSlaResponseTargetMinutes() {
        return slaResponseTargetMinutes;
    }

    public void setSlaResponseTargetMinutes(Integer slaResponseTargetMinutes) {
        this.slaResponseTargetMinutes = slaResponseTargetMinutes;
    }

    public Integer getSlaResolutionTargetMinutes() {
        return slaResolutionTargetMinutes;
    }

    public void setSlaResolutionTargetMinutes(Integer slaResolutionTargetMinutes) {
        this.slaResolutionTargetMinutes = slaResolutionTargetMinutes;
    }

    public Integer getSlaWarningThresholdMinutes() {
        return slaWarningThresholdMinutes;
    }

    public void setSlaWarningThresholdMinutes(Integer slaWarningThresholdMinutes) {
        this.slaWarningThresholdMinutes = slaWarningThresholdMinutes;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public void setAcknowledgedAt(Instant acknowledgedAt) {
        this.acknowledgedAt = acknowledgedAt;
    }

    public int getEscalationLevel() {
        return escalationLevel;
    }

    public void setEscalationLevel(int escalationLevel) {
        this.escalationLevel = escalationLevel;
    }

    public Instant getLastEscalatedAt() {
        return lastEscalatedAt;
    }

    public void setLastEscalatedAt(Instant lastEscalatedAt) {
        this.lastEscalatedAt = lastEscalatedAt;
    }

    public boolean isResponseSlaBreached() {
        return responseSlaBreached;
    }

    public void setResponseSlaBreached(boolean responseSlaBreached) {
        this.responseSlaBreached = responseSlaBreached;
    }

    public boolean isResolutionSlaBreached() {
        return resolutionSlaBreached;
    }

    public void setResolutionSlaBreached(boolean resolutionSlaBreached) {
        this.resolutionSlaBreached = resolutionSlaBreached;
    }

    public boolean isSlaWarningSent() {
        return slaWarningSent;
    }

    public void setSlaWarningSent(boolean slaWarningSent) {
        this.slaWarningSent = slaWarningSent;
    }

    /**
     * Snapshots the given policy's targets and calculates fixed deadlines based on baseTime.
     */
    public void applySlaPolicy(SlaPolicy policy, Instant baseTime) {
        this.slaPolicy = policy;
        if (policy != null && baseTime != null) {
            this.slaResponseTargetMinutes = policy.getResponseTargetMinutes();
            this.slaResolutionTargetMinutes = policy.getResolutionTargetMinutes();
            this.slaWarningThresholdMinutes = policy.getWarningThresholdMinutes();
            this.responseDeadline = baseTime.plus(java.time.Duration.ofMinutes(policy.getResponseTargetMinutes()));
            this.resolutionDeadline = baseTime.plus(java.time.Duration.ofMinutes(policy.getResolutionTargetMinutes()));
        }
    }

    /**
     * Evaluates current Response SLA compliance state based on server reference time.
     */
    public SlaState calculateResponseSlaState(Instant now) {
        if (responseDeadline == null) {
            return SlaState.POLICY_MISSING;
        }
        if (acknowledgedAt != null) {
            return acknowledgedAt.isAfter(responseDeadline) ? SlaState.BREACHED : SlaState.MET;
        }
        if (responseSlaBreached || now.isAfter(responseDeadline)) {
            return SlaState.BREACHED;
        }
        if (slaWarningThresholdMinutes != null && slaWarningThresholdMinutes > 0) {
            Instant warningTime = responseDeadline.minus(java.time.Duration.ofMinutes(slaWarningThresholdMinutes));
            if (!now.isBefore(warningTime)) {
                return SlaState.APPROACHING_DEADLINE;
            }
        }
        return SlaState.ON_TRACK;
    }

    /**
     * Evaluates current Resolution SLA compliance state based on server reference time.
     */
    public SlaState calculateResolutionSlaState(Instant now) {
        if (resolutionDeadline == null) {
            return SlaState.POLICY_MISSING;
        }
        if (status == IncidentStatus.RESOLVED || status == IncidentStatus.CLOSED) {
            Instant effectiveResolved = resolvedAt != null ? resolvedAt : updatedAt;
            return (effectiveResolved != null && effectiveResolved.isAfter(resolutionDeadline))
                    ? SlaState.BREACHED
                    : SlaState.MET;
        }
        if (resolutionSlaBreached || now.isAfter(resolutionDeadline)) {
            return SlaState.BREACHED;
        }
        if (slaWarningThresholdMinutes != null && slaWarningThresholdMinutes > 0) {
            Instant warningTime = resolutionDeadline.minus(java.time.Duration.ofMinutes(slaWarningThresholdMinutes));
            if (!now.isBefore(warningTime)) {
                return SlaState.APPROACHING_DEADLINE;
            }
        }
        return SlaState.ON_TRACK;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Incident incident)) return false;
        return Objects.equals(id, incident.id) || (ticketNumber != null && Objects.equals(ticketNumber, incident.ticketNumber));
    }

    @Override
    public int hashCode() {
        return ticketNumber != null ? ticketNumber.hashCode() : Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Incident{" +
                "id=" + id +
                ", ticketNumber='" + ticketNumber + '\'' +
                ", buildingId=" + (building != null ? building.getId() : null) +
                ", elevatorId=" + (elevator != null ? elevator.getId() : null) +
                ", severity=" + severity +
                ", status=" + status +
                ", entrapmentReported=" + entrapmentReported +
                ", reportedAt=" + reportedAt +
                '}';
    }
}
