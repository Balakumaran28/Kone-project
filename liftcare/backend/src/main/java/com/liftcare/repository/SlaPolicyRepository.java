package com.liftcare.repository;

import com.liftcare.model.SeverityLevel;
import com.liftcare.model.SlaPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, Long> {

    List<SlaPolicy> findByActiveTrue();

    List<SlaPolicy> findByBuildingIdAndActiveTrue(Long buildingId);

    List<SlaPolicy> findBySeverity(SeverityLevel severity);

    // Precedence Level 1: Building + Category + Severity (returns List to handle any historical duplicates)
    List<SlaPolicy> findBySeverityAndBuildingIdAndIssueCategoryIdAndActiveTrue(
            SeverityLevel severity, Long buildingId, Long issueCategoryId
    );

    // Precedence Level 2: Building + Severity (Category is NULL)
    List<SlaPolicy> findBySeverityAndBuildingIdAndIssueCategoryIsNullAndActiveTrue(
            SeverityLevel severity, Long buildingId
    );

    // Precedence Level 3: Global (Building is NULL) + Category + Severity
    List<SlaPolicy> findBySeverityAndBuildingIsNullAndIssueCategoryIdAndActiveTrue(
            SeverityLevel severity, Long issueCategoryId
    );

    // Precedence Level 4: Global (Building is NULL) + Severity (Category is NULL)
    // Returns List to be resilient against accidental duplicate seeding
    List<SlaPolicy> findBySeverityAndBuildingIsNullAndIssueCategoryIsNullAndActiveTrue(
            SeverityLevel severity
    );

    // Conflict detection for active policies
    @Query("""
        SELECT p FROM SlaPolicy p
        WHERE p.active = true
          AND (:buildingId IS NULL AND p.building IS NULL OR p.building.id = :buildingId)
          AND (:categoryId IS NULL AND p.issueCategory IS NULL OR p.issueCategory.id = :categoryId)
          AND p.severity = :severity
          AND (:excludeId IS NULL OR p.id <> :excludeId)
    """)
    List<SlaPolicy> findConflictingPolicies(
            @Param("buildingId") Long buildingId,
            @Param("categoryId") Long categoryId,
            @Param("severity") SeverityLevel severity,
            @Param("excludeId") Long excludeId
    );
}
