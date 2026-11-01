package com.liftcare;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liftcare.dto.IncidentAssignRequest;
import com.liftcare.dto.IncidentCreateRequest;
import com.liftcare.dto.IncidentNoteRequest;
import com.liftcare.dto.IncidentStatusUpdateRequest;
import com.liftcare.model.*;
import com.liftcare.repository.*;
import com.liftcare.security.CustomUserDetails;
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
public class IncidentHistoryTest {

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

    private User residentAlice;
    private User residentBob;
    private User technician1;
    private User technician2;
    private User securityGuard;
    private User manager;
    private User admin;

    private String aliceToken;
    private String bobToken;
    private String tech1Token;
    private String tech2Token;
    private String securityToken;
    private String managerToken;
    private String adminToken;

    private Building buildingA;
    private Elevator elevatorA1;
    private IssueCategory entrapmentCategory;
    private IssueCategory mechanicalCategory;

    @BeforeEach
    void setUp() {
        incidentHistoryRepository.deleteAll();
        incidentRepository.deleteAll();
        elevatorRepository.deleteAll();
        buildingRepository.deleteAll();
        issueCategoryRepository.deleteAll();
        userRepository.deleteAll();

        residentAlice = createUser("alice@liftcare.local", UserRole.RESIDENT, "Alice Resident");
        residentBob = createUser("bob@liftcare.local", UserRole.RESIDENT, "Bob Resident");
        technician1 = createUser("carlos@liftcare.local", UserRole.TECHNICIAN, "Carlos Tech");
        technician2 = createUser("dan@liftcare.local", UserRole.TECHNICIAN, "Dan Tech");
        securityGuard = createUser("eve@liftcare.local", UserRole.SECURITY_STAFF, "Eve Security");
        manager = createUser("frank@liftcare.local", UserRole.BUILDING_MANAGER, "Frank Manager");
        admin = createUser("grace@liftcare.local", UserRole.ADMINISTRATOR, "Grace Admin");

        aliceToken = generateToken(residentAlice);
        bobToken = generateToken(residentBob);
        tech1Token = generateToken(technician1);
        tech2Token = generateToken(technician2);
        securityToken = generateToken(securityGuard);
        managerToken = generateToken(manager);
        adminToken = generateToken(admin);

        buildingA = buildingRepository.save(new Building("BLD-A", "Tower Alpha", "100 Innovation Way", "Metro", "node-01"));
        elevatorA1 = elevatorRepository.save(new Elevator("LIFT-01", buildingA, "East Shaft", ElevatorOperationalStatus.ACTIVE));

        entrapmentCategory = issueCategoryRepository.save(new IssueCategory(
                "ENTRAPMENT", "Passenger Entrapment", "Trapped passengers", SeverityLevel.CRITICAL, true
        ));
        mechanicalCategory = issueCategoryRepository.save(new IssueCategory(
                "MECHANICAL", "Mechanical Fault", "Motor vibration", SeverityLevel.MEDIUM, false
        ));
    }

    private User createUser(String email, UserRole role, String name) {
        User u = new User(name, email, "+1-555-0100", role, passwordEncoder.encode("Password123!"));
        return userRepository.save(u);
    }

    private String generateToken(User user) {
        return jwtService.generateToken(user);
    }

    @Test
    @DisplayName("1. Incident creation produces history entry with ActorType.USER and server-generated timestamp")
    void testIncidentCreationProducesHistoryWithActorTypeUser() throws Exception {
        IncidentCreateRequest req = new IncidentCreateRequest(
                buildingA.getId(),
                elevatorA1.getId(),
                mechanicalCategory.getId(),
                "Strange hum heard in cabin",
                SeverityLevel.MEDIUM,
                false
        );

        mockMvc.perform(post("/api/incidents")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        List<IncidentHistory> history = incidentHistoryRepository.findAll();
        assertEquals(1, history.size());
        IncidentHistory h = history.get(0);
        assertEquals("INCIDENT_REPORTED", h.getAction());
        assertNull(h.getPreviousValue());
        assertEquals("REPORTED", h.getNewValue());
        assertEquals(residentAlice.getId(), h.getChangedBy().getId());
        assertEquals(ActorType.USER, h.getActorType());
        assertNotNull(h.getChangedAt());
        assertTrue(h.getRemarks().contains("Incident report submitted"));
    }

    @Test
    @DisplayName("2. Full status transition lifecycle records previous and new values, timestamps, and actors")
    void testStatusTransitionsRecordPreviousAndNewValues() throws Exception {
        // 1. Create incident
        Incident incident = incidentRepository.save(new Incident(
                "INC-HIST-001", buildingA, elevatorA1, residentAlice, mechanicalCategory, "Test", SeverityLevel.MEDIUM, false
        ));
        incidentHistoryRepository.save(new IncidentHistory(
                incident, "INCIDENT_REPORTED", null, "REPORTED", residentAlice, ActorType.USER, "Reported"
        ));

        // 2. Acknowledge
        mockMvc.perform(patch("/api/incidents/" + incident.getId() + "/acknowledge?remarks=Desk verified")
                        .header("Authorization", "Bearer " + securityToken))
                .andExpect(status().isOk());

        // 3. Assign
        IncidentAssignRequest assignReq = new IncidentAssignRequest(technician1.getId(), "Dispatched to site");
        mockMvc.perform(patch("/api/incidents/" + incident.getId() + "/assign")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isOk());

        // 4. In Progress
        IncidentStatusUpdateRequest progressReq = new IncidentStatusUpdateRequest(IncidentStatus.IN_PROGRESS, "Diagnosing motor");
        mockMvc.perform(patch("/api/incidents/" + incident.getId() + "/status")
                        .header("Authorization", "Bearer " + tech1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(progressReq)))
                .andExpect(status().isOk());

        // 5. Resolved
        IncidentStatusUpdateRequest resolveReq = new IncidentStatusUpdateRequest(IncidentStatus.RESOLVED, "Replaced drive belt");
        mockMvc.perform(patch("/api/incidents/" + incident.getId() + "/status")
                        .header("Authorization", "Bearer " + tech1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resolveReq)))
                .andExpect(status().isOk());

        // 6. Closed
        IncidentStatusUpdateRequest closeReq = new IncidentStatusUpdateRequest(IncidentStatus.CLOSED, "Verified operations");
        mockMvc.perform(patch("/api/incidents/" + incident.getId() + "/status")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(closeReq)))
                .andExpect(status().isOk());

        List<IncidentHistory> history = incidentHistoryRepository.findByIncidentIdOrderByChangedAtAsc(incident.getId());
        assertEquals(6, history.size());

        // Verify actions
        assertEquals("INCIDENT_REPORTED", history.get(0).getAction());
        assertEquals("ACKNOWLEDGED", history.get(1).getAction());
        assertEquals("REPORTED", history.get(1).getPreviousValue());
        assertEquals("ACKNOWLEDGED", history.get(1).getNewValue());
        assertEquals(securityGuard.getId(), history.get(1).getChangedBy().getId());

        assertEquals("ASSIGNED", history.get(2).getAction());
        assertEquals("UNASSIGNED", history.get(2).getPreviousValue());
        assertTrue(history.get(2).getNewValue().contains("Carlos Tech"));
        assertEquals(manager.getId(), history.get(2).getChangedBy().getId());

        assertEquals("STATUS_CHANGED", history.get(3).getAction());
        assertEquals("ASSIGNED", history.get(3).getPreviousValue());
        assertEquals("IN_PROGRESS", history.get(3).getNewValue());
        assertEquals(technician1.getId(), history.get(3).getChangedBy().getId());

        assertEquals("RESOLVED", history.get(4).getAction());
        assertEquals("IN_PROGRESS", history.get(4).getPreviousValue());
        assertEquals("RESOLVED", history.get(4).getNewValue());
        assertEquals("Replaced drive belt", history.get(4).getRemarks());

        assertEquals("CLOSED", history.get(5).getAction());
        assertEquals("RESOLVED", history.get(5).getPreviousValue());
        assertEquals("CLOSED", history.get(5).getNewValue());
        assertEquals(manager.getId(), history.get(5).getChangedBy().getId());
    }

    @Test
    @DisplayName("3. Technician reassignment records TECHNICIAN_REASSIGNED with previous and new technician values")
    void testTechnicianAssignmentAndReassignmentAuditTrail() throws Exception {
        Incident incident = incidentRepository.save(new Incident(
                "INC-HIST-002", buildingA, elevatorA1, residentAlice, mechanicalCategory, "Test", SeverityLevel.MEDIUM, false
        ));
        incidentHistoryRepository.save(new IncidentHistory(
                incident, "INCIDENT_REPORTED", null, "REPORTED", residentAlice, ActorType.USER, "Reported"
        ));

        // Initial assignment
        IncidentAssignRequest assignReq1 = new IncidentAssignRequest(technician1.getId(), "First dispatch");
        mockMvc.perform(patch("/api/incidents/" + incident.getId() + "/assign")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignReq1)))
                .andExpect(status().isOk());

        // Reassignment to Dan Tech
        IncidentAssignRequest assignReq2 = new IncidentAssignRequest(technician2.getId(), "Reassigned: Carlos Tech called to emergency");
        mockMvc.perform(patch("/api/incidents/" + incident.getId() + "/assign")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignReq2)))
                .andExpect(status().isOk());

        List<IncidentHistory> history = incidentHistoryRepository.findByIncidentIdOrderByChangedAtAsc(incident.getId());
        assertEquals(3, history.size());

        IncidentHistory reassignEntry = history.get(2);
        assertEquals("TECHNICIAN_REASSIGNED", reassignEntry.getAction());
        assertTrue(reassignEntry.getPreviousValue().contains("Carlos Tech"));
        assertTrue(reassignEntry.getNewValue().contains("Dan Tech"));
        assertEquals("Reassigned: Carlos Tech called to emergency", reassignEntry.getRemarks());
        assertEquals(manager.getId(), reassignEntry.getChangedBy().getId());
    }

    @Test
    @DisplayName("4. Elevator safety shutdown and operational status transitions produce audit records")
    void testElevatorSafetyShutdownAndOperationalStatusAudit() throws Exception {
        // Entrapment triggers automatic ELEVATOR_OUT_OF_SERVICE
        IncidentCreateRequest req = new IncidentCreateRequest(
                buildingA.getId(),
                elevatorA1.getId(),
                entrapmentCategory.getId(),
                "People trapped between floors 4 and 5",
                SeverityLevel.CRITICAL,
                true
        );

        mockMvc.perform(post("/api/incidents")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        List<IncidentHistory> history = incidentHistoryRepository.findAll();
        assertEquals(2, history.size());
        IncidentHistory shutdownEntry = history.stream()
                .filter(h -> "ELEVATOR_OUT_OF_SERVICE".equals(h.getAction()))
                .findFirst()
                .orElseThrow();

        assertEquals("ACTIVE", shutdownEntry.getPreviousValue());
        assertEquals("OUT_OF_SERVICE", shutdownEntry.getNewValue());
        assertTrue(shutdownEntry.getRemarks().contains("OUT_OF_SERVICE due to safety incident"));

        // Now resolve incident and restore elevator to ACTIVE
        Incident savedIncident = shutdownEntry.getIncident();
        IncidentStatusUpdateRequest resolveReq = new IncidentStatusUpdateRequest(
                IncidentStatus.RESOLVED, "People freed and brake released", ElevatorOperationalStatus.ACTIVE
        );

        mockMvc.perform(patch("/api/incidents/" + savedIncident.getId() + "/status")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resolveReq)))
                .andExpect(status().isOk());

        List<IncidentHistory> updatedHistory = incidentHistoryRepository.findByIncidentIdOrderByChangedAtAsc(savedIncident.getId());
        assertEquals(4, updatedHistory.size());

        IncidentHistory elevatorStatusChange = updatedHistory.stream()
                .filter(h -> "ELEVATOR_STATUS_CHANGED".equals(h.getAction()))
                .findFirst()
                .orElseThrow();

        assertEquals("OUT_OF_SERVICE", elevatorStatusChange.getPreviousValue());
        assertEquals("ACTIVE", elevatorStatusChange.getNewValue());
        assertEquals(manager.getId(), elevatorStatusChange.getChangedBy().getId());
    }

    @Test
    @DisplayName("5. Adding work notes and administrative notes records audit trail")
    void testAddingWorkNotesAndAdministrativeNotes() throws Exception {
        Incident incident = incidentRepository.save(new Incident(
                "INC-HIST-003", buildingA, elevatorA1, residentAlice, mechanicalCategory, "Test", SeverityLevel.MEDIUM, false
        ));
        incidentHistoryRepository.save(new IncidentHistory(
                incident, "INCIDENT_REPORTED", null, "REPORTED", residentAlice, ActorType.USER, "Reported"
        ));

        // 1. Technician adds work note
        IncidentNoteRequest workNote = new IncidentNoteRequest("Measured cable tension: within 2% tolerance.", "WORK_LOG");
        mockMvc.perform(post("/api/incidents/" + incident.getId() + "/notes")
                        .header("Authorization", "Bearer " + tech1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(workNote)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.action", is("WORK_NOTE_ADDED")))
                .andExpect(jsonPath("$.newValue", is("Measured cable tension: within 2% tolerance.")))
                .andExpect(jsonPath("$.changedByName", is("Carlos Tech")));

        // 2. Administrator adds administrative note
        IncidentNoteRequest adminNote = new IncidentNoteRequest("Escalation waived per building supervisor instruction.", "ADMINISTRATIVE");
        mockMvc.perform(post("/api/incidents/" + incident.getId() + "/notes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminNote)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.action", is("ADMINISTRATIVE_ACTION")))
                .andExpect(jsonPath("$.changedByName", is("Grace Admin")));

        List<IncidentHistory> history = incidentHistoryRepository.findByIncidentIdOrderByChangedAtAsc(incident.getId());
        assertEquals(3, history.size());
    }

    @Test
    @DisplayName("6. Dedicated history endpoint GET /api/incidents/{id}/history supports pagination and ordering")
    void testDedicatedHistoryEndpointWithPaginationAndOrdering() throws Exception {
        Incident incident = incidentRepository.save(new Incident(
                "INC-HIST-004", buildingA, elevatorA1, residentAlice, mechanicalCategory, "Test", SeverityLevel.MEDIUM, false
        ));

        for (int i = 1; i <= 5; i++) {
            IncidentHistory h = new IncidentHistory(
                    incident, "STEP_" + i, "PREV_" + i, "NEW_" + i, manager, ActorType.USER, "Step " + i
            );
            incidentHistoryRepository.save(h);
            Thread.sleep(10); // Guarantee distinct timestamps
        }

        // Test ASC (Oldest first) with page size 2
        mockMvc.perform(get("/api/incidents/" + incident.getId() + "/history?page=0&size=2&sort=ASC")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].action", is("STEP_1")))
                .andExpect(jsonPath("$.content[1].action", is("STEP_2")))
                .andExpect(jsonPath("$.totalElements", is(5)))
                .andExpect(jsonPath("$.totalPages", is(3)))
                .andExpect(jsonPath("$.hasNext", is(true)))
                .andExpect(jsonPath("$.hasPrevious", is(false)));

        // Test DESC (Newest first)
        mockMvc.perform(get("/api/incidents/" + incident.getId() + "/history?page=0&size=2&sort=DESC")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].action", is("STEP_5")))
                .andExpect(jsonPath("$.content[1].action", is("STEP_4")));
    }

    @Test
    @DisplayName("7. Dedicated history endpoint action filtering returns only matching event types")
    void testDedicatedHistoryEndpointActionFiltering() throws Exception {
        Incident incident = incidentRepository.save(new Incident(
                "INC-HIST-005", buildingA, elevatorA1, residentAlice, mechanicalCategory, "Test", SeverityLevel.MEDIUM, false
        ));
        incidentHistoryRepository.save(new IncidentHistory(
                incident, "INCIDENT_REPORTED", null, "REPORTED", residentAlice, ActorType.USER, "Reported"
        ));
        incidentHistoryRepository.save(new IncidentHistory(
                incident, "ACKNOWLEDGED", "REPORTED", "ACKNOWLEDGED", securityGuard, ActorType.USER, "Ack"
        ));
        incidentHistoryRepository.save(new IncidentHistory(
                incident, "RESOLVED", "ACKNOWLEDGED", "RESOLVED", technician1, ActorType.USER, "Resolved"
        ));

        mockMvc.perform(get("/api/incidents/" + incident.getId() + "/history?action=RESOLVED")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].action", is("RESOLVED")))
                .andExpect(jsonPath("$.content[0].changedByName", is("Carlos Tech")));
    }

    @Test
    @DisplayName("8. Resident cannot view another resident's incident history (403 Forbidden)")
    void testResidentCannotViewAnotherResidentsHistory() throws Exception {
        Incident aliceIncident = incidentRepository.save(new Incident(
                "INC-HIST-006", buildingA, elevatorA1, residentAlice, mechanicalCategory, "Alice Test", SeverityLevel.MEDIUM, false
        ));
        incidentHistoryRepository.save(new IncidentHistory(
                aliceIncident, "INCIDENT_REPORTED", null, "REPORTED", residentAlice, ActorType.USER, "Reported"
        ));

        // Alice can view her own history
        mockMvc.perform(get("/api/incidents/" + aliceIncident.getId() + "/history")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));

        // Bob cannot view Alice's history
        mockMvc.perform(get("/api/incidents/" + aliceIncident.getId() + "/history")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("9. Operational staff roles can view authorized incident history")
    void testAuthorizedStaffCanAccessHistory() throws Exception {
        Incident incident = incidentRepository.save(new Incident(
                "INC-HIST-007", buildingA, elevatorA1, residentAlice, mechanicalCategory, "Test", SeverityLevel.MEDIUM, false
        ));
        incidentHistoryRepository.save(new IncidentHistory(
                incident, "INCIDENT_REPORTED", null, "REPORTED", residentAlice, ActorType.USER, "Reported"
        ));

        // Security Staff
        mockMvc.perform(get("/api/incidents/" + incident.getId() + "/history")
                        .header("Authorization", "Bearer " + securityToken))
                .andExpect(status().isOk());

        // Technician
        mockMvc.perform(get("/api/incidents/" + incident.getId() + "/history")
                        .header("Authorization", "Bearer " + tech1Token))
                .andExpect(status().isOk());

        // Building Manager
        mockMvc.perform(get("/api/incidents/" + incident.getId() + "/history")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk());

        // Administrator
        mockMvc.perform(get("/api/incidents/" + incident.getId() + "/history")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("10. History entries cannot be modified or deleted through application APIs")
    void testHistoryEntriesCannotBeModifiedOrDeletedViaApi() throws Exception {
        Incident incident = incidentRepository.save(new Incident(
                "INC-HIST-008", buildingA, elevatorA1, residentAlice, mechanicalCategory, "Test", SeverityLevel.MEDIUM, false
        ));

        // Attempting to PUT to history returns 405 Method Not Allowed
        mockMvc.perform(put("/api/incidents/" + incident.getId() + "/history")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"FORGED\"}"))
                .andExpect(status().isMethodNotAllowed());

        // Attempting to DELETE history returns 405 Method Not Allowed
        mockMvc.perform(delete("/api/incidents/" + incident.getId() + "/history")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("11. Actor identity is derived strictly from authentication context rather than client payload")
    void testActorDerivedFromSecurityContextNotClientPayload() throws Exception {
        Incident incident = incidentRepository.save(new Incident(
                "INC-HIST-009", buildingA, elevatorA1, residentAlice, mechanicalCategory, "Test", SeverityLevel.MEDIUM, false
        ));

        IncidentNoteRequest note = new IncidentNoteRequest("Test note", "WORK_LOG");

        // Technician1 posts a note
        mockMvc.perform(post("/api/incidents/" + incident.getId() + "/notes")
                        .header("Authorization", "Bearer " + tech1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(note)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.changedById", is(technician1.getId().intValue())))
                .andExpect(jsonPath("$.changedByName", is("Carlos Tech")))
                .andExpect(jsonPath("$.actorType", is("USER")));
    }

    @Test
    @DisplayName("12. Failed transactions rollback completely without leaving phantom history entries")
    void testFailedTransactionRollbackPreservesHistoryIntegrity() throws Exception {
        Incident incident = incidentRepository.save(new Incident(
                "INC-HIST-010", buildingA, elevatorA1, residentAlice, mechanicalCategory, "Test", SeverityLevel.MEDIUM, false
        ));
        incidentHistoryRepository.save(new IncidentHistory(
                incident, "INCIDENT_REPORTED", null, "REPORTED", residentAlice, ActorType.USER, "Reported"
        ));

        // Attempt invalid status transition (transitioning to REPORTED is forbidden)
        IncidentStatusUpdateRequest invalidReq = new IncidentStatusUpdateRequest(IncidentStatus.REPORTED, "Invalid back-transition");
        mockMvc.perform(patch("/api/incidents/" + incident.getId() + "/status")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest());

        // Verify only 1 history entry remains (no phantom entry created)
        List<IncidentHistory> history = incidentHistoryRepository.findByIncidentIdOrderByChangedAtAsc(incident.getId());
        assertEquals(1, history.size());
        assertEquals("INCIDENT_REPORTED", history.get(0).getAction());
    }
}
