package com.liftcare;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liftcare.dto.IncidentAssignRequest;
import com.liftcare.dto.IncidentCreateRequest;
import com.liftcare.dto.IncidentStatusUpdateRequest;
import com.liftcare.model.*;
import com.liftcare.repository.*;
import com.liftcare.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class IncidentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IncidentHistoryRepository incidentHistoryRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private ElevatorRepository elevatorRepository;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private IssueCategoryRepository issueCategoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private User resident;
    private User otherResident;
    private User technician;
    private User securityStaff;
    private User manager;
    private User admin;

    private String residentToken;
    private String otherResidentToken;
    private String technicianToken;
    private String securityToken;
    private String managerToken;
    private String adminToken;

    private Building buildingA;
    private Building buildingB;
    private Building inactiveBuilding;

    private Elevator elevatorA1;
    private Elevator elevatorA2;
    private Elevator elevatorB1;

    private IssueCategory entrapmentCategory;
    private IssueCategory doorCategory;
    private IssueCategory lightCategory;
    private IssueCategory inactiveCategory;

    @BeforeEach
    void setUp() {
        incidentHistoryRepository.deleteAll();
        incidentRepository.deleteAll();
        elevatorRepository.deleteAll();
        buildingRepository.deleteAll();
        issueCategoryRepository.deleteAll();
        userRepository.deleteAll();

        resident = createUser("alice@liftcare.local", UserRole.RESIDENT, "Alice Resident");
        otherResident = createUser("bob@liftcare.local", UserRole.RESIDENT, "Bob Resident");
        technician = createUser("tech@liftcare.local", UserRole.TECHNICIAN, "Tom Technician");
        securityStaff = createUser("security@liftcare.local", UserRole.SECURITY_STAFF, "Sam Security");
        manager = createUser("manager@liftcare.local", UserRole.BUILDING_MANAGER, "Mary Manager");
        admin = createUser("admin@liftcare.local", UserRole.ADMINISTRATOR, "Adam Admin");

        residentToken = jwtService.generateToken(resident);
        otherResidentToken = jwtService.generateToken(otherResident);
        technicianToken = jwtService.generateToken(technician);
        securityToken = jwtService.generateToken(securityStaff);
        managerToken = jwtService.generateToken(manager);
        adminToken = jwtService.generateToken(admin);

        buildingA = buildingRepository.save(new Building("BLD-A", "Tower Alpha", "100 Alpha Rd", "City", "node-01"));
        buildingB = buildingRepository.save(new Building("BLD-B", "Tower Beta", "200 Beta St", "City", "node-01"));
        inactiveBuilding = new Building("BLD-INACT", "Old Tower", "300 Closed Rd", "City", "node-01");
        inactiveBuilding.setActive(false);
        inactiveBuilding = buildingRepository.save(inactiveBuilding);

        elevatorA1 = elevatorRepository.save(new Elevator("LIFT-01", buildingA, "Lobby Alpha", ElevatorOperationalStatus.ACTIVE));
        elevatorA2 = elevatorRepository.save(new Elevator("LIFT-02", buildingA, "North Shaft", ElevatorOperationalStatus.ACTIVE));
        elevatorB1 = elevatorRepository.save(new Elevator("LIFT-01", buildingB, "Lobby Beta", ElevatorOperationalStatus.ACTIVE));

        entrapmentCategory = issueCategoryRepository.save(new IssueCategory(
                "PASSENGER_ENTRAPMENT", "Passenger entrapment", "Passengers trapped inside", SeverityLevel.CRITICAL, true
        ));
        doorCategory = issueCategoryRepository.save(new IssueCategory(
                "DOOR_MALFUNCTION", "Door malfunction", "Doors jammed", SeverityLevel.HIGH, false
        ));
        lightCategory = issueCategoryRepository.save(new IssueCategory(
                "LIGHT_FAULT", "Cabin light flicker", "Minor lighting issue", SeverityLevel.LOW, false
        ));

        inactiveCategory = new IssueCategory("INACT_CAT", "Inactive issue", "Disabled", SeverityLevel.LOW, false);
        inactiveCategory.setActive(false);
        inactiveCategory = issueCategoryRepository.save(inactiveCategory);
    }

    private User createUser(String email, UserRole role, String name) {
        User user = new User(name, email, "+1-555-0100", role, passwordEncoder.encode("Password123!"));
        return userRepository.save(user);
    }

    @Test
    @DisplayName("1. Successful incident creation by authenticated resident")
    void testSuccessfulIncidentCreationByResident() throws Exception {
        IncidentCreateRequest request = new IncidentCreateRequest(
                buildingA.getId(),
                elevatorA1.getId(),
                doorCategory.getId(),
                "Elevator doors stick on 4th floor",
                SeverityLevel.HIGH,
                false
        );

        mockMvc.perform(post("/api/incidents")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.ticketNumber", startsWith("INC-")))
                .andExpect(jsonPath("$.buildingId", is(buildingA.getId().intValue())))
                .andExpect(jsonPath("$.elevatorId", is(elevatorA1.getId().intValue())))
                .andExpect(jsonPath("$.reportedById", is(resident.getId().intValue())))
                .andExpect(jsonPath("$.reportedByName", is("Alice Resident")))
                .andExpect(jsonPath("$.severity", is("HIGH")))
                .andExpect(jsonPath("$.status", is("REPORTED")))
                .andExpect(jsonPath("$.entrapmentReported", is(false)));

        // Verify history entry was created
        List<IncidentHistory> history = incidentHistoryRepository.findAll();
        assertEquals(1, history.size());
        assertEquals("INCIDENT_REPORTED", history.get(0).getAction());
        assertEquals(resident.getId(), history.get(0).getChangedBy().getId());
    }

    @Test
    @DisplayName("2. Rejection of unauthenticated incident creation (401)")
    void testUnauthenticatedIncidentCreation() throws Exception {
        IncidentCreateRequest request = new IncidentCreateRequest(
                buildingA.getId(), elevatorA1.getId(), doorCategory.getId(), "Doors jammed", null, null
        );

        mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("3. Reporter identity is derived from authentication rather than request input")
    void testReporterIdentityDerivedFromAuth() throws Exception {
        IncidentCreateRequest request = new IncidentCreateRequest(
                buildingA.getId(),
                elevatorA1.getId(),
                doorCategory.getId(),
                "Buttons unresponsive",
                SeverityLevel.MEDIUM,
                false
        );

        // Submitting with residentToken (Alice)
        mockMvc.perform(post("/api/incidents")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportedById", is(resident.getId().intValue())))
                .andExpect(jsonPath("$.reportedByEmail", is("alice@liftcare.local")));
    }

    @Test
    @DisplayName("4. Rejection of a nonexistent building (404)")
    void testNonexistentBuildingRejected() throws Exception {
        IncidentCreateRequest request = new IncidentCreateRequest(
                99999L, elevatorA1.getId(), doorCategory.getId(), "Building does not exist", null, null
        );

        mockMvc.perform(post("/api/incidents")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", containsString("Building not found with id: 99999")));
    }

    @Test
    @DisplayName("4b. Rejection of an inactive building (400)")
    void testInactiveBuildingRejected() throws Exception {
        Elevator inactiveElevator = elevatorRepository.save(
                new Elevator("LIFT-X", inactiveBuilding, "Shaft", ElevatorOperationalStatus.ACTIVE)
        );

        IncidentCreateRequest request = new IncidentCreateRequest(
                inactiveBuilding.getId(), inactiveElevator.getId(), doorCategory.getId(), "Inactive building report", null, null
        );

        mockMvc.perform(post("/api/incidents")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("Cannot report an incident for an inactive building")));
    }

    @Test
    @DisplayName("5. Rejection of a nonexistent elevator (404)")
    void testNonexistentElevatorRejected() throws Exception {
        IncidentCreateRequest request = new IncidentCreateRequest(
                buildingA.getId(), 88888L, doorCategory.getId(), "Elevator does not exist", null, null
        );

        mockMvc.perform(post("/api/incidents")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", containsString("Elevator not found with id: 88888")));
    }

    @Test
    @DisplayName("6. Rejection when elevator does not belong to the selected building (400)")
    void testElevatorBuildingMismatchRejected() throws Exception {
        // elevatorB1 belongs to buildingB, but buildingA is specified
        IncidentCreateRequest request = new IncidentCreateRequest(
                buildingA.getId(),
                elevatorB1.getId(),
                doorCategory.getId(),
                "Elevator and building mismatch",
                null,
                null
        );

        mockMvc.perform(post("/api/incidents")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("does not belong to building")));
    }

    @Test
    @DisplayName("7. Rejection of invalid or missing required fields (400)")
    void testMissingRequiredFields() throws Exception {
        // Missing description and required IDs
        mockMvc.perform(post("/api/incidents")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.validationErrors.description", notNullValue()));
    }

    @Test
    @DisplayName("8. Correct default initial severity from issue category and status REPORTED")
    void testDefaultSeverityAndStatus() throws Exception {
        // Omitting severity should default to issue category's default severity (HIGH for doorCategory)
        IncidentCreateRequest request = new IncidentCreateRequest(
                buildingA.getId(),
                elevatorA1.getId(),
                doorCategory.getId(),
                "Door sticking periodically",
                null,
                false
        );

        mockMvc.perform(post("/api/incidents")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.severity", is("HIGH")))
                .andExpect(jsonPath("$.status", is("REPORTED")));
    }

    @Test
    @DisplayName("9. Entrapment is classified for immediate attention (CRITICAL severity)")
    void testEntrapmentClassifiedCritical() throws Exception {
        IncidentCreateRequest request = new IncidentCreateRequest(
                buildingA.getId(),
                elevatorA1.getId(),
                entrapmentCategory.getId(),
                "Stalled between 3rd and 4th floor with 3 occupants",
                null,
                true
        );

        mockMvc.perform(post("/api/incidents")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.severity", is("CRITICAL")))
                .andExpect(jsonPath("$.entrapmentReported", is(true)));
    }

    @Test
    @DisplayName("10. Entrapment cannot be silently downgraded to low severity (400)")
    void testEntrapmentCannotBeDowngraded() throws Exception {
        IncidentCreateRequest request = new IncidentCreateRequest(
                buildingA.getId(),
                elevatorA1.getId(),
                entrapmentCategory.getId(),
                "People stuck in lift",
                SeverityLevel.LOW,
                true
        );

        mockMvc.perform(post("/api/incidents")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("Passenger entrapment incidents require CRITICAL severity")));
    }

    @Test
    @DisplayName("11. A qualifying incident triggers elevator operational status change to OUT_OF_SERVICE")
    void testQualifyingIncidentTriggersElevatorOutOfService() throws Exception {
        assertEquals(ElevatorOperationalStatus.ACTIVE, elevatorA1.getOperationalStatus());

        IncidentCreateRequest request = new IncidentCreateRequest(
                buildingA.getId(),
                elevatorA1.getId(),
                entrapmentCategory.getId(),
                "Emergency: car stalled with passengers inside",
                SeverityLevel.CRITICAL,
                true
        );

        mockMvc.perform(post("/api/incidents")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Verify elevator status was automatically transitioned to OUT_OF_SERVICE
        Elevator updatedElevator = elevatorRepository.findById(elevatorA1.getId()).orElseThrow();
        assertEquals(ElevatorOperationalStatus.OUT_OF_SERVICE, updatedElevator.getOperationalStatus());

        // Verify ELEVATOR_OUT_OF_SERVICE history entry exists
        List<IncidentHistory> history = incidentHistoryRepository.findAll();
        assertTrue(history.stream().anyMatch(h -> "ELEVATOR_OUT_OF_SERVICE".equals(h.getAction())));
    }

    @Test
    @DisplayName("12. Non-qualifying incident does not trigger unjustified status change")
    void testNonQualifyingIncidentDoesNotChangeElevatorStatus() throws Exception {
        assertEquals(ElevatorOperationalStatus.ACTIVE, elevatorA2.getOperationalStatus());

        IncidentCreateRequest request = new IncidentCreateRequest(
                buildingA.getId(),
                elevatorA2.getId(),
                lightCategory.getId(),
                "Cabin recessed bulb is flickering",
                SeverityLevel.LOW,
                false
        );

        mockMvc.perform(post("/api/incidents")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Elevator must remain ACTIVE
        Elevator unchanged = elevatorRepository.findById(elevatorA2.getId()).orElseThrow();
        assertEquals(ElevatorOperationalStatus.ACTIVE, unchanged.getOperationalStatus());

        // No ELEVATOR_OUT_OF_SERVICE history
        List<IncidentHistory> history = incidentHistoryRepository.findAll();
        assertFalse(history.stream().anyMatch(h -> "ELEVATOR_OUT_OF_SERVICE".equals(h.getAction())));
    }

    @Test
    @DisplayName("13. Incident triage operations and history audit trail (Acknowledge, Assign, Status Update, Resolve)")
    void testIncidentTriageWorkflowAndAuditTrail() throws Exception {
        // 1. Create incident
        Incident incident = incidentRepository.save(new Incident(
                "INC-TEST-001", buildingA, elevatorA1, resident, doorCategory, "Doors jammed", SeverityLevel.HIGH, false
        ));
        incidentHistoryRepository.save(new IncidentHistory(
                incident, "INCIDENT_REPORTED", null, "REPORTED", resident, "Reported"
        ));

        // 2. Security staff acknowledges incident
        mockMvc.perform(patch("/api/incidents/" + incident.getId() + "/acknowledge?remarks=Control desk verified on CCTV")
                        .header("Authorization", "Bearer " + securityToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACKNOWLEDGED")));

        // 3. Manager assigns incident to technician
        IncidentAssignRequest assignRequest = new IncidentAssignRequest(technician.getId(), "Dispatched to on-site tech");
        mockMvc.perform(patch("/api/incidents/" + incident.getId() + "/assign")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ASSIGNED")))
                .andExpect(jsonPath("$.assignedToId", is(technician.getId().intValue())))
                .andExpect(jsonPath("$.assignedToName", is("Tom Technician")));

        // 4. Technician transitions to IN_PROGRESS
        IncidentStatusUpdateRequest progressRequest = new IncidentStatusUpdateRequest(IncidentStatus.IN_PROGRESS, "Inspecting door interlock");
        mockMvc.perform(patch("/api/incidents/" + incident.getId() + "/status")
                        .header("Authorization", "Bearer " + technicianToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(progressRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("IN_PROGRESS")));

        // 5. Technician resolves incident
        IncidentStatusUpdateRequest resolveRequest = new IncidentStatusUpdateRequest(IncidentStatus.RESOLVED, "Replaced optical door sensor");
        mockMvc.perform(patch("/api/incidents/" + incident.getId() + "/status")
                        .header("Authorization", "Bearer " + technicianToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resolveRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("RESOLVED")))
                .andExpect(jsonPath("$.resolvedAt", notNullValue()));

        // 6. Verify chronological audit trail
        mockMvc.perform(get("/api/incidents/" + incident.getId())
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history", hasSize(5)))
                .andExpect(jsonPath("$.history[0].action", is("INCIDENT_REPORTED")))
                .andExpect(jsonPath("$.history[1].action", is("ACKNOWLEDGED")))
                .andExpect(jsonPath("$.history[2].action", is("ASSIGNED")))
                .andExpect(jsonPath("$.history[3].action", is("STATUS_CHANGED")))
                .andExpect(jsonPath("$.history[4].action", is("RESOLVED")));
    }

    @Test
    @DisplayName("14. Unauthorized users cannot update or assign incidents (403)")
    void testUnauthorizedTriageOperations() throws Exception {
        Incident incident = incidentRepository.save(new Incident(
                "INC-TEST-002", buildingA, elevatorA1, resident, doorCategory, "Door issue", SeverityLevel.HIGH, false
        ));

        // Resident attempting to acknowledge -> 403 Forbidden
        mockMvc.perform(patch("/api/incidents/" + incident.getId() + "/acknowledge")
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isForbidden());

        // Resident attempting to assign -> 403 Forbidden
        IncidentAssignRequest assignRequest = new IncidentAssignRequest(technician.getId(), "Hack assignment");
        mockMvc.perform(patch("/api/incidents/" + incident.getId() + "/assign")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignRequest)))
                .andExpect(status().isForbidden());

        // Resident attempting to resolve -> 403 Forbidden
        IncidentStatusUpdateRequest statusRequest = new IncidentStatusUpdateRequest(IncidentStatus.RESOLVED, "Resident closing own issue");
        mockMvc.perform(patch("/api/incidents/" + incident.getId() + "/status")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("15. Resident cannot view another resident's incident (403 Access Denied)")
    void testResidentScopeEnforcement() throws Exception {
        // Incident reported by Alice
        Incident incident = incidentRepository.save(new Incident(
                "INC-ALICE-01", buildingA, elevatorA1, resident, doorCategory, "Alice's report", SeverityLevel.MEDIUM, false
        ));

        // Alice can view her own incident
        mockMvc.perform(get("/api/incidents/" + incident.getId())
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incident.ticketNumber", is("INC-ALICE-01")));

        // Bob (other resident) is denied access (403 Forbidden)
        mockMvc.perform(get("/api/incidents/" + incident.getId())
                        .header("Authorization", "Bearer " + otherResidentToken))
                .andExpect(status().isForbidden());

        // Manager is allowed to view
        mockMvc.perform(get("/api/incidents/" + incident.getId())
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("15b. List incidents: Resident only sees their own incidents; Manager sees all")
    void testIncidentListScoping() throws Exception {
        // Incident 1 reported by Alice
        incidentRepository.save(new Incident(
                "INC-A1", buildingA, elevatorA1, resident, doorCategory, "Alice issue", SeverityLevel.MEDIUM, false
        ));
        // Incident 2 reported by Bob
        incidentRepository.save(new Incident(
                "INC-B1", buildingA, elevatorA2, otherResident, lightCategory, "Bob issue", SeverityLevel.LOW, false
        ));

        // Alice sees only 1 incident (her own)
        mockMvc.perform(get("/api/incidents")
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].ticketNumber", is("INC-A1")));

        // Manager sees both incidents
        mockMvc.perform(get("/api/incidents")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }
}
