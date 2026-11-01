package com.liftcare;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liftcare.dto.IncidentCreateRequest;
import com.liftcare.dto.IncidentStatusUpdateRequest;
import com.liftcare.dto.SlaPolicyRequest;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SlaAndEscalationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SlaPolicyRepository slaPolicyRepository;

    @Autowired
    private NotificationOutboxRepository notificationOutboxRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

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

    @Autowired
    private SlaEvaluationService slaEvaluationService;

    private User admin;
    private User manager;
    private User technician;
    private User resident;

    private String adminToken;
    private String managerToken;
    private String techToken;
    private String residentToken;

    private Building buildingA;
    private Elevator elevatorA1;
    private IssueCategory entrapmentCategory;
    private IssueCategory noiseCategory;

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

        admin = createUser("admin@liftcare.local", UserRole.ADMINISTRATOR, "Admin User");
        manager = createUser("manager@liftcare.local", UserRole.BUILDING_MANAGER, "Frank Manager");
        technician = createUser("carlos@liftcare.local", UserRole.TECHNICIAN, "Carlos Tech");
        resident = createUser("alice@liftcare.local", UserRole.RESIDENT, "Alice Resident");

        adminToken = jwtService.generateToken(admin);
        managerToken = jwtService.generateToken(manager);
        techToken = jwtService.generateToken(technician);
        residentToken = jwtService.generateToken(resident);

        buildingA = buildingRepository.save(new Building("BLD-A", "Tower Alpha", "100 Innovation Way", "Metro", "node-01"));
        elevatorA1 = elevatorRepository.save(new Elevator("LIFT-01", buildingA, "East Shaft", ElevatorOperationalStatus.ACTIVE));

        entrapmentCategory = issueCategoryRepository.save(new IssueCategory(
                "ENTRAPMENT", "Passenger Entrapment", "Trapped passengers inside cabin", SeverityLevel.CRITICAL, true
        ));
        noiseCategory = issueCategoryRepository.save(new IssueCategory(
                "NOISE", "Unusual Grinding Noise", "Abnormal acoustic noise", SeverityLevel.MEDIUM, false
        ));
    }

    private User createUser(String email, UserRole role, String fullName) {
        User user = new User(
                fullName,
                email,
                "+1-555-0199",
                role,
                passwordEncoder.encode("Password123!")
        );
        user.setActive(true);
        return userRepository.save(user);
    }

    @Test
    @DisplayName("SLA Policy CRUD: Administrator can create policy; Resident is forbidden")
    void testSlaPolicyPermissionsAndValidation() throws Exception {
        SlaPolicyRequest validReq = new SlaPolicyRequest(
                "Tower Alpha Custom Medium SLA",
                buildingA.getId(),
                SeverityLevel.MEDIUM,
                null,
                true,
                60,   // Response 1 hr
                360,  // Resolution 6 hrs
                15,   // Warning 15 mins
                UserRole.TECHNICIAN,
                UserRole.BUILDING_MANAGER,
                UserRole.COMPANY_SUPERVISOR
        );

        // Resident attempt must be forbidden (403)
        mockMvc.perform(post("/api/sla-policies")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isForbidden());

        // Administrator succeeds (201)
        mockMvc.perform(post("/api/sla-policies")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Tower Alpha Custom Medium SLA"))
                .andExpect(jsonPath("$.responseTargetMinutes").value(60))
                .andExpect(jsonPath("$.version").value(1));

        // Invalid durations: response > resolution must be rejected (400)
        SlaPolicyRequest invalidReq = new SlaPolicyRequest(
                "Invalid Inverted Target Policy",
                null,
                SeverityLevel.LOW,
                null,
                true,
                500,  // Response > Resolution!
                100,
                30,
                UserRole.TECHNICIAN,
                UserRole.BUILDING_MANAGER,
                UserRole.COMPANY_SUPERVISOR
        );

        mockMvc.perform(post("/api/sla-policies")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("cannot exceed resolution target")));
    }

    @Test
    @DisplayName("SLA Precedence: Building-specific policy takes precedence over global default")
    void testPolicyPrecedenceAndSnapshotting() throws Exception {
        // 1. Create global medium policy (120m response, 600m resolution)
        SlaPolicy globalPolicy = slaPolicyRepository.save(new SlaPolicy(
                "Global Medium", null, SeverityLevel.MEDIUM, null,
                120, 600, 30, UserRole.TECHNICIAN, UserRole.BUILDING_MANAGER, UserRole.COMPANY_SUPERVISOR
        ));

        // 2. Create building-specific medium policy for buildingA (45m response, 240m resolution)
        SlaPolicy bldPolicy = slaPolicyRepository.save(new SlaPolicy(
                "Building A Medium", buildingA, SeverityLevel.MEDIUM, null,
                45, 240, 10, UserRole.TECHNICIAN, UserRole.BUILDING_MANAGER, UserRole.COMPANY_SUPERVISOR
        ));

        IncidentCreateRequest req = new IncidentCreateRequest(
                buildingA.getId(),
                elevatorA1.getId(),
                noiseCategory.getId(),
                "Grinding sounds during ascent",
                SeverityLevel.MEDIUM,
                false
        );

        // Report incident
        mockMvc.perform(post("/api/incidents")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticketNumber").exists());

        Incident incident = incidentRepository.findAll().get(0);
        assertNotNull(incident.getSlaPolicy(), "Policy must be resolved and attached");
        assertEquals(bldPolicy.getId(), incident.getSlaPolicy().getId(), "Building-specific policy must have precedence over global policy");
        assertEquals(45, incident.getSlaResponseTargetMinutes());
        assertEquals(240, incident.getSlaResolutionTargetMinutes());
        assertNotNull(incident.getResponseDeadline());
        assertNotNull(incident.getResolutionDeadline());

        // Now modify the policy; existing incident targets must NOT change (historical preservation)
        bldPolicy.setResponseTargetMinutes(999);
        slaPolicyRepository.save(bldPolicy);

        Incident reloaded = incidentRepository.findById(incident.getId()).orElseThrow();
        assertEquals(45, reloaded.getSlaResponseTargetMinutes(), "Historical incident target must remain preserved at 45m");
    }

    @Test
    @DisplayName("SLA Missing Policy: Handled safely and surfaces POLICY_MISSING in history")
    void testMissingPolicyHandledSafely() throws Exception {
        // No policies exist in DB
        assertEquals(0, slaPolicyRepository.count());

        IncidentCreateRequest req = new IncidentCreateRequest(
                buildingA.getId(),
                elevatorA1.getId(),
                noiseCategory.getId(),
                "No policy test",
                SeverityLevel.LOW,
                false
        );

        mockMvc.perform(post("/api/incidents")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        Incident incident = incidentRepository.findAll().get(0);
        assertNull(incident.getSlaPolicy());
        assertNull(incident.getResponseDeadline());

        List<IncidentHistory> history = incidentHistoryRepository.findByIncidentIdOrderByChangedAtAsc(incident.getId());
        boolean hasMissingPolicyAudit = history.stream().anyMatch(h ->
                "SLA_POLICY_MISSING".equals(h.getAction()) && h.getActorType() == ActorType.SYSTEM
        );
        assertTrue(hasMissingPolicyAudit, "IncidentHistory must record SLA_POLICY_MISSING by SYSTEM actor");
    }

    @Test
    @DisplayName("SLA Lifecycle: Acknowledge satisfies Response SLA; Resolve satisfies Resolution SLA")
    void testAcknowledgeAndResolveSatisfiesClocks() throws Exception {
        slaPolicyRepository.save(new SlaPolicy(
                "Global Medium", null, SeverityLevel.MEDIUM, null,
                60, 240, 15, UserRole.TECHNICIAN, UserRole.BUILDING_MANAGER, UserRole.COMPANY_SUPERVISOR
        ));

        Incident incident = new Incident(
                "INC-TEST-SLA-01", buildingA, elevatorA1, resident, noiseCategory, "Noise", SeverityLevel.MEDIUM, false
        );
        Instant reported = Instant.now().minus(Duration.ofMinutes(10));
        incident.setReportedAt(reported);
        incident.applySlaPolicy(slaPolicyRepository.findAll().get(0), reported);
        incident = incidentRepository.save(incident);

        // Initially reported: response clock is ON_TRACK, resolution is ON_TRACK
        assertEquals(SlaState.ON_TRACK, incident.calculateResponseSlaState(Instant.now()));
        assertEquals(SlaState.ON_TRACK, incident.calculateResolutionSlaState(Instant.now()));

        // Acknowledge incident
        mockMvc.perform(patch("/api/incidents/" + incident.getId() + "/acknowledge")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk());

        Incident acked = incidentRepository.findById(incident.getId()).orElseThrow();
        assertNotNull(acked.getAcknowledgedAt(), "acknowledgedAt must be set");
        assertEquals(SlaState.MET, acked.calculateResponseSlaState(Instant.now()));

        // Resolve incident
        IncidentStatusUpdateRequest resolveReq = new IncidentStatusUpdateRequest(
                IncidentStatus.RESOLVED, "Repaired bearing noise", ElevatorOperationalStatus.ACTIVE
        );
        mockMvc.perform(patch("/api/incidents/" + incident.getId() + "/status")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resolveReq)))
                .andExpect(status().isOk());

        Incident resolved = incidentRepository.findById(incident.getId()).orElseThrow();
        assertNotNull(resolved.getResolvedAt(), "resolvedAt must be set");
        assertEquals(SlaState.MET, resolved.calculateResolutionSlaState(Instant.now()));
    }

    @Test
    @DisplayName("SLA Warning & Escalation Engine: Automated evaluation triggers warning and Tier 1 escalation")
    void testScheduledEvaluationTriggersWarningAndEscalation() throws Exception {
        // Policy: Response target 30 mins, Warning threshold 10 mins (triggers at T+20)
        SlaPolicy policy = slaPolicyRepository.save(new SlaPolicy(
                "Fast SLA", null, SeverityLevel.HIGH, null,
                30, 120, 10, UserRole.TECHNICIAN, UserRole.BUILDING_MANAGER, UserRole.COMPANY_SUPERVISOR
        ));

        Instant creationTime = Instant.now().minus(Duration.ofMinutes(35)); // 35 minutes ago: both warning and breach passed!
        Incident incident = new Incident(
                "INC-TEST-ESC-01", buildingA, elevatorA1, resident, noiseCategory, "Overdue fault", SeverityLevel.HIGH, false
        );
        incident.setReportedAt(creationTime);
        incident.applySlaPolicy(policy, creationTime);
        incident = incidentRepository.save(incident);

        assertEquals(0, incident.getEscalationLevel());
        assertFalse(incident.isResponseSlaBreached());

        // Run evaluation cycle at current time
        slaEvaluationService.evaluateIncident(incident, Instant.now());

        Incident evaluated = incidentRepository.findById(incident.getId()).orElseThrow();
        assertTrue(evaluated.isResponseSlaBreached(), "Response SLA must be marked breached");
        assertEquals(1, evaluated.getEscalationLevel(), "Incident must be escalated to Tier 1");

        // Verify history entries
        List<IncidentHistory> history = incidentHistoryRepository.findByIncidentIdOrderByChangedAtAsc(incident.getId());
        boolean hasBreachAudit = history.stream().anyMatch(h ->
                "RESPONSE_SLA_BREACHED".equals(h.getAction()) && h.getActorType() == ActorType.SYSTEM
        );
        boolean hasEscalationAudit = history.stream().anyMatch(h ->
                "INCIDENT_ESCALATED".equals(h.getAction()) &&
                "1".equals(h.getNewValue()) &&
                h.getActorType() == ActorType.SYSTEM
        );
        assertTrue(hasBreachAudit, "History must record RESPONSE_SLA_BREACHED by SYSTEM");
        assertTrue(hasEscalationAudit, "History must record INCIDENT_ESCALATED by SYSTEM");

        // Verify outbox notification enqueued for technician
        List<NotificationOutbox> outbox = notificationOutboxRepository.findAll();
        boolean hasEscNotification = outbox.stream().anyMatch(n ->
                "INCIDENT_ESCALATED".equals(n.getEventType()) &&
                n.getRecipientUser() != null &&
                n.getRecipientUser().getId().equals(technician.getId())
        );
        assertTrue(hasEscNotification, "Outbox must contain Tier 1 escalation notification for active technician");

        // Repeated run should NOT duplicate escalation or history
        int historyCountBefore = history.size();
        int outboxCountBefore = outbox.size();

        slaEvaluationService.evaluateIncident(evaluated, Instant.now());

        int historyCountAfter = incidentHistoryRepository.findByIncidentIdOrderByChangedAtAsc(incident.getId()).size();
        int outboxCountAfter = notificationOutboxRepository.findAll().size();

        assertEquals(historyCountBefore, historyCountAfter, "Repeated scheduler execution must be strictly idempotent");
        assertEquals(outboxCountBefore, outboxCountAfter, "Outbox records must not duplicate on repeated scheduler run");
    }

    @Test
    @DisplayName("SLA Detail API & Dashboard Summary Telemetry")
    void testIncidentSlaEndpointAndDashboardMetrics() throws Exception {
        SlaPolicy policy = slaPolicyRepository.save(new SlaPolicy(
                "Dashboard Test Policy", null, SeverityLevel.CRITICAL, null,
                15, 60, 5, UserRole.SECURITY_STAFF, UserRole.BUILDING_MANAGER, UserRole.COMPANY_SUPERVISOR
        ));

        Incident incident = new Incident(
                "INC-TEST-DASH-01", buildingA, elevatorA1, resident, entrapmentCategory, "Trapped in lift", SeverityLevel.CRITICAL, true
        );
        Instant reported = Instant.now().minus(Duration.ofMinutes(20)); // Breached response!
        incident.setReportedAt(reported);
        incident.applySlaPolicy(policy, reported);
        incident.setResponseSlaBreached(true);
        incident.setEscalationLevel(1);
        incident = incidentRepository.save(incident);

        // Test GET /api/incidents/{id}/sla
        mockMvc.perform(get("/api/incidents/" + incident.getId() + "/sla")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value(incident.getId()))
                .andExpect(jsonPath("$.ticketNumber").value("INC-TEST-DASH-01"))
                .andExpect(jsonPath("$.responseSlaState").value("BREACHED"))
                .andExpect(jsonPath("$.escalationLevel").value(1));

        // Test GET /api/dashboard/summary reflects SLA metrics
        mockMvc.perform(get("/api/dashboard/summary")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseSlaBreachedCount").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.escalatedTier1Count").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.activeEntrapments").value(greaterThanOrEqualTo(1)));
    }
}
