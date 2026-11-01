package com.liftcare.config;

import com.liftcare.model.*;
import com.liftcare.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@Profile("!test")
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final BuildingRepository buildingRepository;
    private final ElevatorRepository elevatorRepository;
    private final IssueCategoryRepository issueCategoryRepository;
    private final SlaPolicyRepository slaPolicyRepository;
    private final IncidentRepository incidentRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(
            UserRepository userRepository,
            BuildingRepository buildingRepository,
            ElevatorRepository elevatorRepository,
            IssueCategoryRepository issueCategoryRepository,
            SlaPolicyRepository slaPolicyRepository,
            IncidentRepository incidentRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.buildingRepository = buildingRepository;
        this.elevatorRepository = elevatorRepository;
        this.issueCategoryRepository = issueCategoryRepository;
        this.slaPolicyRepository = slaPolicyRepository;
        this.incidentRepository = incidentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // Always run SLA deduplication to fix any duplicate global policies from prior seeding bugs.
        deduplicateGlobalSlaPolicies();

        if (userRepository.count() > 0) {
            log.info("Database already contains user records. Skipping initial data seeding.");
            return;
        }

        log.info("Seeding initial demo data for LiftCare local instance...");
        String encodedPassword = passwordEncoder.encode("Password123!");

        // 1. Users
        User admin = userRepository.save(new User("System Administrator", "admin@liftcare.com", "+10000000001", UserRole.ADMINISTRATOR, encodedPassword));
        User manager = userRepository.save(new User("Alice Building Manager", "manager@liftcare.com", "+10000000002", UserRole.BUILDING_MANAGER, encodedPassword));
        User tech = userRepository.save(new User("Bob Technician", "tech@liftcare.com", "+10000000003", UserRole.TECHNICIAN, encodedPassword));
        User resident = userRepository.save(new User("Charlie Resident", "resident@liftcare.com", "+10000000004", UserRole.RESIDENT, encodedPassword));
        User security = userRepository.save(new User("David Security", "security@liftcare.com", "+10000000005", UserRole.SECURITY_STAFF, encodedPassword));
        User supervisor = userRepository.save(new User("Elena Supervisor", "supervisor@liftcare.com", "+10000000006", UserRole.COMPANY_SUPERVISOR, encodedPassword));

        // 2. Issue Categories
        IssueCategory entrapment = issueCategoryRepository.save(new IssueCategory(
                "ENTRAPMENT", "Passenger Entrapment", "Passenger trapped inside elevator car", SeverityLevel.CRITICAL, true
        ));
        IssueCategory power = issueCategoryRepository.save(new IssueCategory(
                "POWER_FAILURE", "Power Failure / Stopped Shaft", "Elevator halted between floors due to outage", SeverityLevel.HIGH, true
        ));
        IssueCategory door = issueCategoryRepository.save(new IssueCategory(
                "DOOR_MALFUNCTION", "Door Mechanism Malfunction", "Elevator doors fail to close or open smoothly", SeverityLevel.MEDIUM, false
        ));
        IssueCategory noise = issueCategoryRepository.save(new IssueCategory(
                "UNUSUAL_NOISE", "Unusual Noise or Vibration", "Squeaking or motor grinding during transit", SeverityLevel.LOW, false
        ));
        IssueCategory button = issueCategoryRepository.save(new IssueCategory(
                "BUTTON_PANEL", "Floor Call Button Unresponsive", "Call button on floor 4 not lighting up", SeverityLevel.LOW, false
        ));

        // 3. Buildings & Elevators
        Building bldA = buildingRepository.save(new Building("BLD-ALPHA", "Horizon Tower Alpha", "100 Grand Avenue", "Metro City", "building-local-node-01"));
        Building bldB = buildingRepository.save(new Building("BLD-BETA", "Horizon Tower Beta", "102 Grand Avenue", "Metro City", "building-local-node-01"));

        Elevator lift1 = elevatorRepository.save(new Elevator("LIFT-01", bldA, "North Lobby - Passenger Car 1", ElevatorOperationalStatus.ACTIVE));
        Elevator lift2 = elevatorRepository.save(new Elevator("LIFT-02", bldA, "South Lobby - Freight Car 2", ElevatorOperationalStatus.ACTIVE));
        Elevator lift3 = elevatorRepository.save(new Elevator("LIFT-03", bldB, "Central Core - Passenger Car 1", ElevatorOperationalStatus.ACTIVE));

        // 4. Default SLA Policies — seeded by V7 Flyway migration; look up existing ones only.
        // DO NOT insert here: V7 migration already inserts the global defaults,
        // and inserting again would create duplicates causing NonUniqueResultException.
        SlaPolicy slaCritical = slaPolicyRepository.findBySeverityAndBuildingIsNullAndIssueCategoryIsNullAndActiveTrue(SeverityLevel.CRITICAL)
                .stream().findFirst().orElse(null);
        SlaPolicy slaHigh = slaPolicyRepository.findBySeverityAndBuildingIsNullAndIssueCategoryIsNullAndActiveTrue(SeverityLevel.HIGH)
                .stream().findFirst().orElse(null);
        SlaPolicy slaMedium = slaPolicyRepository.findBySeverityAndBuildingIsNullAndIssueCategoryIsNullAndActiveTrue(SeverityLevel.MEDIUM)
                .stream().findFirst().orElse(null);
        SlaPolicy slaLow = slaPolicyRepository.findBySeverityAndBuildingIsNullAndIssueCategoryIsNullAndActiveTrue(SeverityLevel.LOW)
                .stream().findFirst().orElse(null);

        // 5. Initial Sample Incidents for Control Room Dashboard
        Incident inc1 = new Incident("INC-20261031-0001", bldA, lift1, resident, door, "Elevator door sticks on 5th floor landing", SeverityLevel.MEDIUM, false);
        inc1.setStatus(IncidentStatus.ACKNOWLEDGED);
        inc1.setAssignedTo(tech);
        inc1.setSlaPolicy(slaMedium);
        inc1.setSlaResponseTargetMinutes(60);
        inc1.setSlaResolutionTargetMinutes(480);
        inc1.setSlaWarningThresholdMinutes(30);
        inc1.setResponseDeadline(Instant.now().plus(45, ChronoUnit.MINUTES));
        inc1.setResolutionDeadline(Instant.now().plus(400, ChronoUnit.MINUTES));
        inc1.setAcknowledgedAt(Instant.now().minus(10, ChronoUnit.MINUTES));
        incidentRepository.save(inc1);

        Incident inc2 = new Incident("INC-20261031-0002", bldA, lift2, security, entrapment, "2 passengers stuck between floors 3 and 4", SeverityLevel.CRITICAL, true);
        inc2.setStatus(IncidentStatus.IN_PROGRESS);
        inc2.setAssignedTo(tech);
        inc2.setSlaPolicy(slaCritical);
        inc2.setSlaResponseTargetMinutes(15);
        inc2.setSlaResolutionTargetMinutes(120);
        inc2.setSlaWarningThresholdMinutes(10);
        inc2.setResponseDeadline(Instant.now().plus(10, ChronoUnit.MINUTES));
        inc2.setResolutionDeadline(Instant.now().plus(90, ChronoUnit.MINUTES));
        inc2.setAcknowledgedAt(Instant.now().minus(5, ChronoUnit.MINUTES));
        incidentRepository.save(inc2);

        Incident inc3 = new Incident("INC-20261031-0003", bldB, lift3, manager, noise, "Vibration detected at top speed between floors 8-12", SeverityLevel.LOW, false);
        inc3.setStatus(IncidentStatus.REPORTED);
        inc3.setSlaPolicy(slaLow);
        inc3.setSlaResponseTargetMinutes(120);
        inc3.setSlaResolutionTargetMinutes(1440);
        inc3.setSlaWarningThresholdMinutes(60);
        inc3.setResponseDeadline(Instant.now().plus(110, ChronoUnit.MINUTES));
        inc3.setResolutionDeadline(Instant.now().plus(1400, ChronoUnit.MINUTES));
        incidentRepository.save(inc3);

        log.info("LiftCare local demo data successfully seeded! Ready for testing.");
    }

    /**
     * Deduplicates global SLA policies (building IS NULL, issueCategory IS NULL).
     * Multiple seeding paths (Flyway V7, DataInitializer, SlaPolicyService.@PostConstruct)
     * could each insert 4 global policies if they ran in the wrong order on the first boot.
     * This method keeps the lowest-ID active policy per severity and deactivates the rest.
     * Safe to run on every startup — idempotent when no duplicates exist.
     */
    private void deduplicateGlobalSlaPolicies() {
        for (SeverityLevel severity : SeverityLevel.values()) {
            java.util.List<SlaPolicy> globals = slaPolicyRepository
                    .findBySeverityAndBuildingIsNullAndIssueCategoryIsNullAndActiveTrue(severity);
            if (globals.size() > 1) {
                // Sort by ID ascending so we keep the earliest inserted policy
                globals.sort(java.util.Comparator.comparingLong(SlaPolicy::getId));
                log.warn("Found {} duplicate active global SLA policies for severity={}. " +
                         "Keeping id={}, deactivating the rest.", globals.size(), severity, globals.get(0).getId());
                for (int i = 1; i < globals.size(); i++) {
                    globals.get(i).setActive(false);
                    slaPolicyRepository.save(globals.get(i));
                }
            }
        }
    }
}
