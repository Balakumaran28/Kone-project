package com.liftcare.dto;

import com.liftcare.model.SeverityLevel;
import com.liftcare.model.SlaPolicy;
import com.liftcare.model.UserRole;

import java.time.Instant;

public record SlaPolicyResponse(
        Long id,
        String name,
        Long buildingId,
        String buildingCode,
        String buildingName,
        SeverityLevel severity,
        Long issueCategoryId,
        String issueCategoryCode,
        String issueCategoryName,
        boolean active,
        int responseTargetMinutes,
        int resolutionTargetMinutes,
        int warningThresholdMinutes,
        UserRole tier1Role,
        UserRole tier2Role,
        UserRole tier3Role,
        int version,
        Instant createdAt,
        Instant updatedAt
) {
    public static SlaPolicyResponse from(SlaPolicy p) {
        return new SlaPolicyResponse(
                p.getId(),
                p.getName(),
                p.getBuilding() != null ? p.getBuilding().getId() : null,
                p.getBuilding() != null ? p.getBuilding().getBuildingCode() : null,
                p.getBuilding() != null ? p.getBuilding().getName() : null,
                p.getSeverity(),
                p.getIssueCategory() != null ? p.getIssueCategory().getId() : null,
                p.getIssueCategory() != null ? p.getIssueCategory().getCode() : null,
                p.getIssueCategory() != null ? p.getIssueCategory().getName() : null,
                p.isActive(),
                p.getResponseTargetMinutes(),
                p.getResolutionTargetMinutes(),
                p.getWarningThresholdMinutes(),
                p.getTier1Role(),
                p.getTier2Role(),
                p.getTier3Role(),
                p.getVersion(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
