package com.liftcare.controller;

import com.liftcare.dto.BuildingRegistrationRequest;
import com.liftcare.dto.BuildingRegistrationResponse;
import com.liftcare.model.CloudRegisteredBuilding;
import com.liftcare.service.CloudRegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cloud/buildings")
@PreAuthorize("hasAnyRole('ADMINISTRATOR', 'COMPANY_SUPERVISOR')")
public class CloudRegistrationController {

    private final CloudRegistrationService registrationService;

    public CloudRegistrationController(CloudRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/register")
    public ResponseEntity<BuildingRegistrationResponse> registerBuilding(
            @Valid @RequestBody BuildingRegistrationRequest request) {
        BuildingRegistrationResponse response = registrationService.registerBuilding(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<CloudRegisteredBuilding>> getAllRegisteredBuildings() {
        return ResponseEntity.ok(registrationService.getAllRegisteredBuildings());
    }

    @GetMapping("/{buildingCode}")
    public ResponseEntity<CloudRegisteredBuilding> getBuildingByCode(@PathVariable String buildingCode) {
        return ResponseEntity.ok(registrationService.getBuildingByCode(buildingCode));
    }
}
