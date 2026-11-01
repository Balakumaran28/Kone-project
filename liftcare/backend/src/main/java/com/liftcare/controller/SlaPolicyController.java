package com.liftcare.controller;

import com.liftcare.dto.SlaPolicyRequest;
import com.liftcare.dto.SlaPolicyResponse;
import com.liftcare.model.SeverityLevel;
import com.liftcare.service.SlaPolicyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sla-policies")
public class SlaPolicyController {

    private final SlaPolicyService slaPolicyService;

    public SlaPolicyController(SlaPolicyService slaPolicyService) {
        this.slaPolicyService = slaPolicyService;
    }

    /**
     * Lists SLA policies with optional building, severity, and active filters.
     * Accessible by operational staff (residents forbidden).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'BUILDING_MANAGER', 'SECURITY_STAFF', 'COMPANY_SUPERVISOR', 'TECHNICIAN')")
    public ResponseEntity<List<SlaPolicyResponse>> getPolicies(
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) SeverityLevel severity,
            @RequestParam(required = false, defaultValue = "false") Boolean activeOnly) {
        List<SlaPolicyResponse> policies = slaPolicyService.getPolicies(buildingId, severity, activeOnly);
        return ResponseEntity.ok(policies);
    }

    /**
     * Retrieves an individual SLA policy by its primary key.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'BUILDING_MANAGER', 'SECURITY_STAFF', 'COMPANY_SUPERVISOR', 'TECHNICIAN')")
    public ResponseEntity<SlaPolicyResponse> getPolicyById(@PathVariable Long id) {
        SlaPolicyResponse policy = slaPolicyService.getPolicyById(id);
        return ResponseEntity.ok(policy);
    }

    /**
     * Creates a new SLA policy. Restricted to ADMINISTRATOR.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<SlaPolicyResponse> createPolicy(@Valid @RequestBody SlaPolicyRequest request) {
        SlaPolicyResponse created = slaPolicyService.createPolicy(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Updates an existing SLA policy and increments its version. Restricted to ADMINISTRATOR.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<SlaPolicyResponse> updatePolicy(
            @PathVariable Long id,
            @Valid @RequestBody SlaPolicyRequest request) {
        SlaPolicyResponse updated = slaPolicyService.updatePolicy(id, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * Safely deactivates an SLA policy. Restricted to ADMINISTRATOR.
     */
    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<SlaPolicyResponse> deactivatePolicy(@PathVariable Long id) {
        SlaPolicyResponse deactivated = slaPolicyService.deactivatePolicy(id);
        return ResponseEntity.ok(deactivated);
    }
}
