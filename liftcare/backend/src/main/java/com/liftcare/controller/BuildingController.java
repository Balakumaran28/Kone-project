package com.liftcare.controller;

import com.liftcare.dto.BuildingCreateRequest;
import com.liftcare.dto.BuildingResponse;
import com.liftcare.dto.BuildingUpdateRequest;
import com.liftcare.service.BuildingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/buildings")
public class BuildingController {

    private final BuildingService buildingService;

    public BuildingController(BuildingService buildingService) {
        this.buildingService = buildingService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<BuildingResponse> createBuilding(@Valid @RequestBody BuildingCreateRequest request) {
        BuildingResponse response = buildingService.createBuilding(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<BuildingResponse>> getBuildings(
            @RequestParam(defaultValue = "false") boolean includeInactive,
            Authentication authentication) {
        List<BuildingResponse> buildings = buildingService.getBuildings(includeInactive, authentication);
        return ResponseEntity.ok(buildings);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BuildingResponse> getBuildingById(
            @PathVariable Long id,
            Authentication authentication) {
        BuildingResponse response = buildingService.getBuildingById(id, authentication);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'BUILDING_MANAGER')")
    public ResponseEntity<BuildingResponse> updateBuilding(
            @PathVariable Long id,
            @Valid @RequestBody BuildingUpdateRequest request) {
        BuildingResponse response = buildingService.updateBuilding(id, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<BuildingResponse> deactivateBuilding(@PathVariable Long id) {
        BuildingResponse response = buildingService.deactivateBuilding(id);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<BuildingResponse> deleteBuilding(@PathVariable Long id) {
        // Soft deactivation to preserve historical records and references
        BuildingResponse response = buildingService.deactivateBuilding(id);
        return ResponseEntity.ok(response);
    }
}
