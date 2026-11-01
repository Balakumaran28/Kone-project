package com.liftcare.dto;

import com.liftcare.model.IssueCategory;
import com.liftcare.model.SeverityLevel;

public record IssueCategoryResponse(
        Long id,
        String code,
        String name,
        String description,
        SeverityLevel defaultSeverity,
        boolean requiresImmediateAttention,
        boolean active
) {
    public static IssueCategoryResponse from(IssueCategory cat) {
        return new IssueCategoryResponse(
                cat.getId(),
                cat.getCode(),
                cat.getName(),
                cat.getDescription(),
                cat.getDefaultSeverity(),
                cat.isRequiresImmediateAttention(),
                cat.isActive()
        );
    }
}
