package com.liftcare.service;

import com.liftcare.dto.*;
import com.liftcare.exception.ResourceNotFoundException;
import com.liftcare.model.*;
import com.liftcare.repository.*;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentService.class);

    private final IncidentRepository incidentRepository;
    private final IncidentHistoryRepository incidentHistoryRepository;
    private final BuildingRepository buildingRepository;
    private final ElevatorRepository elevatorRepository;
    private final IssueCategoryRepository issueCategoryRepository;
    private final UserRepository userRepository;
    private final NotificationOutboxService notificationOutboxService;
    private final SlaPolicyService slaPolicyService;
    private final CloudSyncOutboxService cloudSyncOutboxService;
    private final Clock clock;

    public IncidentService(
            IncidentRepository incidentRepository,
            IncidentHistoryRepository incidentHistoryRepository,
            BuildingRepository buildingRepository,
            ElevatorRepository elevatorRepository,
            IssueCategoryRepository issueCategoryRepository,
            UserRepository userRepository,
            NotificationOutboxService notificationOutboxService,
            @Autowired(required = false) SlaPolicyService slaPolicyService,
            @Autowired(required = false) Clock clock) {
        this(incidentRepository, incidentHistoryRepository, buildingRepository, elevatorRepository,
                issueCategoryRepository, userRepository, notificationOutboxService, slaPolicyService, null, clock);
    }

    @Autowired
    public IncidentService(
            IncidentRepository incidentRepository,
            IncidentHistoryRepository incidentHistoryRepository,
            BuildingRepository buildingRepository,
            ElevatorRepository elevatorRepository,
            IssueCategoryRepository issueCategoryRepository,
            UserRepository userRepository,
            NotificationOutboxService notificationOutboxService,
            @Autowired(required = false) SlaPolicyService slaPolicyService,
            @Autowired(required = false) CloudSyncOutboxService cloudSyncOutboxService,
            @Autowired(required = false) Clock clock) {
        this.incidentRepository = incidentRepository;
        this.incidentHistoryRepository = incidentHistoryRepository;
        this.buildingRepository = buildingRepository;
        this.elevatorRepository = elevatorRepository;
        this.issueCategoryRepository = issueCategoryRepository;
        this.userRepository = userRepository;
        this.notificationOutboxService = notificationOutboxService;
        this.slaPolicyService = slaPolicyService;
        this.cloudSyncOutboxService = cloudSyncOutboxService;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Transactional
    public IncidentResponse createIncident(IncidentCreateRequest request, User currentUser) {
        // 1. Verify building exists and is active
        Building building = buildingRepository.findById(request.buildingId())
                .orElseThrow(() -> new ResourceNotFoundException("Building not found with id: " + request.buildingId()));
        if (!building.isActive()) {
            throw new IllegalArgumentException("Cannot report an incident for an inactive building complex.");
        }

        // 2. Verify elevator exists, belongs to specified building, and is eligible
        Elevator elevator = elevatorRepository.findById(request.elevatorId())
                .orElseThrow(() -> new ResourceNotFoundException("Elevator not found with id: " + request.elevatorId()));
        if (!elevator.getBuilding().getId().equals(building.getId())) {
            throw new IllegalArgumentException("Elevator id " + request.elevatorId() + " does not belong to building id " + request.buildingId());
        }

        // 3. Verify issue category exists and is active
        IssueCategory category = issueCategoryRepository.findById(request.issueCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Issue category not found with id: " + request.issueCategoryId()));
        if (!category.isActive()) {
            throw new IllegalArgumentException("Cannot report an incident with an inactive issue category.");
        }

        // 4. Emergency & Severity Rules
        boolean isEntrapment = Boolean.TRUE.equals(request.entrapmentReported())
                || "ENTRAPMENT".equalsIgnoreCase(category.getCode());

        SeverityLevel resolvedSeverity;
        if (isEntrapment) {
            resolvedSeverity = SeverityLevel.CRITICAL;
            if (request.severity() != null && request.severity() != SeverityLevel.CRITICAL) {
                throw new IllegalArgumentException("Passenger entrapment incidents require CRITICAL severity.");
            }
        } else if (request.severity() != null) {
            resolvedSeverity = request.severity();
        } else if (category.getDefaultSeverity() != null) {
            resolvedSeverity = category.getDefaultSeverity();
        } else {
            resolvedSeverity = SeverityLevel.MEDIUM;
        }

        // 5. Generate unique Ticket Number (e.g., INC-20261031-A1B2C3)
        String datePrefix = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomSuffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String ticketNumber = String.format("INC-%s-%s", datePrefix, randomSuffix);

        // 6. Build Incident Entity (Reporter identity strictly derived from server security context)
        Instant now = clock.instant();
        Incident incident = new Incident(
                ticketNumber,
                building,
                elevator,
                currentUser,
                category,
                request.description().trim(),
                resolvedSeverity,
                isEntrapment
        );

        // Snapshot applicable SLA Policy and deadlines
        if (slaPolicyService != null) {
            Optional<SlaPolicy> policyOpt = slaPolicyService.resolvePolicyForIncident(building, category, resolvedSeverity);
            if (policyOpt.isPresent()) {
                incident.applySlaPolicy(policyOpt.get(), now);
            }
        }

        Incident saved = incidentRepository.save(incident);
        log.info("Incident created id={}, ticket='{}', building='{}', elevator='{}', severity={}, entrapment={}",
                saved.getId(), saved.getTicketNumber(), building.getBuildingCode(), elevator.getElevatorCode(), resolvedSeverity, isEntrapment);

        // 7. Record IncidentHistory entry (Actor strictly derived from authenticated session)
        IncidentHistory initialHistory = new IncidentHistory(
                saved,
                "INCIDENT_REPORTED",
                null,
                IncidentStatus.REPORTED.name(),
                currentUser,
                ActorType.USER,
                "Incident report submitted via local building network. Initial severity: " + resolvedSeverity
        );
        incidentHistoryRepository.save(initialHistory);

        // Record policy missing history if no policy was configured
        if (saved.getSlaPolicy() == null && saved.getResponseDeadline() == null) {
            IncidentHistory missingPolicyHistory = new IncidentHistory(
                    saved,
                    "SLA_POLICY_MISSING",
                    null,
                    "POLICY_MISSING",
                    null,
                    ActorType.SYSTEM,
                    "No active SLA policy configured for scope (Building: " + building.getBuildingCode() +
                            ", Category: " + category.getCode() + ", Severity: " + resolvedSeverity + "). Flagged as POLICY_MISSING."
            );
            incidentHistoryRepository.save(missingPolicyHistory);
        }

        // 8. Elevator Operational Status Safety Transition
        boolean qualifiesForOutOfService = isEntrapment
                || resolvedSeverity == SeverityLevel.CRITICAL
                || (category.isRequiresImmediateAttention() && resolvedSeverity == SeverityLevel.HIGH);

        if (qualifiesForOutOfService && elevator.getOperationalStatus() != ElevatorOperationalStatus.OUT_OF_SERVICE) {
            ElevatorOperationalStatus oldStatus = elevator.getOperationalStatus();
            elevator.setOperationalStatus(ElevatorOperationalStatus.OUT_OF_SERVICE);
            elevatorRepository.save(elevator);

            IncidentHistory elevatorHistory = new IncidentHistory(
                    saved,
                    "ELEVATOR_OUT_OF_SERVICE",
                    oldStatus.name(),
                    ElevatorOperationalStatus.OUT_OF_SERVICE.name(),
                    null,
                    ActorType.SYSTEM,
                    "Elevator automatically transitioned to OUT_OF_SERVICE due to safety incident (" + saved.getTicketNumber() + ")"
            );
            incidentHistoryRepository.save(elevatorHistory);
            log.warn("Elevator id={} automatically transitioned from {} to OUT_OF_SERVICE due to ticket={}",
                    elevator.getId(), oldStatus, ticketNumber);
        }

        // 9. Enqueue durable notification outbox records
        if (notificationOutboxService != null) {
            notificationOutboxService.queueIncidentCreated(saved);
        }

        // 10. Enqueue cloud synchronization outbox records
        if (cloudSyncOutboxService != null) {
            cloudSyncOutboxService.queueIncidentCreated(saved);
            if (qualifiesForOutOfService) {
                cloudSyncOutboxService.queueElevatorStatusChanged(elevator);
            }
        }

        return IncidentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public IncidentDetailResponse getIncidentById(Long id, User currentUser) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found with id: " + id));

        // Enforce resident scope: residents can only inspect incidents they reported
        if (currentUser.getRole() == UserRole.RESIDENT) {
            if (incident.getReportedBy() == null || !incident.getReportedBy().getId().equals(currentUser.getId())) {
                throw new AccessDeniedException("Access denied. Residents can only view their own reported incidents.");
            }
        }

        List<IncidentHistory> history = incidentHistoryRepository.findByIncidentIdOrderByChangedAtAsc(id);
        List<IncidentHistoryResponse> historyResponses = history.stream()
                .map(IncidentHistoryResponse::from)
                .toList();

        return new IncidentDetailResponse(IncidentResponse.from(incident), historyResponses);
    }

    @Transactional(readOnly = true)
    public IncidentHistoryPageResponse getIncidentHistory(
            Long incidentId,
            int page,
            int size,
            String sortDirection,
            String action,
            ActorType actorType,
            Long actorId,
            User currentUser) {

        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found with id: " + incidentId));

        // Enforce resident scope: residents can only inspect history for incidents they reported
        if (currentUser.getRole() == UserRole.RESIDENT) {
            if (incident.getReportedBy() == null || !incident.getReportedBy().getId().equals(currentUser.getId())) {
                throw new AccessDeniedException("Access denied. Residents can only view history for their own reported incidents.");
            }
        }

        Sort.Direction direction = "DESC".equalsIgnoreCase(sortDirection) ? Sort.Direction.DESC : Sort.Direction.ASC;
        int sanitizedPage = Math.max(0, page);
        int sanitizedSize = Math.max(1, Math.min(size, 100));
        Pageable pageable = PageRequest.of(sanitizedPage, sanitizedSize, Sort.by(direction, "changedAt", "id"));

        Specification<IncidentHistory> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("incident").get("id"), incidentId));

            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action.trim()));
            }
            if (actorType != null) {
                predicates.add(cb.equal(root.get("actorType"), actorType));
            }
            if (actorId != null) {
                predicates.add(cb.equal(root.get("changedBy").get("id"), actorId));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<IncidentHistory> historyPage = incidentHistoryRepository.findAll(spec, pageable);

        List<IncidentHistoryResponse> content = historyPage.getContent().stream()
                .map(IncidentHistoryResponse::from)
                .toList();

        return new IncidentHistoryPageResponse(
                content,
                historyPage.getNumber(),
                historyPage.getSize(),
                historyPage.getTotalElements(),
                historyPage.getTotalPages(),
                historyPage.hasNext(),
                historyPage.hasPrevious()
        );
    }

    @Transactional(readOnly = true)
    public List<IncidentResponse> getIncidents(
            IncidentStatus status,
            SeverityLevel severity,
            Long buildingId,
            Long elevatorId,
            Boolean entrapmentOnly,
            Long assignedToId,
            User currentUser) {

        // If current user is resident, return only their reports
        if (currentUser.getRole() == UserRole.RESIDENT) {
            return incidentRepository.findByReportedByIdOrderByReportedAtDesc(currentUser.getId()).stream()
                    .map(IncidentResponse::from)
                    .toList();
        }

        List<Incident> incidents;
        if (buildingId != null && status != null) {
            incidents = incidentRepository.findByBuildingIdAndStatus(buildingId, status);
        } else if (buildingId != null) {
            incidents = incidentRepository.findByBuildingIdOrderByReportedAtDesc(buildingId);
        } else if (elevatorId != null) {
            incidents = incidentRepository.findByElevatorIdOrderByReportedAtDesc(elevatorId);
        } else if (status != null) {
            incidents = incidentRepository.findByStatus(status);
        } else {
            incidents = incidentRepository.findAllByOrderByReportedAtDesc();
        }

        return incidents.stream()
                .filter(i -> severity == null || i.getSeverity() == severity)
                .filter(i -> entrapmentOnly == null || !entrapmentOnly || i.isEntrapmentReported())
                .filter(i -> assignedToId == null || (i.getAssignedTo() != null && i.getAssignedTo().getId().equals(assignedToId)))
                .map(IncidentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<IncidentResponse> getMyIncidents(User currentUser) {
        return incidentRepository.findByReportedByIdOrderByReportedAtDesc(currentUser.getId()).stream()
                .map(IncidentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<IncidentResponse> getIncidentsByBuilding(Long buildingId, User currentUser) {
        if (!buildingRepository.existsById(buildingId)) {
            throw new ResourceNotFoundException("Building not found with id: " + buildingId);
        }
        if (currentUser.getRole() == UserRole.RESIDENT) {
            return incidentRepository.findByBuildingIdOrderByReportedAtDesc(buildingId).stream()
                    .filter(i -> i.getReportedBy() != null && i.getReportedBy().getId().equals(currentUser.getId()))
                    .map(IncidentResponse::from)
                    .toList();
        }
        return incidentRepository.findByBuildingIdOrderByReportedAtDesc(buildingId).stream()
                .map(IncidentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<IncidentResponse> getIncidentsByElevator(Long elevatorId, User currentUser) {
        if (!elevatorRepository.existsById(elevatorId)) {
            throw new ResourceNotFoundException("Elevator not found with id: " + elevatorId);
        }
        if (currentUser.getRole() == UserRole.RESIDENT) {
            return incidentRepository.findByElevatorIdOrderByReportedAtDesc(elevatorId).stream()
                    .filter(i -> i.getReportedBy() != null && i.getReportedBy().getId().equals(currentUser.getId()))
                    .map(IncidentResponse::from)
                    .toList();
        }
        return incidentRepository.findByElevatorIdOrderByReportedAtDesc(elevatorId).stream()
                .map(IncidentResponse::from)
                .toList();
    }

    @Transactional
    public IncidentResponse acknowledgeIncident(Long id, User currentUser, String remarks) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found with id: " + id));

        if (incident.getStatus() == IncidentStatus.REPORTED) {
            Instant now = clock.instant();
            IncidentStatus oldStatus = incident.getStatus();
            incident.setStatus(IncidentStatus.ACKNOWLEDGED);
            incident.setAcknowledgedAt(now);
            incident.setSlaWarningSent(false);
            if (incident.getResponseDeadline() != null && now.isAfter(incident.getResponseDeadline())) {
                incident.setResponseSlaBreached(true);
            }
            Incident saved = incidentRepository.save(incident);

            IncidentHistory history = new IncidentHistory(
                    saved,
                    "ACKNOWLEDGED",
                    oldStatus.name(),
                    IncidentStatus.ACKNOWLEDGED.name(),
                    currentUser,
                    ActorType.USER,
                    remarks != null && !remarks.isBlank() ? remarks.trim() : "Incident acknowledged by on-duty staff"
            );
            incidentHistoryRepository.save(history);
            log.info("Incident id={} ticket='{}' acknowledged by user id={}", id, saved.getTicketNumber(), currentUser.getId());

            if (cloudSyncOutboxService != null) {
                cloudSyncOutboxService.queueIncidentUpdated(saved, "ACKNOWLEDGED");
            }

            return IncidentResponse.from(saved);
        }

        return IncidentResponse.from(incident);
    }

    @Transactional
    public IncidentResponse assignIncident(Long id, IncidentAssignRequest request, User currentUser) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found with id: " + id));

        User technician = userRepository.findById(request.technicianId())
                .orElseThrow(() -> new ResourceNotFoundException("Technician user not found with id: " + request.technicianId()));

        if (technician.getRole() != UserRole.TECHNICIAN) {
            throw new IllegalArgumentException("User '" + technician.getFullName() + "' does not hold the TECHNICIAN role (assigned role: " + technician.getRole() + ")");
        }
        if (!technician.isActive()) {
            throw new IllegalArgumentException("Cannot assign an incident to a deactivated technician account.");
        }

        boolean isReassignment = incident.getAssignedTo() != null;
        String previousAssigned = isReassignment
                ? incident.getAssignedTo().getFullName() + " (" + incident.getAssignedTo().getEmail() + ")"
                : "UNASSIGNED";

        incident.setAssignedTo(technician);

        // Transition status to ASSIGNED if currently in initial reported/acknowledged state
        if (incident.getStatus() == IncidentStatus.REPORTED || incident.getStatus() == IncidentStatus.ACKNOWLEDGED) {
            incident.setStatus(IncidentStatus.ASSIGNED);
        }

        Incident saved = incidentRepository.save(incident);

        String newAssigned = technician.getFullName() + " (" + technician.getEmail() + ")";
        String action = isReassignment ? "TECHNICIAN_REASSIGNED" : "ASSIGNED";
        IncidentHistory history = new IncidentHistory(
                saved,
                action,
                previousAssigned,
                newAssigned,
                currentUser,
                ActorType.USER,
                request.remarks() != null && !request.remarks().isBlank()
                        ? request.remarks().trim()
                        : (isReassignment ? "Reassigned to maintenance technician" : "Assigned to maintenance technician")
        );
        incidentHistoryRepository.save(history);
        log.info("Incident id={} ticket='{}' action='{}' technician id={} by user id={}",
                id, saved.getTicketNumber(), action, technician.getId(), currentUser.getId());

        if (notificationOutboxService != null) {
            notificationOutboxService.queueIncidentAssigned(saved, technician);
        }

        if (cloudSyncOutboxService != null) {
            cloudSyncOutboxService.queueIncidentUpdated(saved, action);
        }

        return IncidentResponse.from(saved);
    }

    @Transactional
    public IncidentResponse updateIncidentStatus(Long id, IncidentStatusUpdateRequest request, User currentUser) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found with id: " + id));

        IncidentStatus newStatus = request.status();
        IncidentStatus oldStatus = incident.getStatus();

        if (newStatus == IncidentStatus.REPORTED) {
            throw new IllegalArgumentException("Cannot transition an existing incident back to REPORTED status.");
        }
        if (oldStatus == IncidentStatus.CLOSED) {
            throw new IllegalArgumentException("Cannot update an incident that has already been CLOSED.");
        }

        // Technicians can only update incidents assigned to them or update to IN_PROGRESS/RESOLVED
        if (currentUser.getRole() == UserRole.TECHNICIAN) {
            if (newStatus != IncidentStatus.IN_PROGRESS && newStatus != IncidentStatus.RESOLVED) {
                throw new AccessDeniedException("Technicians are authorized only to transition incidents to IN_PROGRESS or RESOLVED.");
            }
        }

        Instant now = clock.instant();
        incident.setStatus(newStatus);
        if (newStatus == IncidentStatus.RESOLVED && incident.getResolvedAt() == null) {
            incident.setResolvedAt(now);
            if (incident.getResolutionDeadline() != null && now.isAfter(incident.getResolutionDeadline())) {
                incident.setResolutionSlaBreached(true);
            }
        } else if (oldStatus == IncidentStatus.RESOLVED && (newStatus == IncidentStatus.IN_PROGRESS || newStatus == IncidentStatus.ASSIGNED || newStatus == IncidentStatus.ACKNOWLEDGED)) {
            // Reopened incident: clear resolution timestamp and allow resolution warning if applicable
            incident.setResolvedAt(null);
            incident.setSlaWarningSent(false);
        }

        // Commencing work also acknowledges if not acknowledged already
        if ((newStatus == IncidentStatus.IN_PROGRESS || newStatus == IncidentStatus.RESOLVED) && incident.getAcknowledgedAt() == null) {
            incident.setAcknowledgedAt(now);
            incident.setSlaWarningSent(false);
            if (incident.getResponseDeadline() != null && now.isAfter(incident.getResponseDeadline())) {
                incident.setResponseSlaBreached(true);
            }
        }

        Incident saved = incidentRepository.save(incident);

        String action;
        if (oldStatus == IncidentStatus.RESOLVED && (newStatus == IncidentStatus.IN_PROGRESS || newStatus == IncidentStatus.ASSIGNED)) {
            action = "INCIDENT_REOPENED";
        } else if (newStatus == IncidentStatus.RESOLVED) {
            action = "RESOLVED";
        } else if (newStatus == IncidentStatus.CLOSED) {
            action = "CLOSED";
        } else {
            action = "STATUS_CHANGED";
        }

        IncidentHistory history = new IncidentHistory(
                saved,
                action,
                oldStatus.name(),
                newStatus.name(),
                currentUser,
                ActorType.USER,
                request.remarks() != null && !request.remarks().isBlank() ? request.remarks().trim() : "Incident status changed to " + newStatus
        );
        incidentHistoryRepository.save(history);
        log.info("Incident id={} ticket='{}' status transitioned from {} to {} by user id={}",
                id, saved.getTicketNumber(), oldStatus, newStatus, currentUser.getId());

        // Optional elevator operational status transition linked to incident triage
        if (request.elevatorStatus() != null && saved.getElevator() != null) {
            Elevator elevator = saved.getElevator();
            if (elevator.getOperationalStatus() != request.elevatorStatus()) {
                ElevatorOperationalStatus oldElevatorStatus = elevator.getOperationalStatus();
                elevator.setOperationalStatus(request.elevatorStatus());
                elevatorRepository.save(elevator);

                IncidentHistory elevatorHistory = new IncidentHistory(
                        saved,
                        "ELEVATOR_STATUS_CHANGED",
                        oldElevatorStatus.name(),
                        request.elevatorStatus().name(),
                        currentUser,
                        ActorType.USER,
                        "Elevator operational status transitioned from " + oldElevatorStatus + " to " + request.elevatorStatus() + " during incident triage (" + saved.getTicketNumber() + ")"
                );
                incidentHistoryRepository.save(elevatorHistory);
                log.info("Elevator id={} status transitioned from {} to {} during incident id={}",
                        elevator.getId(), oldElevatorStatus, request.elevatorStatus(), id);
            }
        }

        // Enqueue durable notification outbox records
        if (notificationOutboxService != null) {
            notificationOutboxService.queueIncidentStatusTransition(saved, oldStatus, newStatus);
        }

        if (cloudSyncOutboxService != null) {
            cloudSyncOutboxService.queueIncidentUpdated(saved, action);
            if (request.elevatorStatus() != null && saved.getElevator() != null) {
                cloudSyncOutboxService.queueElevatorStatusChanged(saved.getElevator());
            }
        }

        return IncidentResponse.from(saved);
    }

    @Transactional
    public IncidentHistoryResponse addIncidentNote(Long id, IncidentNoteRequest request, User currentUser) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found with id: " + id));

        // Enforce resident scope: residents can only append notes to their own open incidents
        if (currentUser.getRole() == UserRole.RESIDENT) {
            if (incident.getReportedBy() == null || !incident.getReportedBy().getId().equals(currentUser.getId())) {
                throw new AccessDeniedException("Access denied. Residents can only append notes to their own reported incidents.");
            }
            if (incident.getStatus() == IncidentStatus.CLOSED) {
                throw new IllegalArgumentException("Cannot append notes to a closed incident.");
            }
        }

        String noteType = request.noteType() != null ? request.noteType().trim().toUpperCase() : "";
        String action;

        if ("RESOLUTION".equals(noteType) || incident.getStatus() == IncidentStatus.RESOLVED) {
            action = "RESOLUTION_NOTES_UPDATED";
        } else if ("ADMINISTRATIVE".equals(noteType) || currentUser.getRole() == UserRole.ADMINISTRATOR) {
            action = "ADMINISTRATIVE_ACTION";
        } else {
            action = "WORK_NOTE_ADDED";
        }

        IncidentHistory noteHistory = new IncidentHistory(
                incident,
                action,
                null,
                request.notes().trim(),
                currentUser,
                ActorType.USER,
                request.notes().trim()
        );
        IncidentHistory saved = incidentHistoryRepository.save(noteHistory);
        log.info("Incident id={} audit note recorded action='{}' by user id={}", id, action, currentUser.getId());

        if (cloudSyncOutboxService != null) {
            cloudSyncOutboxService.queueIncidentUpdated(incident, action);
        }

        return IncidentHistoryResponse.from(saved);
    }
}
