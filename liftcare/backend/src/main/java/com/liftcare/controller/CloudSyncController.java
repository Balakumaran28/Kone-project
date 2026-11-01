package com.liftcare.controller;

import com.liftcare.dto.CloudSyncBatchRequest;
import com.liftcare.dto.CloudSyncBatchResponse;
import com.liftcare.exception.InvalidRequestException;
import com.liftcare.service.CloudRegistrationService;
import com.liftcare.service.CloudSyncIngestionService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cloud/sync")
public class CloudSyncController {

    private static final Logger log = LoggerFactory.getLogger(CloudSyncController.class);

    private final CloudRegistrationService registrationService;
    private final CloudSyncIngestionService ingestionService;

    public CloudSyncController(CloudRegistrationService registrationService,
                               CloudSyncIngestionService ingestionService) {
        this.registrationService = registrationService;
        this.ingestionService = ingestionService;
    }

    @PostMapping("/batch")
    public ResponseEntity<CloudSyncBatchResponse> ingestBatch(
            @RequestHeader(value = "X-Building-Code", required = false) String headerBuildingCode,
            @RequestHeader(value = "X-Building-Api-Key", required = false) String apiKey,
            @Valid @RequestBody CloudSyncBatchRequest request) {

        if (headerBuildingCode == null || headerBuildingCode.isBlank() ||
                apiKey == null || apiKey.isBlank()) {
            log.warn("Missing building authentication headers in cloud sync request");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String buildingCode = headerBuildingCode.trim().toUpperCase();

        // 1. Authenticate building server identity using server credentials
        boolean valid = registrationService.validateBuildingCredentials(buildingCode, apiKey);
        if (!valid) {
            log.warn("Unauthorized cloud sync attempt for building code='{}'", buildingCode);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 2. Enforce strict tenant isolation: request payload buildingCode must match header
        if (!buildingCode.equalsIgnoreCase(request.buildingCode().trim())) {
            log.warn("Tenant isolation breach attempt: header='{}', payload='{}'",
                    buildingCode, request.buildingCode());
            throw new InvalidRequestException("Tenant isolation violation: building code mismatch between header and payload");
        }

        CloudSyncBatchResponse response = ingestionService.ingestBatch(buildingCode, request);
        return ResponseEntity.ok(response);
    }
}
