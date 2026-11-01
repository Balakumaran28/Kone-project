package com.liftcare.repository;

import com.liftcare.model.*;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class EntityRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private ElevatorRepository elevatorRepository;

    @Autowired
    private IssueCategoryRepository issueCategoryRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private IncidentHistoryRepository incidentHistoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User residentUser;
    private User technicianUser;
    private Building buildingApex;
    private Elevator elevatorA1;
    private IssueCategory entrapmentCategory;

    @BeforeEach
    void setUp() {
        // Create baseline entities for tests
        residentUser = userRepository.save(new User(
                "Jane Doe",
                "jane.doe@example.com",
                "+1-555-0100",
                UserRole.RESIDENT
        ));

        technicianUser = userRepository.save(new User(
                "Alex Rivera",
                "alex.tech@example.com",
                "+1-555-0199",
                UserRole.TECHNICIAN
        ));

        buildingApex = buildingRepository.save(new Building(
                "BLD-APEX",
                "Apex Sky Tower",
                "100 Metropolis Boulevard",
                "Metropolis",
                "apex-node-01"
        ));

        elevatorA1 = elevatorRepository.save(new Elevator(
                "LIFT-01",
                buildingApex,
                "North Lobby Passenger Shaft",
                ElevatorOperationalStatus.ACTIVE
        ));

        entrapmentCategory = issueCategoryRepository.save(new IssueCategory(
                "PASSENGER_ENTRAPMENT",
                "Passenger entrapment",
                "Passenger stuck in elevator cab",
                SeverityLevel.CRITICAL,
                true
        ));
    }

    @Test
    @DisplayName("1. Persist and retrieve User with string enum role")
    void testUserPersistenceAndStringEnum() {
        User user = new User("Marcus Vance", "marcus@example.com", "+1-555-0122", UserRole.BUILDING_MANAGER);
        User saved = userRepository.save(user);

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        assertEquals(UserRole.BUILDING_MANAGER, saved.getRole());

        // Verify stored in DB as varchar string, not ordinal number
        String storedRole = jdbcTemplate.queryForObject(
                "SELECT role FROM users WHERE id = ?",
                String.class,
                saved.getId()
        );
        assertEquals("BUILDING_MANAGER", storedRole);

        Optional<User> found = userRepository.findByEmail("marcus@example.com");
        assertTrue(found.isPresent());
        assertEquals("Marcus Vance", found.get().getFullName());
    }

    @Test
    @DisplayName("2. Persist and retrieve Building with finder methods")
    void testBuildingPersistenceAndFinders() {
        assertTrue(buildingRepository.existsByBuildingCode("BLD-APEX"));

        Optional<Building> byCode = buildingRepository.findByBuildingCode("BLD-APEX");
        assertTrue(byCode.isPresent());
        assertEquals("Apex Sky Tower", byCode.get().getName());

        Optional<Building> byNode = buildingRepository.findByLocalNodeId("apex-node-01");
        assertTrue(byNode.isPresent());
        assertEquals("BLD-APEX", byNode.get().getBuildingCode());
    }

    @Test
    @DisplayName("3. Persist Elevator and verify building-scoped uniqueness")
    void testElevatorPersistenceAndScopedUniqueness() {
        Elevator found = elevatorRepository.findByBuildingAndElevatorCode(buildingApex, "LIFT-01")
                .orElse(null);
        assertNotNull(found);
        assertEquals("North Lobby Passenger Shaft", found.getLocationDescription());
        assertEquals(ElevatorOperationalStatus.ACTIVE, found.getOperationalStatus());

        // Duplicate elevator code in SAME building must fail unique constraint
        Elevator duplicate = new Elevator("LIFT-01", buildingApex, "Duplicate shaft", ElevatorOperationalStatus.ACTIVE);
        assertThrows(DataIntegrityViolationException.class, () -> {
            elevatorRepository.saveAndFlush(duplicate);
        });
    }

    @Test
    @DisplayName("4. Same elevator code allowed in DIFFERENT buildings")
    void testElevatorCodeAllowedInDifferentBuildings() {
        Building buildingBeacon = buildingRepository.save(new Building(
                "BLD-BEACON",
                "Beacon Plaza",
                "200 Market Street",
                "Metropolis",
                "beacon-node-02"
        ));

        // Same code 'LIFT-01' should succeed because building is different
        Elevator beaconElevator = new Elevator("LIFT-01", buildingBeacon, "Main Shaft", ElevatorOperationalStatus.ACTIVE);
        Elevator saved = elevatorRepository.saveAndFlush(beaconElevator);

        assertNotNull(saved.getId());
        assertEquals("LIFT-01", saved.getElevatorCode());
        assertEquals("BLD-BEACON", saved.getBuilding().getBuildingCode());
    }

    @Test
    @DisplayName("5. Persist Incident with all relationships and verify integrity")
    void testIncidentPersistenceAndRelationships() {
        Incident incident = new Incident(
                "INC-20261030-001",
                buildingApex,
                elevatorA1,
                residentUser,
                entrapmentCategory,
                "Elevator stalled between floors 14 and 15 with 2 passengers inside.",
                SeverityLevel.CRITICAL,
                true
        );

        Incident saved = incidentRepository.save(incident);
        assertNotNull(saved.getId());
        assertNotNull(saved.getReportedAt());
        assertEquals(IncidentStatus.REPORTED, saved.getStatus());
        assertTrue(saved.isEntrapmentReported());

        // Verify relationships navigation
        Incident loaded = incidentRepository.findByTicketNumber("INC-20261030-001").orElseThrow();
        assertEquals("Apex Sky Tower", loaded.getBuilding().getName());
        assertEquals("LIFT-01", loaded.getElevator().getElevatorCode());
        assertEquals("Jane Doe", loaded.getReportedBy().getFullName());
        assertEquals("PASSENGER_ENTRAPMENT", loaded.getIssueCategory().getCode());

        // Verify query by building and status
        List<Incident> openIncidents = incidentRepository.findByBuildingIdAndStatus(buildingApex.getId(), IncidentStatus.REPORTED);
        assertFalse(openIncidents.isEmpty());
        assertEquals("INC-20261030-001", openIncidents.get(0).getTicketNumber());

        // Verify duplicate ticket number rejection
        Incident duplicateTicket = new Incident(
                "INC-20261030-001",
                buildingApex,
                elevatorA1,
                residentUser,
                entrapmentCategory,
                "Another report with identical ticket number",
                SeverityLevel.HIGH,
                false
        );
        assertThrows(DataIntegrityViolationException.class, () -> {
            incidentRepository.saveAndFlush(duplicateTicket);
        });
    }

    @Test
    @DisplayName("6. Persist IncidentHistory audit trail and chronological ordering")
    void testIncidentHistoryAuditTrail() {
        Incident incident = incidentRepository.save(new Incident(
                "INC-20261030-002",
                buildingApex,
                elevatorA1,
                residentUser,
                entrapmentCategory,
                "Doors jammed open on floor 3",
                SeverityLevel.HIGH,
                false
        ));

        IncidentHistory step1 = incidentHistoryRepository.save(new IncidentHistory(
                incident,
                "INCIDENT_REPORTED",
                null,
                "REPORTED",
                residentUser,
                "Initial fault report received via building intranet"
        ));

        IncidentHistory step2 = incidentHistoryRepository.save(new IncidentHistory(
                incident,
                "TECHNICIAN_ASSIGNED",
                "UNASSIGNED",
                "Alex Rivera (ID: " + technicianUser.getId() + ")",
                technicianUser,
                "Assigned to on-site duty technician"
        ));

        List<IncidentHistory> history = incidentHistoryRepository.findByIncidentIdOrderByChangedAtAsc(incident.getId());
        assertEquals(2, history.size());
        assertEquals("INCIDENT_REPORTED", history.get(0).getAction());
        assertEquals("TECHNICIAN_ASSIGNED", history.get(1).getAction());
    }

    @Test
    @DisplayName("7. Default Issue Categories can be initialized and queried")
    void testDefaultIssueCategories() {
        IssueCategory doorCategory = issueCategoryRepository.save(new IssueCategory(
                "DOOR_MALFUNCTION",
                "Door malfunction",
                "Door bounce or failure to lock",
                SeverityLevel.HIGH,
                false
        ));

        Optional<IssueCategory> found = issueCategoryRepository.findByCode("DOOR_MALFUNCTION");
        assertTrue(found.isPresent());
        assertEquals(SeverityLevel.HIGH, found.get().getDefaultSeverity());
        assertFalse(found.get().isRequiresImmediateAttention());

        List<IssueCategory> activeCategories = issueCategoryRepository.findByActiveTrue();
        assertTrue(activeCategories.size() >= 2);
    }

    @Test
    @DisplayName("8. Validation constraint rejection on blank/invalid fields")
    void testValidationRejectionOnInvalidEntity() {
        // User with invalid email format
        User invalidUser = new User("Bad User", "not-a-valid-email", null, UserRole.RESIDENT);
        assertThrows(ConstraintViolationException.class, () -> {
            userRepository.saveAndFlush(invalidUser);
        });

        // Building with empty name
        Building invalidBuilding = new Building("BLD-EMPTY", "", "123 Street", "City", "node-01");
        assertThrows(ConstraintViolationException.class, () -> {
            buildingRepository.saveAndFlush(invalidBuilding);
        });
    }
}
