package com.liftcare.service;

import com.liftcare.dto.BuildingCreateRequest;
import com.liftcare.dto.BuildingResponse;
import com.liftcare.dto.BuildingUpdateRequest;
import com.liftcare.exception.DuplicateResourceException;
import com.liftcare.exception.ResourceNotFoundException;
import com.liftcare.model.Building;
import com.liftcare.repository.BuildingRepository;
import com.liftcare.repository.ElevatorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BuildingService {

    private static final Logger log = LoggerFactory.getLogger(BuildingService.class);

    private final BuildingRepository buildingRepository;
    private final ElevatorRepository elevatorRepository;

    public BuildingService(BuildingRepository buildingRepository, ElevatorRepository elevatorRepository) {
        this.buildingRepository = buildingRepository;
        this.elevatorRepository = elevatorRepository;
    }

    @Transactional
    public BuildingResponse createBuilding(BuildingCreateRequest request) {
        String normalizedCode = request.buildingCode().trim();
        if (buildingRepository.existsByBuildingCode(normalizedCode)) {
            throw new DuplicateResourceException("Building with code '" + normalizedCode + "' already exists.");
        }

        Building building = new Building(
                normalizedCode,
                request.name().trim(),
                request.address().trim(),
                request.city() != null ? request.city().trim() : null,
                request.localNodeId() != null ? request.localNodeId().trim() : null
        );

        Building saved = buildingRepository.save(building);
        log.info("Created building id={}, code='{}', name='{}'", saved.getId(), saved.getBuildingCode(), saved.getName());
        return BuildingResponse.from(saved, 0);
    }

    @Transactional(readOnly = true)
    public List<BuildingResponse> getBuildings(boolean includeInactive, Authentication authentication) {
        boolean canViewInactive = isPrivilegedRole(authentication);
        boolean shouldIncludeInactive = includeInactive && canViewInactive;

        List<Building> buildings = shouldIncludeInactive
                ? buildingRepository.findAll()
                : buildingRepository.findByActiveTrue();

        return buildings.stream()
                .map(b -> BuildingResponse.from(b, elevatorRepository.countByBuildingId(b.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public BuildingResponse getBuildingById(Long id, Authentication authentication) {
        Building building = buildingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Building not found with id: " + id));

        if (!building.isActive() && !isPrivilegedRole(authentication)) {
            throw new ResourceNotFoundException("Building not found with id: " + id);
        }

        long elevatorCount = elevatorRepository.countByBuildingId(id);
        return BuildingResponse.from(building, elevatorCount);
    }

    @Transactional
    public BuildingResponse updateBuilding(Long id, BuildingUpdateRequest request) {
        Building building = buildingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Building not found with id: " + id));

        building.setName(request.name().trim());
        building.setAddress(request.address().trim());
        building.setCity(request.city() != null ? request.city().trim() : null);
        building.setLocalNodeId(request.localNodeId() != null ? request.localNodeId().trim() : null);

        if (request.active() != null) {
            building.setActive(request.active());
        }

        Building updated = buildingRepository.save(building);
        log.info("Updated building id={}, code='{}'", updated.getId(), updated.getBuildingCode());
        return BuildingResponse.from(updated, elevatorRepository.countByBuildingId(id));
    }

    @Transactional
    public BuildingResponse deactivateBuilding(Long id) {
        Building building = buildingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Building not found with id: " + id));

        building.setActive(false);
        Building saved = buildingRepository.save(building);
        log.info("Deactivated building id={}, code='{}'", saved.getId(), saved.getBuildingCode());
        return BuildingResponse.from(saved, elevatorRepository.countByBuildingId(id));
    }

    private boolean isPrivilegedRole(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String auth = authority.getAuthority();
            if ("ROLE_ADMINISTRATOR".equals(auth) || "ROLE_BUILDING_MANAGER".equals(auth) || "ROLE_COMPANY_SUPERVISOR".equals(auth)) {
                return true;
            }
        }
        return false;
    }
}
