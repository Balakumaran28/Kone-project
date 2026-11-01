package com.liftcare.service;

import com.liftcare.dto.BuildingRegistrationRequest;
import com.liftcare.dto.BuildingRegistrationResponse;
import com.liftcare.exception.DuplicateResourceException;
import com.liftcare.exception.ResourceNotFoundException;
import com.liftcare.model.CloudRegisteredBuilding;
import com.liftcare.repository.CloudRegisteredBuildingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

@Service
public class CloudRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(CloudRegistrationService.class);
    private final CloudRegisteredBuildingRepository buildingRepository;
    private final PasswordEncoder passwordEncoder;

    public CloudRegistrationService(CloudRegisteredBuildingRepository buildingRepository,
                                    PasswordEncoder passwordEncoder) {
        this.buildingRepository = buildingRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public BuildingRegistrationResponse registerBuilding(BuildingRegistrationRequest request) {
        String code = request.buildingCode().trim().toUpperCase();
        if (buildingRepository.existsByBuildingCode(code)) {
            throw new DuplicateResourceException("Building with code '" + code + "' is already registered in cloud directory");
        }

        String rawKey = request.apiKey();
        if (rawKey == null || rawKey.isBlank()) {
            rawKey = generateSecureApiKey();
        } else {
            rawKey = rawKey.trim();
        }

        String hashedKey = passwordEncoder.encode(rawKey);
        CloudRegisteredBuilding building = new CloudRegisteredBuilding(code, request.buildingName().trim(), hashedKey);
        CloudRegisteredBuilding saved = buildingRepository.save(building);

        log.info("Registered building in cloud registry: code='{}', name='{}'", code, saved.getBuildingName());

        return new BuildingRegistrationResponse(
                saved.getId(),
                saved.getBuildingCode(),
                saved.getBuildingName(),
                rawKey,
                saved.getStatus(),
                saved.getRegisteredAt(),
                saved.getLastSyncedAt()
        );
    }

    @Transactional(readOnly = true)
    public boolean validateBuildingCredentials(String buildingCode, String rawApiKey) {
        if (buildingCode == null || rawApiKey == null || buildingCode.isBlank() || rawApiKey.isBlank()) {
            return false;
        }
        return buildingRepository.findByBuildingCode(buildingCode.trim().toUpperCase())
                .filter(b -> "ACTIVE".equalsIgnoreCase(b.getStatus()))
                .map(b -> passwordEncoder.matches(rawApiKey.trim(), b.getApiKeyHash()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public CloudRegisteredBuilding getBuildingByCode(String buildingCode) {
        return buildingRepository.findByBuildingCode(buildingCode.trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Registered building not found for code: " + buildingCode));
    }

    @Transactional(readOnly = true)
    public List<CloudRegisteredBuilding> getAllRegisteredBuildings() {
        return buildingRepository.findAll();
    }

    private String generateSecureApiKey() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return "lc_bld_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
