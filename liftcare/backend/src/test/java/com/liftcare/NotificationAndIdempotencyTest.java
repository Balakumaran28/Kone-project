package com.liftcare;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liftcare.dto.IncidentAssignRequest;
import com.liftcare.dto.IncidentCreateRequest;
import com.liftcare.dto.IncidentNoteRequest;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class NotificationAndIdempotencyTest {

    @Autowired
    private MockMvc mockMvc;

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

    private User residentAlice;
    private User technicianCarlos;
    private User managerFrank;
    private String aliceToken;
    private String carlosToken;
    private String managerToken;

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
        userRepository.deleteAll();

        residentAlice = createUser("alice@liftcare.local", UserRole.RESIDENT, "Alice Resident");
        technicianCarlos = createUser("carlos@liftcare.local", UserRole.TECHNICIAN, "Carlos Technician");
        managerFrank = createUser("frank@liftcare.local", UserRole.BUILDING_MANAGER, "Frank Manager");

        aliceToken = generateToken(residentAlice);
        carlosToken = generateToken(technicianCarlos);
        managerToken = generateToken(managerFrank);

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

    private String generateToken(User user) {
        return jwtService.generateToken(user);
    }

    @Test
    @DisplayName("Idempotency: Repeated POST /api/incidents with identical key returns cached response without duplicate entity")
    void testCreateIncidentWithIdempotencyKey_RepeatedRequestReturnsCachedResponse() throws Exception {
        IncidentCreateRequest req = new IncidentCreateRequest(
                buildingA.getId(),
                elevatorA1.getId(),
                noiseCategory.getId(),
                "Intermittent squeak when approaching 3rd floor",
                SeverityLevel.MEDIUM,
                false
        );

        String idempotencyKey = "client-uuid-001";

        // First request
        MvcResult firstResult = mockMvc.perform(post("/api/incidents")
                        .header("Authorization", "Bearer " + aliceToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Idempotency-Cache", "MISS"))
                .andExpect(jsonPath("$.ticketNumber").exists())
                .andExpect(jsonPath("$.status").value("REPORTED"))
                .andReturn();

        String firstResponseContent = firstResult.getResponse().getContentAsString();
        assertEquals(1, incidentRepository.count(), "Exactly one incident must be created");

        // Second request with identical payload and identical key
        MvcResult secondResult = mockMvc.perform(post("/api/incidents")
                        .header("Authorization", "Bearer " + aliceToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Idempotency-Cache", "HIT"))
                .andReturn();

        String secondResponseContent = secondResult.getResponse().getContentAsString();
        assertEquals(firstResponseContent, secondResponseContent, "Cached response body must match first response");
        assertEquals(1, incidentRepository.count(), "Incident count must remain 1 after repeated idempotent request");
        assertEquals(1, idempotencyRecordRepository.count(), "Exactly one idempotency record must exist");
    }

    @Test
    @DisplayName("Idempotency: Reusing key with different request payload returns 400 Bad Request")
    void testCreateIncidentWithIdempotencyKey_ModifiedPayloadFailsWithBadRequest() throws Exception {
        IncidentCreateRequest req1 = new IncidentCreateRequest(
                buildingA.getId(),
                elevatorA1.getId(),
                noiseCategory.getId(),
                "Squeaking noise",
                SeverityLevel.MEDIUM,
                false
        );

        String idempotencyKey = "client-uuid-002";

        // First request succeeds
        mockMvc.perform(post("/api/incidents")
                        .header("Authorization", "Bearer " + aliceToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        // Second request with SAME key but DIFFERENT description
        IncidentCreateRequest req2 = new IncidentCreateRequest(
                buildingA.getId(),
                elevatorA1.getId(),
                noiseCategory.getId(),
                "Completely different description to trigger mismatch",
                SeverityLevel.MEDIUM,
                false
        );

        mockMvc.perform(post("/api/incidents")
                        .header("Authorization", "Bearer " + aliceToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("different request payload")));

        assertEquals(1, incidentRepository.count());
    }

    @Test
    @DisplayName("Idempotency: Repeated POST /api/incidents/{id}/notes returns cached note without duplicate history entries")
    void testAddIncidentNoteWithIdempotencyKey_PreventsDuplicateNotes() throws Exception {
        // Create an incident first
        Incident incident = new Incident(
                "INC-TEST-001",
                buildingA,
                elevatorA1,
                residentAlice,
                noiseCategory,
                "Test noise incident",
                SeverityLevel.MEDIUM,
                false
        );
        incident = incidentRepository.save(incident);

        IncidentNoteRequest noteReq = new IncidentNoteRequest(
                "Inspected motor housing; slight vibration detected.",
                "WORK_LOG"
        );

        String idempotencyKey = "note-uuid-101";

        // First call
        mockMvc.perform(post("/api/incidents/" + incident.getId() + "/notes")
                        .header("Authorization", "Bearer " + carlosToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noteReq)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Idempotency-Cache", "MISS"))
                .andExpect(jsonPath("$.action").value("WORK_NOTE_ADDED"));

        int historyCountAfterFirst = incidentHistoryRepository.findByIncidentIdOrderByChangedAtDesc(incident.getId()).size();

        // Second call with same idempotency key
        mockMvc.perform(post("/api/incidents/" + incident.getId() + "/notes")
                        .header("Authorization", "Bearer " + carlosToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noteReq)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Idempotency-Cache", "HIT"));

        int historyCountAfterSecond = incidentHistoryRepository.findByIncidentIdOrderByChangedAtDesc(incident.getId()).size();
        assertEquals(historyCountAfterFirst, historyCountAfterSecond, "History entries must not duplicate on repeated note call");
    }

    @Test
    @DisplayName("Outbox: Critical entrapment incident enqueues urgent safety alert in durable outbox")
    void testNotificationOutboxCreated_OnEntrapmentIncident() throws Exception {
        IncidentCreateRequest req = new IncidentCreateRequest(
                buildingA.getId(),
                elevatorA1.getId(),
                entrapmentCategory.getId(),
                "Two passengers trapped between 4th and 5th floors!",
                SeverityLevel.CRITICAL,
                true
        );

        mockMvc.perform(post("/api/incidents")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REPORTED"))
                .andExpect(jsonPath("$.severity").value("CRITICAL"));

        List<NotificationOutbox> outboxItems = notificationOutboxRepository.findAll();
        assertFalse(outboxItems.isEmpty(), "Outbox must have entries recorded");

        // Verify there is an urgent safety alert for the entrapment
        boolean hasSafetyAlert = outboxItems.stream().anyMatch(n ->
                n.getEventType().equals("CRITICAL_ENTRAPMENT") &&
                n.getNotificationType() == NotificationType.SAFETY_ALERT &&
                n.getMessageBody().contains("Passenger entrapment reported"));

        assertTrue(hasSafetyAlert, "Outbox must include CRITICAL_ENTRAPMENT safety alert");
    }

    @Test
    @DisplayName("Outbox: Assigning technician enqueues DISPATCH_NOTICE for the technician")
    void testNotificationOutboxCreated_OnTechnicianAssignment() throws Exception {
        Incident incident = new Incident(
                "INC-TEST-002",
                buildingA,
                elevatorA1,
                residentAlice,
                noiseCategory,
                "Noise test",
                SeverityLevel.MEDIUM,
                false
        );
        incident = incidentRepository.save(incident);

        IncidentAssignRequest assignReq = new IncidentAssignRequest(technicianCarlos.getId(), "Please inspect bearings.");

        mockMvc.perform(patch("/api/incidents/" + incident.getId() + "/assign")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"));

        List<NotificationOutbox> outboxItems = notificationOutboxRepository.findAll();
        boolean hasDispatchNotice = outboxItems.stream().anyMatch(n ->
                n.getEventType().equals("INCIDENT_ASSIGNED") &&
                n.getNotificationType() == NotificationType.DISPATCH_NOTICE &&
                n.getRecipientUser() != null &&
                n.getRecipientUser().getId().equals(technicianCarlos.getId()));

        assertTrue(hasDispatchNotice, "Outbox must record DISPATCH_NOTICE directed to assigned technician");
    }

    @Test
    @DisplayName("In-App Notifications: /api/notifications/my and /api/notifications/{id}/read")
    void testMyNotificationsAndMarkAsRead() throws Exception {
        // Create an in-app notification directly for Alice
        NotificationOutbox notif = new NotificationOutbox(
                null,
                residentAlice,
                "RESIDENT",
                residentAlice.getPhoneNumber(),
                NotificationType.IN_APP,
                "INCIDENT_CREATED",
                "Your incident report has been received.",
                "INC-001: Elevator noise report submitted.",
                "dedup-my-test-01"
        );
        notif.setDeliveryState(NotificationDeliveryState.SENT);
        notif.setDeliveredAt(Instant.now());
        notif = notificationOutboxRepository.save(notif);

        // Fetch Alice's notifications
        mockMvc.perform(get("/api/notifications/my")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(notif.getId()))
                .andExpect(jsonPath("$[0].read").value(false));

        // Mark as read
        mockMvc.perform(patch("/api/notifications/" + notif.getId() + "/read")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(notif.getId()))
                .andExpect(jsonPath("$.read").value(true));

        // Verify status in DB
        NotificationOutbox updated = notificationOutboxRepository.findById(notif.getId()).orElseThrow();
        assertNotNull(updated.getReadAt());
    }

    @Test
    @DisplayName("Outbox Inspection: Role-based permissions for /api/notifications/outbox")
    void testOutboxInspectionPermissions() throws Exception {
        // RESIDENT should be forbidden (403)
        mockMvc.perform(get("/api/notifications/outbox")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isForbidden());

        // BUILDING_MANAGER should have access (200)
        mockMvc.perform(get("/api/notifications/outbox")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNotifications").isNumber())
                .andExpect(jsonPath("$.pendingCount").isNumber());

        // Paginated items check
        mockMvc.perform(get("/api/notifications/outbox/items")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }
}
