package com.liftcare.controller;

import com.liftcare.dto.CentralDashboardSummaryResponse;
import com.liftcare.dto.ConflictResolutionRequest;
import com.liftcare.model.CloudIncidentReplica;
import com.liftcare.model.CloudSyncConflict;
import com.liftcare.service.CentralDashboardService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cloud")
@PreAuthorize("hasAnyRole('ADMINISTRATOR', 'COMPANY_SUPERVISOR')")
public class CentralDashboardController {

    private final CentralDashboardService centralDashboardService;

    public CentralDashboardController(CentralDashboardService centralDashboardService) {
        this.centralDashboardService = centralDashboardService;
    }

    @GetMapping("/dashboard/summary")
    public ResponseEntity<CentralDashboardSummaryResponse> getSummary() {
        return ResponseEntity.ok(centralDashboardService.getSummary());
    }

    @GetMapping("/incidents")
    public ResponseEntity<List<CloudIncidentReplica>> getIncidents(
            @RequestParam(required = false) String buildingCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity) {
        return ResponseEntity.ok(centralDashboardService.getIncidentReplicas(buildingCode, status, severity));
    }

    @GetMapping("/conflicts")
    public ResponseEntity<List<CloudSyncConflict>> getConflicts(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(centralDashboardService.getConflicts(status));
    }

    @PatchMapping("/conflicts/{conflictId}/resolve")
    public ResponseEntity<CloudSyncConflict> resolveConflict(
            @PathVariable String conflictId,
            @Valid @RequestBody ConflictResolutionRequest request,
            Authentication authentication) {
        String resolvedBy = authentication != null ? authentication.getName() : "ADMINISTRATOR";
        return ResponseEntity.ok(centralDashboardService.resolveConflict(conflictId, request, resolvedBy));
    }
}
