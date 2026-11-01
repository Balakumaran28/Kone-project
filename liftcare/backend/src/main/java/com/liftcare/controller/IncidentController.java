package com.liftcare.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liftcare.dto.*;
import com.liftcare.model.ActorType;
import com.liftcare.model.IdempotencyRecord;
import com.liftcare.model.IncidentStatus;
import com.liftcare.model.SeverityLevel;
import com.liftcare.security.CustomUserDetails;
import com.liftcare.service.IdempotencyService;
import com.liftcare.service.IncidentService;
import com.liftcare.service.SlaEvaluationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class IncidentController {

    private final IncidentService incidentService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final SlaEvaluationService slaEvaluationService;

    public IncidentController(
            IncidentService incidentService,
            IdempotencyService idempotencyService,
            ObjectMapper objectMapper,
            @Autowired(required = false) SlaEvaluationService slaEvaluationService) {
        this.incidentService = incidentService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
        this.slaEvaluationService = slaEvaluationService;
    }

    @PostMapping("/incidents")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<IncidentResponse> createIncident(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody IncidentCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (idempotencyKey != null && !idempotencyKey.isBlank() && idempotencyService != null) {
            String hash = idempotencyService.computePayloadHash(request);
            Optional<IdempotencyRecord> existing = idempotencyService.findExistingRecord(
                    userDetails.getUser(), idempotencyKey, hash
            );
            if (existing.isPresent()) {
                try {
                    IncidentResponse cached = objectMapper.readValue(existing.get().getResponseBody(), IncidentResponse.class);
                    return ResponseEntity.status(existing.get().getResponseStatus())
                            .header("X-Idempotency-Cache", "HIT")
                            .body(cached);
                } catch (Exception e) {
                    // Fallback to normal execution if deserialization fails
                }
            }
            IncidentResponse response = incidentService.createIncident(request, userDetails.getUser());
            idempotencyService.saveSuccessRecord(
                    idempotencyKey, userDetails.getUser(), "/api/incidents", hash, HttpStatus.CREATED.value(), response
            );
            return ResponseEntity.status(HttpStatus.CREATED)
                    .header("X-Idempotency-Cache", "MISS")
                    .body(response);
        }

        IncidentResponse response = incidentService.createIncident(request, userDetails.getUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/incidents/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<IncidentDetailResponse> getIncidentById(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        IncidentDetailResponse response = incidentService.getIncidentById(id, userDetails.getUser());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/incidents/{id}/sla")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<com.liftcare.dto.IncidentSlaDetailResponse> getIncidentSlaDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        // Enforce resident ownership check
        incidentService.getIncidentById(id, userDetails.getUser());
        if (slaEvaluationService == null) {
            return ResponseEntity.notFound().build();
        }
        com.liftcare.dto.IncidentSlaDetailResponse response = slaEvaluationService.getIncidentSlaDetail(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/incidents/{id}/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<IncidentHistoryPageResponse> getIncidentHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "ASC") String sort,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) ActorType actorType,
            @RequestParam(required = false) Long actorId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        IncidentHistoryPageResponse response = incidentService.getIncidentHistory(
                id, page, size, sort, action, actorType, actorId, userDetails.getUser()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/incidents/{id}/notes")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<IncidentHistoryResponse> addIncidentNote(
            @PathVariable Long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody IncidentNoteRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (idempotencyKey != null && !idempotencyKey.isBlank() && idempotencyService != null) {
            String hash = idempotencyService.computePayloadHash(request);
            Optional<IdempotencyRecord> existing = idempotencyService.findExistingRecord(
                    userDetails.getUser(), idempotencyKey, hash
            );
            if (existing.isPresent()) {
                try {
                    IncidentHistoryResponse cached = objectMapper.readValue(existing.get().getResponseBody(), IncidentHistoryResponse.class);
                    return ResponseEntity.status(existing.get().getResponseStatus())
                            .header("X-Idempotency-Cache", "HIT")
                            .body(cached);
                } catch (Exception e) {
                    // Fallback
                }
            }
            IncidentHistoryResponse response = incidentService.addIncidentNote(id, request, userDetails.getUser());
            idempotencyService.saveSuccessRecord(
                    idempotencyKey, userDetails.getUser(), "/api/incidents/" + id + "/notes", hash, HttpStatus.CREATED.value(), response
            );
            return ResponseEntity.status(HttpStatus.CREATED)
                    .header("X-Idempotency-Cache", "MISS")
                    .body(response);
        }

        IncidentHistoryResponse response = incidentService.addIncidentNote(id, request, userDetails.getUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/incidents")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<IncidentResponse>> getIncidents(
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) SeverityLevel severity,
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Long elevatorId,
            @RequestParam(required = false) Boolean entrapmentOnly,
            @RequestParam(required = false) Long assignedToId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<IncidentResponse> response = incidentService.getIncidents(
                status, severity, buildingId, elevatorId, entrapmentOnly, assignedToId, userDetails.getUser()
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/incidents/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<IncidentResponse>> getMyIncidents(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<IncidentResponse> response = incidentService.getMyIncidents(userDetails.getUser());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/buildings/{buildingId}/incidents")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<IncidentResponse>> getIncidentsByBuilding(
            @PathVariable Long buildingId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<IncidentResponse> response = incidentService.getIncidentsByBuilding(buildingId, userDetails.getUser());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/elevators/{elevatorId}/incidents")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<IncidentResponse>> getIncidentsByElevator(
            @PathVariable Long elevatorId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<IncidentResponse> response = incidentService.getIncidentsByElevator(elevatorId, userDetails.getUser());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/incidents/{id}/acknowledge")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'BUILDING_MANAGER', 'SECURITY_STAFF', 'TECHNICIAN', 'COMPANY_SUPERVISOR')")
    public ResponseEntity<IncidentResponse> acknowledgeIncident(
            @PathVariable Long id,
            @RequestParam(required = false) String remarks,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        IncidentResponse response = incidentService.acknowledgeIncident(id, userDetails.getUser(), remarks);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/incidents/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'BUILDING_MANAGER', 'SECURITY_STAFF', 'COMPANY_SUPERVISOR')")
    public ResponseEntity<IncidentResponse> assignIncident(
            @PathVariable Long id,
            @Valid @RequestBody IncidentAssignRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        IncidentResponse response = incidentService.assignIncident(id, request, userDetails.getUser());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/incidents/{id}/status")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'BUILDING_MANAGER', 'TECHNICIAN')")
    public ResponseEntity<IncidentResponse> updateIncidentStatus(
            @PathVariable Long id,
            @Valid @RequestBody IncidentStatusUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        IncidentResponse response = incidentService.updateIncidentStatus(id, request, userDetails.getUser());
        return ResponseEntity.ok(response);
    }
}
