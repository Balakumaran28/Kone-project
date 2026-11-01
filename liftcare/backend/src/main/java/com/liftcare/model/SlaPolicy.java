package com.liftcare.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.Objects;

/**
 * Configurable Service Level Agreement (SLA) policy defining target response
 * and resolution durations, pre-deadline warning thresholds, and multi-tier
 * escalation recipient roles for elevator incidents on the local building node.
 */
@Entity
@Table(name = "sla_policies", indexes = {
        @Index(name = "idx_sla_policy_active", columnList = "active"),
        @Index(name = "idx_sla_policy_lookup", columnList = "building_id, severity, issue_category_id, active")
})
public class SlaPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Policy name is required")
    @Size(max = 120, message = "Policy name cannot exceed 120 characters")
    @Column(nullable = false, length = 120)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Building building; // Null indicates global default for all buildings

    @NotNull(message = "Severity level is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeverityLevel severity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issue_category_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private IssueCategory issueCategory; // Null indicates applies to all issue categories

    @Column(nullable = false)
    private boolean active = true;

    @Min(value = 1, message = "Response target duration must be at least 1 minute")
    @Column(name = "response_target_minutes", nullable = false)
    private int responseTargetMinutes;

    @Min(value = 1, message = "Resolution target duration must be at least 1 minute")
    @Column(name = "resolution_target_minutes", nullable = false)
    private int resolutionTargetMinutes;

    @Min(value = 0, message = "Warning threshold cannot be negative")
    @Column(name = "warning_threshold_minutes", nullable = false)
    private int warningThresholdMinutes;

    @NotNull(message = "Tier 1 escalation role is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "tier1_role", nullable = false, length = 50)
    private UserRole tier1Role = UserRole.TECHNICIAN;

    @NotNull(message = "Tier 2 escalation role is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "tier2_role", nullable = false, length = 50)
    private UserRole tier2Role = UserRole.BUILDING_MANAGER;

    @NotNull(message = "Tier 3 escalation role is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "tier3_role", nullable = false, length = 50)
    private UserRole tier3Role = UserRole.COMPANY_SUPERVISOR;

    @Column(nullable = false)
    private int version = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public SlaPolicy() {
    }

    public SlaPolicy(
            String name,
            Building building,
            SeverityLevel severity,
            IssueCategory issueCategory,
            int responseTargetMinutes,
            int resolutionTargetMinutes,
            int warningThresholdMinutes,
            UserRole tier1Role,
            UserRole tier2Role,
            UserRole tier3Role) {
        this.name = name;
        this.building = building;
        this.severity = severity;
        this.issueCategory = issueCategory;
        this.responseTargetMinutes = responseTargetMinutes;
        this.resolutionTargetMinutes = resolutionTargetMinutes;
        this.warningThresholdMinutes = warningThresholdMinutes;
        this.tier1Role = tier1Role != null ? tier1Role : UserRole.TECHNICIAN;
        this.tier2Role = tier2Role != null ? tier2Role : UserRole.BUILDING_MANAGER;
        this.tier3Role = tier3Role != null ? tier3Role : UserRole.COMPANY_SUPERVISOR;
        this.active = true;
        this.version = 1;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.version == 0) {
            this.version = 1;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Building getBuilding() {
        return building;
    }

    public void setBuilding(Building building) {
        this.building = building;
    }

    public SeverityLevel getSeverity() {
        return severity;
    }

    public void setSeverity(SeverityLevel severity) {
        this.severity = severity;
    }

    public IssueCategory getIssueCategory() {
        return issueCategory;
    }

    public void setIssueCategory(IssueCategory issueCategory) {
        this.issueCategory = issueCategory;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getResponseTargetMinutes() {
        return responseTargetMinutes;
    }

    public void setResponseTargetMinutes(int responseTargetMinutes) {
        this.responseTargetMinutes = responseTargetMinutes;
    }

    public int getResolutionTargetMinutes() {
        return resolutionTargetMinutes;
    }

    public void setResolutionTargetMinutes(int resolutionTargetMinutes) {
        this.resolutionTargetMinutes = resolutionTargetMinutes;
    }

    public int getWarningThresholdMinutes() {
        return warningThresholdMinutes;
    }

    public void setWarningThresholdMinutes(int warningThresholdMinutes) {
        this.warningThresholdMinutes = warningThresholdMinutes;
    }

    public UserRole getTier1Role() {
        return tier1Role;
    }

    public void setTier1Role(UserRole tier1Role) {
        this.tier1Role = tier1Role;
    }

    public UserRole getTier2Role() {
        return tier2Role;
    }

    public void setTier2Role(UserRole tier2Role) {
        this.tier2Role = tier2Role;
    }

    public UserRole getTier3Role() {
        return tier3Role;
    }

    public void setTier3Role(UserRole tier3Role) {
        this.tier3Role = tier3Role;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SlaPolicy slaPolicy)) return false;
        return Objects.equals(id, slaPolicy.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
