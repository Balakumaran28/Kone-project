package com.liftcare;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liftcare.dto.*;
import com.liftcare.model.*;
import com.liftcare.repository.*;
import com.liftcare.security.JwtService;
import com.liftcare.service.CloudRegistrationService;
import com.liftcare.service.LocalCloudSyncClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class Module12CloudSynchronizationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtService jwtService;

    @Autowired private UserRepository userRepository;
    @Autowired private BuildingRepository buildingRepository;
    @Autowired private ElevatorRepository elevatorRepository;
    @Autowired private IssueCategoryRepository issueCategoryRepository;
    @Autowired private IncidentRepository incidentRepository;
    @Autowired private IncidentHistoryRepository incidentHistoryRepository;
    @Autowired private NotificationOutboxRepository notificationOutboxRepository;
    @Autowired private IdempotencyRecordRepository idempotencyRecordRepository;
    @Autowired private CloudRegisteredBuildingRepository cloudRegisteredBuildingRepository;
    @Autowired private CloudSyncOutboxRepository cloudSyncOutboxRepository;
    @Autowired private CloudSyncCheckpointRepository cloudSyncCheckpointRepository;
    @Autowired private CloudSyncEventRepository cloudSyncEventRepository;
    @Autowired private CloudIncidentReplicaRepository cloudIncidentReplicaRepository;
    @Autowired private CloudSyncConflictRepository cloudSyncConflictRepository;

    @Autowired private CloudRegistrationService cloudRegistrationService;
    @Autowired private LocalCloudSyncClientService localCloudSyncClientService;

    private User adminUser;
    private User supervisorUser;
    private User residentUser;
    private String adminToken;
    private String supervisorToken;
    private String residentToken;

    private Building localBuilding;
    private Elevator localElevator;
    private IssueCategory generalCategory;

    @BeforeEach
    void setUp() {
        // Clear sync and incident state in foreign key order
        notificationOutboxRepository.deleteAll();
        idempotencyRecordRepository.deleteAll();
        incidentHistoryRepository.deleteAll();
        cloudSyncConflictRepository.deleteAll();
        cloudIncidentReplicaRepository.deleteAll();
        cloudSyncEventRepository.deleteAll();
        cloudSyncOutboxRepository.deleteAll();
        cloudSyncCheckpointRepository.deleteAll();
        cloudRegisteredBuildingRepository.deleteAll();
        incidentRepository.deleteAll();
        elevatorRepository.deleteAll();
        buildingRepository.deleteAll();
        userRepository.deleteAll();

        // Reset simulation toggle to online
        localCloudSyncClientService.setSimulatedCloudOffline(false);

        // Seed users
        adminUser = new User("System Administrator", "admin@liftcare.com", "+1-555-0100", UserRole.ADMINISTRATOR, "$2a$10$hashedAdminPassword");
        adminUser.setActive(true);
        adminUser = userRepository.save(adminUser);
        adminToken = jwtService.generateToken(adminUser);

        supervisorUser = new User("Central Supervisor", "supervisor@liftcare.com", "+1-555-0101", UserRole.COMPANY_SUPERVISOR, "$2a$10$hashedSupervisorPassword");
        supervisorUser.setActive(true);
        supervisorUser = userRepository.save(supervisorUser);
        supervisorToken = jwtService.generateToken(supervisorUser);

        residentUser = new User("Resident John", "resident@liftcare.com", "+1-555-0102", UserRole.RESIDENT, "$2a$10$hashedResidentPassword");
        residentUser.setActive(true);
        residentUser = userRepository.save(residentUser);
        residentToken = jwtService.generateToken(residentUser);

        // Seed local building & elevator
        localBuilding = new Building("BLD-A", "Tower Alpha", "100 Main St", "Metropolis", "node-01");
        localBuilding.setActive(true);
        localBuilding = buildingRepository.save(localBuilding);

        localElevator = new Elevator("LIFT-01", localBuilding, "Main Shaft", ElevatorOperationalStatus.ACTIVE);
        localElevator = elevatorRepository.save(localElevator);

        generalCategory = issueCategoryRepository.findAll().stream().findFirst().orElseGet(() ->
                issueCategoryRepository.save(new IssueCategory("GENERAL", "General Issue", "General elevator issue", SeverityLevel.MEDIUM, false)));
    }

    @Test
    @DisplayName("12.A Building Registration & Credential Validation")
    void testBuildingRegistrationAndCredentialValidation() throws Exception {
        BuildingRegistrationRequest regReq = new BuildingRegistrationRequest("BLD-A", "Tower Alpha", null);

        // 1. Central Admin registers building
        String response = mockMvc.perform(post("/api/cloud/buildings/register")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.buildingCode", is("BLD-A")))
                .andExpect(jsonPath("$.buildingName", is("Tower Alpha")))
                .andExpect(jsonPath("$.apiKey", startsWith("lc_bld_")))
                .andReturn().getResponse().getContentAsString();

        BuildingRegistrationResponse regResponse = objectMapper.readValue(response, BuildingRegistrationResponse.class);
        String rawApiKey = regResponse.apiKey();

        // 2. Validate credentials via service
        assertTrue(cloudRegistrationService.validateBuildingCredentials("BLD-A", rawApiKey));
        assertFalse(cloudRegistrationService.validateBuildingCredentials("BLD-A", "invalid_api_key"));
        assertFalse(cloudRegistrationService.validateBuildingCredentials("BLD-NON-EXISTENT", rawApiKey));

        // 3. Duplicate registration is rejected
        mockMvc.perform(post("/api/cloud/buildings/register")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("12.B Multi-Building Tenant Isolation & Header-Payload Mismatch Rejection")
    void testMultiBuildingTenantIsolation() throws Exception {
        // Register BLD-A
        BuildingRegistrationResponse regA = cloudRegistrationService.registerBuilding(
                new BuildingRegistrationRequest("BLD-A", "Tower Alpha", "lc_bld_test_key_alpha_12345678"));

        // 1. Attempt batch sync with mismatched payload (Header is BLD-A, Body is BLD-B)
        SyncEventItemDto eventItem = new SyncEventItemDto(
                UUID.randomUUID().toString(), "INCIDENT_CREATED", "INCIDENT", "INC-01",
                "{}", "key-01"
        );
        CloudSyncBatchRequest mismatchedBatch = new CloudSyncBatchRequest(
                "BLD-B", UUID.randomUUID().toString(), List.of(eventItem), Instant.now());

        mockMvc.perform(post("/api/cloud/sync/batch")
                        .header("X-Building-Code", "BLD-A")
                        .header("X-Building-Api-Key", "lc_bld_test_key_alpha_12345678")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mismatchedBatch)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Tenant isolation violation")));

        // 2. Attempt sync with invalid credentials
        CloudSyncBatchRequest validBatch = new CloudSyncBatchRequest(
                "BLD-A", UUID.randomUUID().toString(), List.of(eventItem), Instant.now());

        mockMvc.perform(post("/api/cloud/sync/batch")
                        .header("X-Building-Code", "BLD-A")
                        .header("X-Building-Api-Key", "wrong_credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validBatch)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("12.C Local Outbox Capture on Incident Creation & Status Changes")
    void testLocalOutboxCaptureOnLocalMutations() throws Exception {
        // Report incident locally
        IncidentCreateRequest req = new IncidentCreateRequest(
                localBuilding.getId(), localElevator.getId(), generalCategory.getId(),
                "Strange vibration on elevator start", SeverityLevel.MEDIUM, false
        );

        mockMvc.perform(post("/api/incidents")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // Verify local cloud sync outbox queued an event
        List<CloudSyncOutbox> outboxItems = cloudSyncOutboxRepository.findByBuildingCodeAndStatusOrderByCreatedAtAsc("BLD-A", "PENDING");
        assertEquals(1, outboxItems.size());
        CloudSyncOutbox item = outboxItems.get(0);
        assertEquals("INCIDENT_CREATED", item.getEventType());
        assertEquals("INCIDENT", item.getAggregateType());
        assertTrue(item.getPayload().contains("Strange vibration on elevator start"));
    }

    @Test
    @DisplayName("12.D Batch Ingestion, Idempotent Deduplication & Central Incident Replicas")
    void testBatchIngestionAndIdempotentDeduplication() throws Exception {
        // Register BLD-A
        cloudRegistrationService.registerBuilding(
                new BuildingRegistrationRequest("BLD-A", "Tower Alpha", "lc_bld_secret_batch_sync_key"));

        // Report incident to generate outbox event
        IncidentCreateRequest req = new IncidentCreateRequest(
                localBuilding.getId(), localElevator.getId(), generalCategory.getId(),
                "Door squeak on floor 4", SeverityLevel.LOW, false
        );

        mockMvc.perform(post("/api/incidents")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // 1. Local sync runner triggers sync
        CloudSyncBatchResponse batchRes1 = localCloudSyncClientService.triggerLocalSync("BLD-A");
        assertEquals("SUCCESS", batchRes1.status());
        assertEquals(1, batchRes1.processedCount());
        assertEquals(0, batchRes1.duplicateCount());

        // Verify outbox item marked SYNCED
        List<CloudSyncOutbox> pendingAfter = cloudSyncOutboxRepository.findByBuildingCodeAndStatusOrderByCreatedAtAsc("BLD-A", "PENDING");
        assertTrue(pendingAfter.isEmpty());
        List<CloudSyncOutbox> syncedAfter = cloudSyncOutboxRepository.findByBuildingCodeAndStatusOrderByCreatedAtAsc("BLD-A", "SYNCED");
        assertEquals(1, syncedAfter.size());

        // Verify central replica created
        List<CloudIncidentReplica> replicas = cloudIncidentReplicaRepository.findByBuildingCodeOrderByReportedAtDesc("BLD-A");
        assertEquals(1, replicas.size());
        assertEquals("Door squeak on floor 4", replicas.get(0).getDescription());

        // 2. Replay duplicate batch directly to central endpoint
        CloudSyncOutbox syncedItem = syncedAfter.get(0);
        SyncEventItemDto duplicateItem = new SyncEventItemDto(
                syncedItem.getEventId(), syncedItem.getEventType(), syncedItem.getAggregateType(),
                syncedItem.getAggregateId(), syncedItem.getPayload(), syncedItem.getIdempotencyKey());

        CloudSyncBatchRequest replayedBatch = new CloudSyncBatchRequest(
                "BLD-A", UUID.randomUUID().toString(), List.of(duplicateItem), Instant.now());

        mockMvc.perform(post("/api/cloud/sync/batch")
                        .header("X-Building-Code", "BLD-A")
                        .header("X-Building-Api-Key", "lc_bld_secret_batch_sync_key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(replayedBatch)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processedCount", is(0)))
                .andExpect(jsonPath("$.duplicateCount", is(1)));

        // Verify no duplicate replica rows created
        assertEquals(1, cloudIncidentReplicaRepository.findByBuildingCodeOrderByReportedAtDesc("BLD-A").size());
    }

    @Test
    @DisplayName("12.E Conflict Detection on Closed Ticket Regressions & Conflict Resolution")
    void testConflictDetectionAndResolution() throws Exception {
        cloudRegistrationService.registerBuilding(
                new BuildingRegistrationRequest("BLD-A", "Tower Alpha", "lc_bld_conflict_test_key"));

        // Setup a closed replica in central cloud
        CloudIncidentReplica replica = new CloudIncidentReplica();
        replica.setTicketNumber("INC-CONFLICT-01");
        replica.setBuildingCode("BLD-A");
        replica.setElevatorCode("LIFT-01");
        replica.setCategoryName("General");
        replica.setSeverity("MEDIUM");
        replica.setStatus("CLOSED");
        replica.setDescription("Closed maintenance issue");
        replica.setReportedAt(Instant.now().minusSeconds(3600));
        replica.setClosedAt(Instant.now().minusSeconds(100));
        replica.setLocalVersion(5);
        cloudIncidentReplicaRepository.save(replica);

        // Incoming stale sync event attempting to set status back to REPORTED
        String payloadJson = "{\"ticketNumber\":\"INC-CONFLICT-01\",\"buildingCode\":\"BLD-A\",\"status\":\"REPORTED\",\"severity\":\"MEDIUM\",\"description\":\"Stale update\"}";
        SyncEventItemDto staleEvent = new SyncEventItemDto(
                UUID.randomUUID().toString(), "INCIDENT_UPDATED", "INCIDENT", "INC-CONFLICT-01",
                payloadJson, "key-stale-01");

        CloudSyncBatchRequest batchReq = new CloudSyncBatchRequest(
                "BLD-A", UUID.randomUUID().toString(), List.of(staleEvent), Instant.now());

        // Ingest batch
        mockMvc.perform(post("/api/cloud/sync/batch")
                        .header("X-Building-Code", "BLD-A")
                        .header("X-Building-Api-Key", "lc_bld_conflict_test_key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batchReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conflictCount", is(1)));

        // Verify conflict recorded in central review repository
        List<CloudSyncConflict> conflicts = cloudSyncConflictRepository.findByBuildingCodeOrderByCreatedAtDesc("BLD-A");
        assertEquals(1, conflicts.size());
        CloudSyncConflict conflict = conflicts.get(0);
        assertEquals("INC-CONFLICT-01", conflict.getTicketNumber());
        assertEquals("STALE_STATUS_ON_CLOSED_TICKET", conflict.getConflictType());
        assertEquals("PENDING_REVIEW", conflict.getResolutionAction());

        // Resolve conflict via central API
        ConflictResolutionRequest resReq = new ConflictResolutionRequest(
                "ACCEPTED_LOCAL", "Reviewed by central supervisor: local technician notes confirmed reopen");

        mockMvc.perform(patch("/api/cloud/conflicts/" + conflict.getConflictId() + "/resolve")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolutionAction", is("ACCEPTED_LOCAL")))
                .andExpect(jsonPath("$.resolvedBy", is(supervisorUser.getEmail())));
    }

    @Test
    @DisplayName("12.F Cloud Outage Resilience: Local Operations Continue Unaffected & Resume on Recovery")
    void testCloudOutageLocalOperationContinuityAndRecovery() throws Exception {
        cloudRegistrationService.registerBuilding(
                new BuildingRegistrationRequest("BLD-A", "Tower Alpha", "lc_bld_outage_test_key"));

        // 1. Simulate Cloud Server Disconnect
        localCloudSyncClientService.setSimulatedCloudOffline(true);

        // 2. Report incident locally while cloud is OFFLINE
        IncidentCreateRequest req = new IncidentCreateRequest(
                localBuilding.getId(), localElevator.getId(), generalCategory.getId(),
                "Critical fault reported while cloud offline", SeverityLevel.CRITICAL, true
        );

        String incResStr = mockMvc.perform(post("/api/incidents")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.severity", is("CRITICAL")))
                .andExpect(jsonPath("$.entrapmentReported", is(true)))
                .andReturn().getResponse().getContentAsString();

        Long incidentId = objectMapper.readTree(incResStr).get("id").asLong();

        // Verify elevator transitioned to OUT_OF_SERVICE locally despite cloud outage
        Elevator elevator = elevatorRepository.findById(localElevator.getId()).orElseThrow();
        assertEquals(ElevatorOperationalStatus.OUT_OF_SERVICE, elevator.getOperationalStatus());

        // 3. Local sync attempt while offline -> Gracefully returns OFFLINE, local outbox items remain PENDING
        CloudSyncBatchResponse offlineBatch = localCloudSyncClientService.triggerLocalSync("BLD-A");
        assertEquals("OFFLINE", offlineBatch.status());

        LocalSyncStatusResponse localStatus = localCloudSyncClientService.getLocalSyncStatus("BLD-A");
        assertEquals("OFFLINE", localStatus.cloudConnectionStatus());
        assertTrue(localStatus.localOperationsAvailable(), "Local operations must always remain available");
        assertTrue(localStatus.pendingEventsCount() > 0, "Outbox events must remain pending in durable storage");

        // 4. Restore Cloud Connectivity
        localCloudSyncClientService.setSimulatedCloudOffline(false);

        // 5. Trigger sync -> Pending events are drained and marked SYNCED
        CloudSyncBatchResponse recoveredBatch = localCloudSyncClientService.triggerLocalSync("BLD-A");
        assertEquals("SUCCESS", recoveredBatch.status());
        assertTrue(recoveredBatch.processedCount() > 0);

        LocalSyncStatusResponse recoveredStatus = localCloudSyncClientService.getLocalSyncStatus("BLD-A");
        assertEquals("ONLINE", recoveredStatus.cloudConnectionStatus());
        assertEquals(0, recoveredStatus.pendingEventsCount());

        // Verify replica in cloud
        List<CloudIncidentReplica> replicas = cloudIncidentReplicaRepository.findByBuildingCodeOrderByReportedAtDesc("BLD-A");
        assertFalse(replicas.isEmpty());
        assertTrue(replicas.stream().anyMatch(r -> r.isEntrapmentReported() && "CRITICAL".equals(r.getSeverity())));
    }

    @Test
    @DisplayName("12.G Central Dashboard Aggregation & Role-Based Authorization")
    void testCentralDashboardAggregationAndPermissions() throws Exception {
        cloudRegistrationService.registerBuilding(
                new BuildingRegistrationRequest("BLD-A", "Tower Alpha", "key-a"));
        cloudRegistrationService.registerBuilding(
                new BuildingRegistrationRequest("BLD-B", "Tower Beta", "key-b"));

        // Seed replicas
        CloudIncidentReplica r1 = new CloudIncidentReplica();
        r1.setTicketNumber("INC-BLDA-01");
        r1.setBuildingCode("BLD-A");
        r1.setBuildingName("Tower Alpha");
        r1.setElevatorCode("LIFT-01");
        r1.setCategoryName("Electrical");
        r1.setSeverity("CRITICAL");
        r1.setStatus("REPORTED");
        r1.setEntrapmentReported(true);
        r1.setDescription("Power outage");
        r1.setReportedAt(Instant.now());
        cloudIncidentReplicaRepository.save(r1);

        CloudIncidentReplica r2 = new CloudIncidentReplica();
        r2.setTicketNumber("INC-BLDB-01");
        r2.setBuildingCode("BLD-B");
        r2.setBuildingName("Tower Beta");
        r2.setElevatorCode("LIFT-01");
        r2.setCategoryName("Noise");
        r2.setSeverity("LOW");
        r2.setStatus("RESOLVED");
        r2.setDescription("Rattle fixed");
        r2.setReportedAt(Instant.now());
        cloudIncidentReplicaRepository.save(r2);

        // 1. Central Supervisor can access aggregated overview
        mockMvc.perform(get("/api/cloud/dashboard/summary")
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRegisteredBuildings", is(2)))
                .andExpect(jsonPath("$.totalIncidents", is(2)))
                .andExpect(jsonPath("$.criticalIncidents", is(1)))
                .andExpect(jsonPath("$.entrapmentsReported", is(1)));

        // 2. Filter incidents by building
        mockMvc.perform(get("/api/cloud/incidents?buildingCode=BLD-A")
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].ticketNumber", is("INC-BLDA-01")));

        // 3. Resident is denied access to central fleet dashboard
        mockMvc.perform(get("/api/cloud/dashboard/summary")
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("12.H Failed Outbox Records Automatic Retry and Recovery")
    void testFailedOutboxEventAutomaticRetryRecovery() throws Exception {
        cloudRegistrationService.registerBuilding(
                new BuildingRegistrationRequest("BLD-A", "Tower Alpha", "lc_bld_retry_test_key"));

        // Seed an outbox item that previously failed with status FAILED and retryCount 1
        String payload = "{\"ticketNumber\":\"INC-RETRY-01\",\"buildingCode\":\"BLD-A\",\"status\":\"REPORTED\",\"severity\":\"LOW\",\"description\":\"Retryable event\"}";
        CloudSyncOutbox failedOutbox = new CloudSyncOutbox(
                UUID.randomUUID().toString(), "BLD-A", "INCIDENT_CREATED", "INCIDENT", "INC-RETRY-01",
                payload, "SYNC-INC-RETRY-01");
        failedOutbox.setStatus("FAILED");
        failedOutbox.setRetryCount(1);
        failedOutbox.setErrorMessage("Connection refused on previous attempt");
        cloudSyncOutboxRepository.save(failedOutbox);

        // Verify outbox currently has 1 failed record
        assertEquals(1, cloudSyncOutboxRepository.countByBuildingCodeAndStatus("BLD-A", "FAILED"));

        // Trigger local sync now that cloud connection is operational
        CloudSyncBatchResponse res = localCloudSyncClientService.triggerLocalSync("BLD-A");
        assertEquals("SUCCESS", res.status());
        assertEquals(1, res.processedCount());

        // Verify the failed item was successfully retried, transitioned to SYNCED, and error cleared
        CloudSyncOutbox retriedItem = cloudSyncOutboxRepository.findByIdempotencyKey("SYNC-INC-RETRY-01").orElseThrow();
        assertEquals("SYNCED", retriedItem.getStatus());
        assertNull(retriedItem.getErrorMessage());
        assertNotNull(retriedItem.getSyncedAt());
    }
}
