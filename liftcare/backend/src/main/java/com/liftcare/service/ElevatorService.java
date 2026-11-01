package com.liftcare.service;

import com.liftcare.dto.ElevatorCreateRequest;
import com.liftcare.dto.ElevatorResponse;
import com.liftcare.dto.ElevatorStatusUpdateRequest;
import com.liftcare.dto.ElevatorUpdateRequest;
import com.liftcare.exception.DuplicateResourceException;
import com.liftcare.exception.ResourceNotFoundException;
import com.liftcare.model.Building;
import com.liftcare.model.Elevator;
import com.liftcare.model.ElevatorOperationalStatus;
import com.liftcare.repository.BuildingRepository;
import com.liftcare.repository.ElevatorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ElevatorService {

    private static final Logger log = LoggerFactory.getLogger(ElevatorService.class);

    private final ElevatorRepository elevatorRepository;
    private final BuildingRepository buildingRepository;
    private final CloudSyncOutboxService cloudSyncOutboxService;

    public ElevatorService(ElevatorRepository elevatorRepository, BuildingRepository buildingRepository) {
        this(elevatorRepository, buildingRepository, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ElevatorService(
            ElevatorRepository elevatorRepository,
            BuildingRepository buildingRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) CloudSyncOutboxService cloudSyncOutboxService) {
        this.elevatorRepository = elevatorRepository;
        this.buildingRepository = buildingRepository;
        this.cloudSyncOutboxService = cloudSyncOutboxService;
    }

    @Transactional
    public ElevatorResponse createElevator(Long buildingId, ElevatorCreateRequest request) {
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new ResourceNotFoundException("Building not found with id: " + buildingId));

        String normalizedCode = request.elevatorCode().trim();
        if (elevatorRepository.existsByBuildingIdAndElevatorCode(buildingId, normalizedCode)) {
            throw new DuplicateResourceException(
                    "Elevator with code '" + normalizedCode + "' already exists in building '" + building.getBuildingCode() + "'."
            );
        }

        ElevatorOperationalStatus initialStatus = request.operationalStatus() != null
                ? request.operationalStatus()
                : ElevatorOperationalStatus.ACTIVE;

        Elevator elevator = new Elevator(
                normalizedCode,
                building,
                request.locationDescription() != null ? request.locationDescription().trim() : null,
                initialStatus
        );

        elevator.setManufacturer(request.manufacturer() != null ? request.manufacturer().trim() : null);
        elevator.setModel(request.model() != null ? request.model().trim() : null);
        elevator.setServiceProvider(request.serviceProvider() != null ? request.serviceProvider().trim() : null);
        elevator.setTotalFloors(request.totalFloors());

        Elevator saved = elevatorRepository.save(elevator);
        log.info("Created elevator id={}, code='{}' under building id={}", saved.getId(), saved.getElevatorCode(), buildingId);
        return ElevatorResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ElevatorResponse> getElevators(Long buildingId, ElevatorOperationalStatus status) {
        if (buildingId != null && !buildingRepository.existsById(buildingId)) {
            throw new ResourceNotFoundException("Building not found with id: " + buildingId);
        }

        List<Elevator> elevators;
        if (buildingId != null && status != null) {
            elevators = elevatorRepository.findByBuildingIdAndOperationalStatus(buildingId, status);
        } else if (buildingId != null) {
            elevators = elevatorRepository.findByBuildingId(buildingId);
        } else if (status != null) {
            elevators = elevatorRepository.findByOperationalStatus(status);
        } else {
            elevators = elevatorRepository.findAll();
        }

        return elevators.stream()
                .map(ElevatorResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ElevatorResponse> getElevatorsByBuildingId(Long buildingId, ElevatorOperationalStatus statusFilter) {
        if (!buildingRepository.existsById(buildingId)) {
            throw new ResourceNotFoundException("Building not found with id: " + buildingId);
        }

        List<Elevator> elevators = statusFilter != null
                ? elevatorRepository.findByBuildingIdAndOperationalStatus(buildingId, statusFilter)
                : elevatorRepository.findByBuildingId(buildingId);

        return elevators.stream()
                .map(ElevatorResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ElevatorResponse getElevatorById(Long id) {
        Elevator elevator = elevatorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Elevator not found with id: " + id));
        return ElevatorResponse.from(elevator);
    }

    @Transactional
    public ElevatorResponse updateElevator(Long id, ElevatorUpdateRequest request) {
        Elevator elevator = elevatorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Elevator not found with id: " + id));

        String normalizedCode = request.elevatorCode().trim();
        Long buildingId = elevator.getBuilding().getId();

        if (elevatorRepository.existsByBuildingIdAndElevatorCodeAndIdNot(buildingId, normalizedCode, id)) {
            throw new DuplicateResourceException(
                    "Elevator with code '" + normalizedCode + "' already exists in building id: " + buildingId
            );
        }

        elevator.setElevatorCode(normalizedCode);
        elevator.setLocationDescription(request.locationDescription() != null ? request.locationDescription().trim() : null);
        elevator.setManufacturer(request.manufacturer() != null ? request.manufacturer().trim() : null);
        elevator.setModel(request.model() != null ? request.model().trim() : null);
        elevator.setServiceProvider(request.serviceProvider() != null ? request.serviceProvider().trim() : null);
        elevator.setTotalFloors(request.totalFloors());

        if (request.operationalStatus() != null) {
            elevator.setOperationalStatus(request.operationalStatus());
        }

        Elevator saved = elevatorRepository.save(elevator);
        log.info("Updated elevator id={}, code='{}'", saved.getId(), saved.getElevatorCode());
        return ElevatorResponse.from(saved);
    }

    @Transactional
    public ElevatorResponse updateElevatorStatus(Long id, ElevatorStatusUpdateRequest request) {
        Elevator elevator = elevatorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Elevator not found with id: " + id));

        elevator.setOperationalStatus(request.operationalStatus());
        Elevator saved = elevatorRepository.save(elevator);
        log.info("Updated elevator id={} operationalStatus to '{}'", saved.getId(), saved.getOperationalStatus());
        if (cloudSyncOutboxService != null) {
            cloudSyncOutboxService.queueElevatorStatusChanged(saved);
        }
        return ElevatorResponse.from(saved);
    }

    @Transactional
    public ElevatorResponse deactivateElevator(Long id) {
        Elevator elevator = elevatorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Elevator not found with id: " + id));

        elevator.setOperationalStatus(ElevatorOperationalStatus.OUT_OF_SERVICE);
        Elevator saved = elevatorRepository.save(elevator);
        log.info("Deactivated elevator id={} (set operationalStatus to OUT_OF_SERVICE)", saved.getId());
        if (cloudSyncOutboxService != null) {
            cloudSyncOutboxService.queueElevatorStatusChanged(saved);
        }
        return ElevatorResponse.from(saved);
    }
}
