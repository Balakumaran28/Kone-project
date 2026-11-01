package com.liftcare;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liftcare.dto.*;
import com.liftcare.model.*;
import com.liftcare.repository.*;
import com.liftcare.security.JwtService;
import com.liftcare.service.SlaEvaluationService;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Module 11: End-to-End Integration Hardening, Reliability Testing,
 * Security Boundary Verification, and System Regression Suite.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class Module11IntegrationHardeningTest {

    @Autowired
    private MockMvc mockMvc;

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
    private SlaPolicyRepository slaPolicyRepository;

    @Autowired
    private NotificationOutboxRepository notificationOutboxRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private SlaEvaluationService slaEvaluationService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private User residentAlice;
    private User residentBob;
    private User technicianCarlos;
    private User managerMary;
    private User adminAdam;

    private String aliceToken;
    private String bobToken;
    private String techToken;
    private String managerToken;
    private String adminToken;

    private Building buildingA;
    private Building buildingB;
    private Elevator elevatorA1;
    private Elevator elevatorB1;
    private IssueCategory noiseCategory;
    private IssueCategory entrapmentCategory;

    @BeforeEach
    void setUp() {
        notificationOutboxRepository.deleteAll();
        idempotencyRecordRepository.deleteAll();
        incidentHistoryRepository.deleteAll();
        incidentRepository.deleteAll();
        elevatorRepository.deleteAll();
        buildingRepository.deleteAll();
        issueCategoryRepository.deleteAll();
        slaPolicyRepository.deleteAll();
        userRepository.deleteAll();

        residentAlice = createUser("alice@liftcare.local", UserRole.RESIDENT, "Alice Resident");
        residentBob = createUser("bob@liftcare.local", UserRole.RESIDENT, "Bob Resident");
        technicianCarlos = createUser("carlos@liftcare.local", UserRole.TECHNICIAN, "Carlos Technician");
        managerMary = createUser("mary@liftcare.local", UserRole.BUILDING_MANAGER, "Mary Manager");
        adminAdam = createUser("adam@liftcare.local", UserRole.ADMINISTRATOR, "Adam Administrator");

        aliceToken = jwtService.generateToken(residentAlice);
        bobToken = jwtService.generateToken(residentBob);
        techToken = jwtService.generateToken(technicianCarlos);
        managerToken = jwtService.generateToken(managerMary);
        adminToken = jwtService.generateToken(adminAdam);

        buildingA = buildingRepository.save(new Building("BLD-A", "Tower Alpha", "100 Alpha Way", "Metropolis", "node-01"));
        buildingB = buildingRepository.save(new Building("BLD-B", "Tower Beta", "200 Beta Way", "Metropolis", "node-01"));

        elevatorA1 = elevatorRepository.save(new Elevator("LIFT-01", buildingA, "East Shaft", ElevatorOperationalStatus.ACTIVE));
        elevatorB1 = elevatorRepository.save(new Elevator("LIFT-02", buildingB, "North Shaft", ElevatorOperationalStatus.ACTIVE));

        noiseCategory = issueCategoryRepository.save(new IssueCategory(
                "CABIN_NOISE", "Motor Vibration", "Cabin vibration or abnormal noise", SeverityLevel.MEDIUM, false
        ));
        entrapmentCategory = issueCategoryRepository.save(new IssueCategory(
                "PASSENGER_ENTRAPMENT", "Passenger Entrapment", "Passengers trapped inside cabin", SeverityLevel.CRITICAL, true
        ));

        // Seed standard global policies for test cases
        slaPolicyRepository.save(new SlaPolicy(
                "Global Critical Policy", null, SeverityLevel.CRITICAL, null,
                15, 60, 5, UserRole.TECHNICIAN, UserRole.BUILDING_MANAGER, UserRole.ADMINISTRATOR
        ));
        slaPolicyRepository.save(new SlaPolicy(
                "Global Medium Policy", null, SeverityLevel.MEDIUM, null,
                60, 240, 15, UserRole.TECHNICIAN, UserRole.BUILDING_MANAGER, UserRole.ADMINISTRATOR
        ));
    }

    private User createUser(String email, UserRole role, String fullName) {
        User user = new User(fullName, email, "+1-555-0199", role, passwordEncoder.encode("Password123!"));
        return userRepository.save(user);
    }

    // =========================================================================
    // SECTION 3.A: Authentication, Authorization & Security Boundaries
    // =========================================================================

    @Test
    @DisplayName("3.A.1 Unauthenticated requests to protected endpoints are rejected with 401")
    void testUnauthenticatedRequestsRejected() throws Exception {
        mockMvc.perform(get("/api/incidents"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/sla-policies"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/buildings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"buildingCode\":\"BLD-C\",\"name\":\"Tower C\",\"address\":\"300 C St\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("3.A.2 Residents are strictly forbidden from administrative and staff endpoints (403)")
    void testResidentForbiddenFromStaffEndpoints() throws Exception {
        // Forbidden from SLA policy administration
        SlaPolicyRequest policyReq = new SlaPolicyRequest(
                "Unauthorized Policy", null, SeverityLevel.LOW, null, true,
                120, 480, 30, UserRole.TECHNICIAN, UserRole.BUILDING_MANAGER, UserRole.ADMINISTRATOR
        );
        mockMvc.perform(post("/api/sla-policies")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(policyReq)))
                .andExpect(status().isForbidden());

        // Forbidden from building creation
        mockMvc.perform(post("/api/buildings")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"buildingCode\":\"BLD-X\",\"name\":\"Forbidden\",\"address\":\"X St\"}"))
                .andExpect(status().isForbidden());

        // Forbidden from control room dashboard summary
        mockMvc.perform(get("/api/dashboard/summary")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isForbidden());

        // Forbidden from acknowledging tickets
        mockMvc.perform(patch("/api/incidents/1/acknowledge")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("3.A.3 Strict Cross-Resident Privacy: Resident Bob cannot access Alice's incidents")
    void testCrossResidentDataAccessPrevention() throws Exception {
        // Alice creates an incident
        IncidentCreateRequest req = new IncidentCreateRequest(
                buildingA.getId(), elevatorA1.getId(), noiseCategory.getId(),
                "Alice private complaint", SeverityLevel.MEDIUM, false
        );

        String aliceIncResponse = mockMvc.perform(post("/api/incidents")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long aliceIncidentId = objectMapper.readTree(aliceIncResponse).get("id").asLong();

        // 1. Bob attempts GET /api/incidents/{id} -> 403 Forbidden
        mockMvc.perform(get("/api/incidents/" + aliceIncidentId)
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isForbidden());

        // 2. Bob attempts GET /api/incidents/{id}/history -> 403 Forbidden
        mockMvc.perform(get("/api/incidents/" + aliceIncidentId + "/history")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isForbidden());

        // 3. Bob attempts GET /api/incidents/{id}/sla -> 403 Forbidden
        mockMvc.perform(get("/api/incidents/" + aliceIncidentId + "/sla")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isForbidden());

        // 4. Bob attempts POST /api/incidents/{id}/notes -> 403 Forbidden
        IncidentNoteRequest noteReq = new IncidentNoteRequest("Bob unauthorized comment", "WORK_LOG");
        mockMvc.perform(post("/api/incidents/" + aliceIncidentId + "/notes")
                        .header("Authorization", "Bearer " + bobToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noteReq)))
                .andExpect(status().isForbidden());

        // 5. Bob queries building incidents -> Alice's incident is not leaked
        mockMvc.perform(get("/api/buildings/" + buildingA.getId() + "/incidents")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // Staff CAN inspect the incident
        mockMvc.perform(get("/api/incidents/" + aliceIncidentId)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incident.ticketNumber", startsWith("INC-")));
    }

    // =========================================================================
    // SECTION 3.B: Full Incident Lifecycle & Operational Integrity
    // =========================================================================

    @Test
    @DisplayName("3.B.1 Complete Incident Lifecycle: Report -> SLA Snapshot -> Ack -> Assign -> Note -> Resolve -> Reopen -> Close")
    void testCompleteIncidentLifecycleAndReopening() throws Exception {
        // 1. Report Incident by Resident
        IncidentCreateRequest createReq = new IncidentCreateRequest(
                buildingA.getId(), elevatorA1.getId(), noiseCategory.getId(),
                "Loud motor grinding at floor 4", SeverityLevel.MEDIUM, false
        );

        String createRes = mockMvc.perform(post("/api/incidents")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticketNumber", startsWith("INC-")))
                .andExpect(jsonPath("$.status", is("REPORTED")))
                .andExpect(jsonPath("$.responseSlaState", is("ON_TRACK")))
                .andExpect(jsonPath("$.resolutionSlaState", is("ON_TRACK")))
                .andReturn().getResponse().getContentAsString();

        Long incidentId = objectMapper.readTree(createRes).get("id").asLong();

        // Verify initial history entry
        List<IncidentHistory> initialHistory = incidentHistoryRepository.findByIncidentIdOrderByChangedAtAsc(incidentId);
        assertEquals(1, initialHistory.size());
        assertEquals("INCIDENT_REPORTED", initialHistory.get(0).getAction());
        assertEquals(ActorType.USER, initialHistory.get(0).getActorType());
        assertEquals(residentAlice.getId(), initialHistory.get(0).getChangedBy().getId());

        // 2. Staff Acknowledges Incident
        mockMvc.perform(patch("/api/incidents/" + incidentId + "/acknowledge")
                        .header("Authorization", "Bearer " + managerToken)
                        .param("remarks", "Acknowledged by facility desk"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACKNOWLEDGED")))
                .andExpect(jsonPath("$.responseSlaState", is("MET")))
                .andExpect(jsonPath("$.resolutionSlaState", is("ON_TRACK")));

        // Verify Response SLA is satisfied but Resolution SLA remains ON_TRACK
        Incident afterAck = incidentRepository.findById(incidentId).orElseThrow();
        assertNotNull(afterAck.getAcknowledgedAt(), "AcknowledgedAt must be set");
        assertNull(afterAck.getResolvedAt(), "ResolvedAt must NOT be set on acknowledgement");
        assertEquals(SlaState.MET, afterAck.calculateResponseSlaState(Instant.now()));
        assertEquals(SlaState.ON_TRACK, afterAck.calculateResolutionSlaState(Instant.now()));

        // 3. Manager Assigns Technician
        IncidentAssignRequest assignReq = new IncidentAssignRequest(technicianCarlos.getId(), "Assigned to primary technician");
        mockMvc.perform(patch("/api/incidents/" + incidentId + "/assign")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ASSIGNED")))
                .andExpect(jsonPath("$.assignedToId", is(technicianCarlos.getId().intValue())));

        // 4. Technician Commences Work & Adds Work Note
        mockMvc.perform(patch("/api/incidents/" + incidentId + "/status")
                        .header("Authorization", "Bearer " + techToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new IncidentStatusUpdateRequest(
                                IncidentStatus.IN_PROGRESS, "Technician on-site inspecting pulley bearings", null
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("IN_PROGRESS")));

        IncidentNoteRequest noteReq = new IncidentNoteRequest("Pulley bearings lubricated, testing vibration levels.", "WORK_LOG");
        mockMvc.perform(post("/api/incidents/" + incidentId + "/notes")
                        .header("Authorization", "Bearer " + techToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noteReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.action", is("WORK_NOTE_ADDED")));

        // 5. Technician Marks Incident as RESOLVED
        mockMvc.perform(patch("/api/incidents/" + incidentId + "/status")
                        .header("Authorization", "Bearer " + techToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new IncidentStatusUpdateRequest(
                                IncidentStatus.RESOLVED, "Vibration eliminated, lift safe to operate.", ElevatorOperationalStatus.ACTIVE
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("RESOLVED")))
                .andExpect(jsonPath("$.resolutionSlaState", is("MET")));

        Incident resolved = incidentRepository.findById(incidentId).orElseThrow();
        assertNotNull(resolved.getResolvedAt());
        assertEquals(SlaState.MET, resolved.calculateResolutionSlaState(Instant.now()));

        // 6. Issue Recurs: Manager Reopens Incident back to IN_PROGRESS
        mockMvc.perform(patch("/api/incidents/" + incidentId + "/status")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new IncidentStatusUpdateRequest(
                                IncidentStatus.IN_PROGRESS, "Resident reports noise recurred during evening peak.", null
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("IN_PROGRESS")));

        Incident reopened = incidentRepository.findById(incidentId).orElseThrow();
        assertNull(reopened.getResolvedAt(), "ResolvedAt must be cleared upon reopening");
        assertEquals(SlaState.ON_TRACK, reopened.calculateResolutionSlaState(Instant.now()), "Resolution SLA must return to active tracking");

        // Verify history records INCIDENT_REOPENED
        List<IncidentHistory> reopenHistory = incidentHistoryRepository.findByIncidentIdOrderByChangedAtAsc(incidentId);
        boolean hasReopenAudit = reopenHistory.stream().anyMatch(h -> "INCIDENT_REOPENED".equals(h.getAction()));
        assertTrue(hasReopenAudit, "History must record INCIDENT_REOPENED action");

        // 7. Resolve and Close Ticket
        mockMvc.perform(patch("/api/incidents/" + incidentId + "/status")
                        .header("Authorization", "Bearer " + techToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new IncidentStatusUpdateRequest(
                                IncidentStatus.RESOLVED, "Replaced bearing assembly completely.", null
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/incidents/" + incidentId + "/status")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new IncidentStatusUpdateRequest(
                                IncidentStatus.CLOSED, "Final sign-off by facility manager.", null
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CLOSED")));

        // Further modifications to CLOSED ticket are rejected
        mockMvc.perform(patch("/api/incidents/" + incidentId + "/status")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new IncidentStatusUpdateRequest(
                                IncidentStatus.IN_PROGRESS, "Attempting update on closed ticket", null
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("3.B.2 Entrapment Safety: Enforces CRITICAL severity & transitions elevator to OUT_OF_SERVICE")
    void testEntrapmentSafetyAndStatusAssertion() throws Exception {
        // 1. Attempting to submit entrapment with LOW severity is rejected with 400 (anti-downgrade rule)
        IncidentCreateRequest invalidLowReq = new IncidentCreateRequest(
                buildingA.getId(), elevatorA1.getId(), entrapmentCategory.getId(),
                "Two passengers trapped", SeverityLevel.LOW, true
        );
        mockMvc.perform(post("/api/incidents")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidLowReq)))
                .andExpect(status().isBadRequest());

        // 2. Submitting entrapment mandates CRITICAL severity and transitions elevator to OUT_OF_SERVICE
        IncidentCreateRequest entrapmentReq = new IncidentCreateRequest(
                buildingA.getId(), elevatorA1.getId(), entrapmentCategory.getId(),
                "Two passengers trapped between floor 2 and 3", null, true
        );

        String response = mockMvc.perform(post("/api/incidents")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entrapmentReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.severity", is("CRITICAL")))
                .andExpect(jsonPath("$.entrapmentReported", is(true)))
                .andReturn().getResponse().getContentAsString();

        Long incidentId = objectMapper.readTree(response).get("id").asLong();

        // Verify elevator status transitioned to OUT_OF_SERVICE
        Elevator elevator = elevatorRepository.findById(elevatorA1.getId()).orElseThrow();
        assertEquals(ElevatorOperationalStatus.OUT_OF_SERVICE, elevator.getOperationalStatus());

        // Verify elevator shutdown history entry exists
        List<IncidentHistory> histories = incidentHistoryRepository.findByIncidentIdOrderByChangedAtAsc(incidentId);
        boolean hasShutdownHistory = histories.stream().anyMatch(h ->
                "ELEVATOR_OUT_OF_SERVICE".equals(h.getAction()) && h.getActorType() == ActorType.SYSTEM
        );
        assertTrue(hasShutdownHistory, "Elevator safety shutdown must be audited with ActorType.SYSTEM");
    }

    // =========================================================================
    // SECTION 3.C: Offline Queue, Idempotency & Conflict Prevention
    // =========================================================================

    @Test
    @DisplayName("3.C.1 Idempotency Key deduplication: Replays return identical ticket; conflicting payloads are rejected")
    void testOfflineIdempotentIncidentSubmissionAndConflictRejection() throws Exception {
        IncidentCreateRequest originalPayload = new IncidentCreateRequest(
                buildingA.getId(), elevatorA1.getId(), noiseCategory.getId(),
                "Vibration complaint with offline key", SeverityLevel.MEDIUM, false
        );
        String idempotencyKey = "client-uuid-999-alpha";

        // 1. Initial submission -> 201 Created with MISS header
        String firstResponse = mockMvc.perform(post("/api/incidents")
                        .header("Authorization", "Bearer " + aliceToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(originalPayload)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Idempotency-Cache", "MISS"))
                .andExpect(jsonPath("$.ticketNumber", notNullValue()))
                .andReturn().getResponse().getContentAsString();

        String ticketNumber = objectMapper.readTree(firstResponse).get("ticketNumber").asText();
        assertEquals(1, incidentRepository.count());

        // 2. Replay with identical payload and key -> 201 Created with HIT header and identical ticket
        mockMvc.perform(post("/api/incidents")
                        .header("Authorization", "Bearer " + aliceToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(originalPayload)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Idempotency-Cache", "HIT"))
                .andExpect(jsonPath("$.ticketNumber", is(ticketNumber)));

        assertEquals(1, incidentRepository.count(), "Replay must NOT create duplicate incident");

        // 3. Replay with same key but altered payload -> 400 Bad Request
        IncidentCreateRequest alteredPayload = new IncidentCreateRequest(
                buildingA.getId(), elevatorA1.getId(), noiseCategory.getId(),
                "Different description attempting payload collision", SeverityLevel.HIGH, false
        );
        mockMvc.perform(post("/api/incidents")
                        .header("Authorization", "Bearer " + aliceToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(alteredPayload)))
                .andExpect(status().isBadRequest());

        // 4. Client validation error -> 400 Bad Request immediately (prevents client endless retries)
        IncidentCreateRequest invalidPayload = new IncidentCreateRequest(
                buildingA.getId(), elevatorA1.getId(), noiseCategory.getId(),
                "", // Blank description
                SeverityLevel.MEDIUM, false
        );
        mockMvc.perform(post("/api/incidents")
                        .header("Authorization", "Bearer " + aliceToken)
                        .header("Idempotency-Key", "client-uuid-invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidPayload)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // SECTION 3.D: SLA & Escalation Reliability & Edge Cases
    // =========================================================================

    @Test
    @DisplayName("3.D.1 Dual SLA Warnings: Both response warning and resolution warning fire with dedicated scopes")
    void testResponseAndResolutionWarnings() throws Exception {
        // Create an incident reported 25 minutes ago under a 30m response / 120m resolution / 10m warning policy
        SlaPolicy warningPolicy = slaPolicyRepository.save(new SlaPolicy(
                "Warning Test Policy", buildingA, SeverityLevel.MEDIUM, null,
                30, 120, 10, UserRole.TECHNICIAN, UserRole.BUILDING_MANAGER, UserRole.ADMINISTRATOR
        ));

        Incident incident = new Incident(
                "INC-WARN-TEST-01", buildingA, elevatorA1, residentAlice, noiseCategory,
                "Warning test incident", SeverityLevel.MEDIUM, false
        );
        Instant reportedAt = Instant.now().minus(Duration.ofMinutes(25)); // 5 minutes before response deadline
        incident.setReportedAt(reportedAt);
        incident.applySlaPolicy(warningPolicy, reportedAt);
        incident = incidentRepository.save(incident);

        // Run evaluator -> Should fire Response Warning
        slaEvaluationService.evaluateIncident(incident, Instant.now());

        List<IncidentHistory> warnings1 = incidentHistoryRepository.findByIncidentIdOrderByChangedAtAsc(incident.getId());
        boolean hasRespWarn = warnings1.stream().anyMatch(h ->
                "SLA_WARNING_TRIGGERED".equals(h.getAction()) && h.getRemarks().contains("Response deadline")
        );
        assertTrue(hasRespWarn, "Response warning must be triggered");

        // Verify outbox queued response warning
        List<NotificationOutbox> outboxItems1 = notificationOutboxRepository.findByIncidentId(incident.getId());
        boolean hasRespOutbox = outboxItems1.stream().anyMatch(o -> o.getDeduplicationKey().contains("SLA-WARN-RESPONSE"));
        assertTrue(hasRespOutbox, "Outbox item for response warning must exist");

        // Acknowledge incident -> Resets sla_warning_sent
        mockMvc.perform(patch("/api/incidents/" + incident.getId() + "/acknowledge")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk());

        Incident ackIncident = incidentRepository.findById(incident.getId()).orElseThrow();
        assertFalse(ackIncident.isSlaWarningSent(), "sla_warning_sent must be reset on acknowledgment");

        // Fast-forward to 115 minutes past report (5 minutes before 120m resolution deadline)
        Instant nearResolutionDeadline = reportedAt.plus(Duration.ofMinutes(115));
        slaEvaluationService.evaluateIncident(ackIncident, nearResolutionDeadline);

        List<IncidentHistory> warnings2 = incidentHistoryRepository.findByIncidentIdOrderByChangedAtAsc(incident.getId());
        boolean hasResolWarn = warnings2.stream().anyMatch(h ->
                "SLA_WARNING_TRIGGERED".equals(h.getAction()) && h.getRemarks().contains("Resolution deadline")
        );
        assertTrue(hasResolWarn, "Resolution warning must be triggered when approaching resolution deadline");

        // Verify outbox queued resolution warning with distinct dedup key
        List<NotificationOutbox> outboxItems2 = notificationOutboxRepository.findByIncidentId(incident.getId());
        boolean hasResolOutbox = outboxItems2.stream().anyMatch(o -> o.getDeduplicationKey().contains("SLA-WARN-RESOLUTION"));
        assertTrue(hasResolOutbox, "Outbox item for resolution warning must exist with distinct key");
    }

    @Test
    @DisplayName("3.D.2 Automated Multi-Tier Escalation: Response breach triggers Tier 1 & Tier 2; Resolution breach triggers Tier 3")
    void testLateBreachesAndAutomatedEscalationTiers() throws Exception {
        Incident incident = new Incident(
                "INC-ESC-TIER-01", buildingA, elevatorA1, residentAlice, noiseCategory,
                "Escalation test", SeverityLevel.MEDIUM, false
        );
        Instant reportedAt = Instant.now().minus(Duration.ofMinutes(70)); // 10 minutes past 60m response target
        incident.setReportedAt(reportedAt);
        incident.applySlaPolicy(slaPolicyRepository.findAll().get(1), reportedAt); // 60m resp / 240m resol
        incident = incidentRepository.save(incident);

        // Run evaluator -> Response SLA breached & escalated to Tier 1
        slaEvaluationService.evaluateIncident(incident, Instant.now());

        Incident tier1 = incidentRepository.findById(incident.getId()).orElseThrow();
        assertTrue(tier1.isResponseSlaBreached());
        assertEquals(1, tier1.getEscalationLevel());

        // Fast-forward past Tier 2 window
        Instant tier2Time = incident.getResponseDeadline().plus(Duration.ofMinutes(75));
        slaEvaluationService.evaluateIncident(tier1, tier2Time);

        Incident tier2 = incidentRepository.findById(incident.getId()).orElseThrow();
        assertEquals(2, tier2.getEscalationLevel());

        // Fast-forward past 240m resolution deadline
        Instant tier3Time = reportedAt.plus(Duration.ofMinutes(250));
        slaEvaluationService.evaluateIncident(tier2, tier3Time);

        Incident tier3 = incidentRepository.findById(incident.getId()).orElseThrow();
        assertTrue(tier3.isResolutionSlaBreached());
        assertEquals(3, tier3.getEscalationLevel());

        // Repeated run -> Idempotent, level remains 3
        slaEvaluationService.evaluateIncident(tier3, tier3Time.plus(Duration.ofMinutes(10)));
        Incident tier3Again = incidentRepository.findById(incident.getId()).orElseThrow();
        assertEquals(3, tier3Again.getEscalationLevel());
    }

    @Test
    @DisplayName("3.D.3 Missing Policy & Policy Deactivation: Historical targets preserved; missing policy logged safely")
    void testMissingPolicyAndPolicyDeactivationPreservesHistoricalSnapshots() throws Exception {
        // 1. Create custom building policy
        SlaPolicy policy = slaPolicyRepository.save(new SlaPolicy(
                "Tower Alpha Custom", buildingA, SeverityLevel.MEDIUM, null,
                45, 180, 10, UserRole.TECHNICIAN, UserRole.BUILDING_MANAGER, UserRole.ADMINISTRATOR
        ));

        Incident incident = new Incident(
                "INC-SNAP-01", buildingA, elevatorA1, residentAlice, noiseCategory,
                "Snapshot test", SeverityLevel.MEDIUM, false
        );
        Instant reportedAt = Instant.now();
        incident.setReportedAt(reportedAt);
        incident.applySlaPolicy(policy, reportedAt);
        incident = incidentRepository.save(incident);

        assertEquals(45, incident.getSlaResponseTargetMinutes());
        assertEquals(180, incident.getSlaResolutionTargetMinutes());

        // 2. Deactivate policy
        mockMvc.perform(patch("/api/sla-policies/" + policy.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // 3. Historical incident targets remain completely preserved
        Incident reloaded = incidentRepository.findById(incident.getId()).orElseThrow();
        assertEquals(45, reloaded.getSlaResponseTargetMinutes());
        assertEquals(180, reloaded.getSlaResolutionTargetMinutes());

        // 4. Delete all policies and report new incident -> Policy Missing handled safely
        slaPolicyRepository.deleteAll();

        IncidentCreateRequest noPolicyReq = new IncidentCreateRequest(
                buildingB.getId(), elevatorB1.getId(), noiseCategory.getId(),
                "Incident with no policy in system", SeverityLevel.LOW, false
        );

        String res = mockMvc.perform(post("/api/incidents")
                        .header("Authorization", "Bearer " + bobToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noPolicyReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long noPolicyIncId = objectMapper.readTree(res).get("id").asLong();
        Incident noPolicyInc = incidentRepository.findById(noPolicyIncId).orElseThrow();
        assertNull(noPolicyInc.getSlaPolicy());
        assertNull(noPolicyInc.getResponseDeadline());
        assertEquals(SlaState.POLICY_MISSING, noPolicyInc.calculateResponseSlaState(Instant.now()));

        List<IncidentHistory> noPolicyHistories = incidentHistoryRepository.findByIncidentIdOrderByChangedAtAsc(noPolicyIncId);
        boolean hasMissingAudit = noPolicyHistories.stream().anyMatch(h ->
                "SLA_POLICY_MISSING".equals(h.getAction()) && h.getActorType() == ActorType.SYSTEM
        );
        assertTrue(hasMissingAudit, "Missing policy must produce SLA_POLICY_MISSING history with ActorType.SYSTEM");
    }

    @Test
    @DisplayName("3.D.4 Missing Escalation Recipients: Records ESCALATION_DELIVERY_FAILED without crashing")
    void testMissingEscalationRecipientsAndFailingIncidentIsolation() throws Exception {
        // Remove technician and building manager
        userRepository.delete(technicianCarlos);
        userRepository.delete(managerMary);

        Incident incident = new Incident(
                "INC-NO-RECIP-01", buildingA, elevatorA1, residentAlice, noiseCategory,
                "No recipient test", SeverityLevel.MEDIUM, false
        );
        Instant reportedAt = Instant.now().minus(Duration.ofMinutes(80));
        incident.setReportedAt(reportedAt);
        incident.applySlaPolicy(slaPolicyRepository.findAll().get(1), reportedAt);
        incident = incidentRepository.save(incident);

        // Run evaluation cycle -> Recipient missing handled safely
        int evaluated = slaEvaluationService.evaluateSlaPoliciesAndEscalate();
        assertTrue(evaluated >= 1);

        List<IncidentHistory> histories = incidentHistoryRepository.findByIncidentIdOrderByChangedAtAsc(incident.getId());
        boolean hasFailAudit = histories.stream().anyMatch(h ->
                "ESCALATION_DELIVERY_FAILED".equals(h.getAction()) && h.getActorType() == ActorType.SYSTEM
        );
        assertTrue(hasFailAudit, "Must record ESCALATION_DELIVERY_FAILED history");

        // Verify outbox queued failure notification
        List<NotificationOutbox> outbox = notificationOutboxRepository.findByIncidentId(incident.getId());
        boolean hasFailOutbox = outbox.stream().anyMatch(o -> o.getDeduplicationKey().contains("SLA-ESC-FAIL"));
        assertTrue(hasFailOutbox, "Must enqueue failure notification to administrator");
    }

    // =========================================================================
    // SECTION 3.E: Audit & Notification Outbox Consistency
    // =========================================================================

    @Test
    @DisplayName("3.E.1 Audit Consistency: System events use ActorType.SYSTEM; Human actions use ActorType.USER")
    void testSystemActorAndOutboxDeliveryIntegrity() throws Exception {
        // Report incident by human
        IncidentCreateRequest req = new IncidentCreateRequest(
                buildingA.getId(), elevatorA1.getId(), noiseCategory.getId(),
                "Human audit verification", SeverityLevel.MEDIUM, false
        );
        String createRes = mockMvc.perform(post("/api/incidents")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long incidentId = objectMapper.readTree(createRes).get("id").asLong();

        // Check human event
        List<IncidentHistory> userEvents = incidentHistoryRepository.findByIncidentIdOrderByChangedAtAsc(incidentId);
        assertEquals(1, userEvents.size());
        assertEquals(ActorType.USER, userEvents.get(0).getActorType());
        assertEquals(residentAlice.getId(), userEvents.get(0).getChangedBy().getId());

        // Fast-forward and trigger breach
        Incident inc = incidentRepository.findById(incidentId).orElseThrow();
        inc.setReportedAt(Instant.now().minus(Duration.ofMinutes(75)));
        inc.setResponseDeadline(Instant.now().minus(Duration.ofMinutes(15)));
        incidentRepository.save(inc);

        slaEvaluationService.evaluateIncident(inc, Instant.now());

        // Check system event
        List<IncidentHistory> allEvents = incidentHistoryRepository.findByIncidentIdOrderByChangedAtAsc(incidentId);
        IncidentHistory breachEvent = allEvents.stream()
                .filter(h -> "RESPONSE_SLA_BREACHED".equals(h.getAction()))
                .findFirst().orElseThrow();

        assertEquals(ActorType.SYSTEM, breachEvent.getActorType());
        assertNull(breachEvent.getChangedBy(), "SYSTEM events must have null changedBy");

        // Check outbox delivery state machine
        List<NotificationOutbox> outboxItems = notificationOutboxRepository.findByIncidentId(incidentId);
        assertFalse(outboxItems.isEmpty());
        for (NotificationOutbox outbox : outboxItems) {
            assertEquals(NotificationDeliveryState.SENT, outbox.getDeliveryState());
            assertNotNull(outbox.getDeliveredAt());
            assertNotNull(outbox.getDeduplicationKey());
        }
    }
}
