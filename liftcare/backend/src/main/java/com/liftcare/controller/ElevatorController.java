package com.liftcare.controller;

import com.liftcare.dto.ElevatorCreateRequest;
import com.liftcare.dto.ElevatorResponse;
import com.liftcare.dto.ElevatorStatusUpdateRequest;
import com.liftcare.dto.ElevatorUpdateRequest;
import com.liftcare.model.ElevatorOperationalStatus;
import com.liftcare.service.ElevatorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ElevatorController {

    private final ElevatorService elevatorService;

    public ElevatorController(ElevatorService elevatorService) {
        this.elevatorService = elevatorService;
    }

    @PostMapping("/buildings/{buildingId}/elevators")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'BUILDING_MANAGER')")
    public ResponseEntity<ElevatorResponse> createElevator(
            @PathVariable Long buildingId,
            @Valid @RequestBody ElevatorCreateRequest request) {
        ElevatorResponse response = elevatorService.createElevator(buildingId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/elevators")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ElevatorResponse>> getElevators(
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) ElevatorOperationalStatus status) {
        List<ElevatorResponse> elevators = elevatorService.getElevators(buildingId, status);
        return ResponseEntity.ok(elevators);
    }

    @GetMapping("/buildings/{buildingId}/elevators")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ElevatorResponse>> getElevatorsByBuilding(
            @PathVariable Long buildingId,
            @RequestParam(required = false) ElevatorOperationalStatus status) {
        List<ElevatorResponse> elevators = elevatorService.getElevatorsByBuildingId(buildingId, status);
        return ResponseEntity.ok(elevators);
    }

    @GetMapping("/elevators/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ElevatorResponse> getElevatorById(@PathVariable Long id) {
        ElevatorResponse response = elevatorService.getElevatorById(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/elevators/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'BUILDING_MANAGER')")
    public ResponseEntity<ElevatorResponse> updateElevator(
            @PathVariable Long id,
            @Valid @RequestBody ElevatorUpdateRequest request) {
        ElevatorResponse response = elevatorService.updateElevator(id, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/elevators/{id}/status")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'BUILDING_MANAGER', 'TECHNICIAN')")
    public ResponseEntity<ElevatorResponse> updateElevatorStatus(
            @PathVariable Long id,
            @Valid @RequestBody ElevatorStatusUpdateRequest request) {
        ElevatorResponse response = elevatorService.updateElevatorStatus(id, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/elevators/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'BUILDING_MANAGER')")
    public ResponseEntity<ElevatorResponse> deactivateElevator(@PathVariable Long id) {
        // Soft-deactivate by setting operational status to OUT_OF_SERVICE to preserve historical integrity
        ElevatorResponse response = elevatorService.deactivateElevator(id);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/elevators/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'BUILDING_MANAGER')")
    public ResponseEntity<ElevatorResponse> deleteElevator(@PathVariable Long id) {
        // Soft-deactivate to avoid breaking existing or future incident references
        ElevatorResponse response = elevatorService.deactivateElevator(id);
        return ResponseEntity.ok(response);
    }
}
