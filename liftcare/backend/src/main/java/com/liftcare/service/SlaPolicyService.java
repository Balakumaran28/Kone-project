package com.liftcare.service;

import com.liftcare.dto.SlaPolicyRequest;
import com.liftcare.dto.SlaPolicyResponse;
import com.liftcare.exception.ResourceNotFoundException;
import com.liftcare.model.Building;
import com.liftcare.model.IssueCategory;
import com.liftcare.model.SeverityLevel;
import com.liftcare.model.SlaPolicy;
import com.liftcare.model.UserRole;
import com.liftcare.repository.BuildingRepository;
import com.liftcare.repository.IssueCategoryRepository;
import com.liftcare.repository.SlaPolicyRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class SlaPolicyService {

    private static final Logger log = LoggerFactory.getLogger(SlaPolicyService.class);

    private final SlaPolicyRepository slaPolicyRepository;
    private final BuildingRepository buildingRepository;
    private final IssueCategoryRepository issueCategoryRepository;

    public SlaPolicyService(
            SlaPolicyRepository slaPolicyRepository,
            BuildingRepository buildingRepository,
            IssueCategoryRepository issueCategoryRepository) {
        this.slaPolicyRepository = slaPolicyRepository;
        this.buildingRepository = buildingRepository;
        this.issueCategoryRepository = issueCategoryRepository;
    }

    @PostConstruct
    public void initDefaultPoliciesIfEmpty() {
        if (slaPolicyRepository.findBySeverityAndBuildingIsNullAndIssueCategoryIsNullAndActiveTrue(SeverityLevel.CRITICAL).isEmpty()) {
            slaPolicyRepository.save(new SlaPolicy(
                    "Global Critical SLA", null, SeverityLevel.CRITICAL, null,
                    15, 60, 5,
                    UserRole.TECHNICIAN, UserRole.BUILDING_MANAGER, UserRole.ADMINISTRATOR
            ));
        }
        if (slaPolicyRepository.findBySeverityAndBuildingIsNullAndIssueCategoryIsNullAndActiveTrue(SeverityLevel.HIGH).isEmpty()) {
            slaPolicyRepository.save(new SlaPolicy(
                    "Global High Severity SLA", null, SeverityLevel.HIGH, null,
                    30, 120, 10,
                    UserRole.TECHNICIAN, UserRole.BUILDING_MANAGER, UserRole.ADMINISTRATOR
            ));
        }
        if (slaPolicyRepository.findBySeverityAndBuildingIsNullAndIssueCategoryIsNullAndActiveTrue(SeverityLevel.MEDIUM).isEmpty()) {
            slaPolicyRepository.save(new SlaPolicy(
                    "Global Medium Severity SLA", null, SeverityLevel.MEDIUM, null,
                    60, 240, 15,
                    UserRole.TECHNICIAN, UserRole.BUILDING_MANAGER, UserRole.ADMINISTRATOR
            ));
        }
        if (slaPolicyRepository.findBySeverityAndBuildingIsNullAndIssueCategoryIsNullAndActiveTrue(SeverityLevel.LOW).isEmpty()) {
            slaPolicyRepository.save(new SlaPolicy(
                    "Global Low Severity SLA", null, SeverityLevel.LOW, null,
                    120, 480, 30,
                    UserRole.TECHNICIAN, UserRole.BUILDING_MANAGER, UserRole.ADMINISTRATOR
            ));
        }
    }

    /**
     * Resolves the most specific active SLA policy matching the incident attributes.
     * Precedence order:
     * 1. Building + Category + Severity
     * 2. Building + Severity (Category is NULL)
     * 3. Global + Category + Severity (Building is NULL)
     * 4. Global + Severity (Building is NULL, Category is NULL)
     */
    @Transactional(readOnly = true)
    public Optional<SlaPolicy> resolvePolicyForIncident(Building building, IssueCategory category, SeverityLevel severity) {
        if (severity == null) {
            return Optional.empty();
        }

        // 1. Building + Category + Severity
        if (building != null && category != null) {
            Optional<SlaPolicy> p1 = slaPolicyRepository
                    .findBySeverityAndBuildingIdAndIssueCategoryIdAndActiveTrue(severity, building.getId(), category.getId())
                    .stream().findFirst();
            if (p1.isPresent()) return p1;
        }

        // 2. Building + Severity (Category is NULL)
        if (building != null) {
            Optional<SlaPolicy> p2 = slaPolicyRepository
                    .findBySeverityAndBuildingIdAndIssueCategoryIsNullAndActiveTrue(severity, building.getId())
                    .stream().findFirst();
            if (p2.isPresent()) return p2;
        }

        // 3. Global (Building is NULL) + Category + Severity
        if (category != null) {
            Optional<SlaPolicy> p3 = slaPolicyRepository
                    .findBySeverityAndBuildingIsNullAndIssueCategoryIdAndActiveTrue(severity, category.getId())
                    .stream().findFirst();
            if (p3.isPresent()) return p3;
        }

        // 4. Global (Building is NULL) + Severity (Category is NULL)
        return slaPolicyRepository
                .findBySeverityAndBuildingIsNullAndIssueCategoryIsNullAndActiveTrue(severity)
                .stream().findFirst();
    }

    @Transactional(readOnly = true)
    public List<SlaPolicyResponse> getPolicies(Long buildingId, SeverityLevel severity, Boolean activeOnly) {
        List<SlaPolicy> policies;
        if (Boolean.TRUE.equals(activeOnly)) {
            policies = slaPolicyRepository.findByActiveTrue();
        } else {
            policies = slaPolicyRepository.findAll();
        }

        return policies.stream()
                .filter(p -> buildingId == null || (p.getBuilding() != null && p.getBuilding().getId().equals(buildingId)))
                .filter(p -> severity == null || p.getSeverity() == severity)
                .map(SlaPolicyResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SlaPolicyResponse getPolicyById(Long id) {
        SlaPolicy policy = slaPolicyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SLA Policy not found with id: " + id));
        return SlaPolicyResponse.from(policy);
    }

    @Transactional
    public SlaPolicyResponse createPolicy(SlaPolicyRequest request) {
        validateDurationsAndThresholds(request.responseTargetMinutes(), request.resolutionTargetMinutes(), request.warningThresholdMinutes());

        Building building = null;
        if (request.buildingId() != null) {
            building = buildingRepository.findById(request.buildingId())
                    .orElseThrow(() -> new ResourceNotFoundException("Building not found with id: " + request.buildingId()));
        }

        IssueCategory category = null;
        if (request.issueCategoryId() != null) {
            category = issueCategoryRepository.findById(request.issueCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Issue category not found with id: " + request.issueCategoryId()));
        }

        // Check for conflicting active policies
        boolean active = request.active() == null || request.active();
        if (active) {
            checkConflictingActivePolicies(building != null ? building.getId() : null, category != null ? category.getId() : null, request.severity(), null);
        }

        SlaPolicy policy = new SlaPolicy(
                request.name().trim(),
                building,
                request.severity(),
                category,
                request.responseTargetMinutes(),
                request.resolutionTargetMinutes(),
                request.warningThresholdMinutes(),
                request.tier1Role(),
                request.tier2Role(),
                request.tier3Role()
        );
        policy.setActive(active);

        SlaPolicy saved = slaPolicyRepository.save(policy);
        log.info("Created SLA policy id={} name='{}' severity={} buildingId={}",
                saved.getId(), saved.getName(), saved.getSeverity(), request.buildingId());
        return SlaPolicyResponse.from(saved);
    }

    @Transactional
    public SlaPolicyResponse updatePolicy(Long id, SlaPolicyRequest request) {
        SlaPolicy existing = slaPolicyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SLA Policy not found with id: " + id));

        validateDurationsAndThresholds(request.responseTargetMinutes(), request.resolutionTargetMinutes(), request.warningThresholdMinutes());

        Building building = null;
        if (request.buildingId() != null) {
            building = buildingRepository.findById(request.buildingId())
                    .orElseThrow(() -> new ResourceNotFoundException("Building not found with id: " + request.buildingId()));
        }

        IssueCategory category = null;
        if (request.issueCategoryId() != null) {
            category = issueCategoryRepository.findById(request.issueCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Issue category not found with id: " + request.issueCategoryId()));
        }

        boolean active = request.active() != null ? request.active() : existing.isActive();
        if (active) {
            checkConflictingActivePolicies(building != null ? building.getId() : null, category != null ? category.getId() : null, request.severity(), id);
        }

        existing.setName(request.name().trim());
        existing.setBuilding(building);
        existing.setSeverity(request.severity());
        existing.setIssueCategory(category);
        existing.setActive(active);
        existing.setResponseTargetMinutes(request.responseTargetMinutes());
        existing.setResolutionTargetMinutes(request.resolutionTargetMinutes());
        existing.setWarningThresholdMinutes(request.warningThresholdMinutes());
        if (request.tier1Role() != null) existing.setTier1Role(request.tier1Role());
        if (request.tier2Role() != null) existing.setTier2Role(request.tier2Role());
        if (request.tier3Role() != null) existing.setTier3Role(request.tier3Role());
        existing.setVersion(existing.getVersion() + 1);

        SlaPolicy updated = slaPolicyRepository.save(existing);
        log.info("Updated SLA policy id={} name='{}' version={}", updated.getId(), updated.getName(), updated.getVersion());
        return SlaPolicyResponse.from(updated);
    }

    @Transactional
    public SlaPolicyResponse deactivatePolicy(Long id) {
        SlaPolicy policy = slaPolicyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SLA Policy not found with id: " + id));
        policy.setActive(false);
        SlaPolicy saved = slaPolicyRepository.save(policy);
        log.info("Deactivated SLA policy id={}", id);
        return SlaPolicyResponse.from(saved);
    }

    private void validateDurationsAndThresholds(int responseMinutes, int resolutionMinutes, int warningMinutes) {
        if (responseMinutes <= 0) {
            throw new IllegalArgumentException("Response target duration must be greater than zero minutes.");
        }
        if (resolutionMinutes <= 0) {
            throw new IllegalArgumentException("Resolution target duration must be greater than zero minutes.");
        }
        if (warningMinutes < 0) {
            throw new IllegalArgumentException("Warning threshold duration cannot be negative.");
        }
        if (warningMinutes >= responseMinutes) {
            throw new IllegalArgumentException("Warning threshold (" + warningMinutes + "m) must be strictly less than response target duration (" + responseMinutes + "m).");
        }
        if (responseMinutes > resolutionMinutes) {
            throw new IllegalArgumentException("Response target duration (" + responseMinutes + "m) cannot exceed resolution target duration (" + resolutionMinutes + "m).");
        }
    }

    private void checkConflictingActivePolicies(Long buildingId, Long categoryId, SeverityLevel severity, Long excludeId) {
        List<SlaPolicy> conflicts = slaPolicyRepository.findConflictingPolicies(buildingId, categoryId, severity, excludeId);
        if (!conflicts.isEmpty()) {
            SlaPolicy conflict = conflicts.get(0);
            throw new IllegalArgumentException("An active conflicting SLA policy already exists with scope (Building: " +
                    (buildingId != null ? buildingId : "GLOBAL") + ", Category: " +
                    (categoryId != null ? categoryId : "ALL") + ", Severity: " + severity + "): '" + conflict.getName() + "' (ID: " + conflict.getId() + ")");
        }
    }
}
