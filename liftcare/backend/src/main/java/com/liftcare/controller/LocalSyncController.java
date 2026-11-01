package com.liftcare.controller;

import com.liftcare.dto.CloudSyncBatchResponse;
import com.liftcare.dto.LocalSyncStatusResponse;
import com.liftcare.service.LocalCloudSyncClientService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/local-sync")
public class LocalSyncController {

    private final LocalCloudSyncClientService localSyncClientService;

    public LocalSyncController(LocalCloudSyncClientService localSyncClientService) {
        this.localSyncClientService = localSyncClientService;
    }

    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'BUILDING_MANAGER', 'TECHNICIAN')")
    public ResponseEntity<LocalSyncStatusResponse> getStatus(
            @RequestParam(required = false) String buildingCode) {
        return ResponseEntity.ok(localSyncClientService.getLocalSyncStatus(buildingCode));
    }

    @PostMapping("/trigger")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'BUILDING_MANAGER')")
    public ResponseEntity<CloudSyncBatchResponse> triggerSync(
            @RequestParam(required = false) String buildingCode) {
        return ResponseEntity.ok(localSyncClientService.triggerLocalSync(buildingCode));
    }

    @PostMapping("/simulation/offline")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<Map<String, Object>> toggleSimulationOffline(@RequestBody Map<String, Boolean> body) {
        boolean offline = Boolean.TRUE.equals(body.get("offline"));
        localSyncClientService.setSimulatedCloudOffline(offline);
        return ResponseEntity.ok(Map.of(
                "simulatedCloudOffline", offline,
                "message", offline ? "Cloud connectivity simulated as OFFLINE" : "Cloud connectivity simulated as ONLINE"
        ));
    }
}
