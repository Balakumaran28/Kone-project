package com.liftcare.controller;

import com.liftcare.dto.DashboardSummaryResponse;
import com.liftcare.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing real-time operational telemetry for the Building Control-Room Dashboard.
 * Strictly restricted to authorized staff roles (Security, Management, Technicians, Supervisors, Administrators).
 * Residents are forbidden (403) from accessing control-room operations.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'BUILDING_MANAGER', 'SECURITY_STAFF', 'TECHNICIAN', 'COMPANY_SUPERVISOR')")
    public ResponseEntity<DashboardSummaryResponse> getDashboardSummary(
            @RequestParam(required = false) Long buildingId) {
        DashboardSummaryResponse response = dashboardService.getDashboardSummary(buildingId);
        return ResponseEntity.ok(response);
    }
}
