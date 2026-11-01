package com.liftcare.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Objects;

/**
 * Standard classification taxonomy for elevator defects, mechanical errors, and safety incidents.
 */
@Entity
@Table(name = "issue_categories", indexes = {
        @Index(name = "idx_issue_cat_code", columnList = "code", unique = true),
        @Index(name = "idx_issue_cat_severity", columnList = "default_severity")
})
public class IssueCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Category code is required")
    @Size(max = 50, message = "Category code cannot exceed 50 characters")
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @NotBlank(message = "Category name is required")
    @Size(max = 150, message = "Category name cannot exceed 150 characters")
    @Column(nullable = false, length = 150)
    private String name;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    @Column(length = 500)
    private String description;

    @NotNull(message = "Default severity is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "default_severity", nullable = false, length = 20)
    private SeverityLevel defaultSeverity = SeverityLevel.MEDIUM;

    @Column(name = "requires_immediate_attention", nullable = false)
    private boolean requiresImmediateAttention = false;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public IssueCategory() {
    }

    public IssueCategory(String code, String name, String description, SeverityLevel defaultSeverity, boolean requiresImmediateAttention) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.defaultSeverity = defaultSeverity;
        this.requiresImmediateAttention = requiresImmediateAttention;
        this.active = true;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        if (this.defaultSeverity == null) {
            this.defaultSeverity = SeverityLevel.MEDIUM;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public SeverityLevel getDefaultSeverity() {
        return defaultSeverity;
    }

    public void setDefaultSeverity(SeverityLevel defaultSeverity) {
        this.defaultSeverity = defaultSeverity;
    }

    public boolean isRequiresImmediateAttention() {
        return requiresImmediateAttention;
    }

    public void setRequiresImmediateAttention(boolean requiresImmediateAttention) {
        this.requiresImmediateAttention = requiresImmediateAttention;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IssueCategory that)) return false;
        return Objects.equals(id, that.id) || (code != null && Objects.equals(code, that.code));
    }

    @Override
    public int hashCode() {
        return code != null ? code.hashCode() : Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "IssueCategory{" +
                "id=" + id +
                ", code='" + code + '\'' +
                ", name='" + name + '\'' +
                ", defaultSeverity=" + defaultSeverity +
                ", requiresImmediateAttention=" + requiresImmediateAttention +
                ", active=" + active +
                '}';
    }
}
