package com.liftcare;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class DashboardControllerTest {

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
    private User securityStaff;
    private User manager;
    private User technician;
    private User supervisor;
    private User admin;

    private String residentToken;
    private String securityToken;
    private String managerToken;
    private String technicianToken;
    private String supervisorToken;
    private String adminToken;

    private Building buildingA;
    private Building buildingB;
    private Elevator elevator1;
    private Elevator elevator2;
    private Elevator elevator3;
    private IssueCategory entrapmentCategory;
    private IssueCategory noiseCategory;

    @BeforeEach
    void setUp() {
        incidentHistoryRepository.deleteAll();
        incidentRepository.deleteAll();
        elevatorRepository.deleteAll();
        buildingRepository.deleteAll();
        issueCategoryRepository.deleteAll();
        userRepository.deleteAll();

        String suffix = UUID.randomUUID().toString().substring(0, 8);

        resident = userRepository.save(new User(
                "Resident Jane",
                "jane." + suffix + "@liftcare.local",
                "+1-555-0101",
                UserRole.RESIDENT,
                passwordEncoder.encode("Secret123!")
        ));
        residentToken = jwtService.generateToken(resident);

        securityStaff = userRepository.save(new User(
                "Security Bob",
                "bob." + suffix + "@liftcare.local",
                "+1-555-0102",
                UserRole.SECURITY_STAFF,
                passwordEncoder.encode("Secret123!")
        ));
        securityToken = jwtService.generateToken(securityStaff);

        manager = userRepository.save(new User(
                "Manager Alice",
                "alice." + suffix + "@liftcare.local",
                "+1-555-0103",
                UserRole.BUILDING_MANAGER,
                passwordEncoder.encode("Secret123!")
        ));
        managerToken = jwtService.generateToken(manager);

        technician = userRepository.save(new User(
                "Tech Dave",
                "dave." + suffix + "@liftcare.local",
                "+1-555-0104",
                UserRole.TECHNICIAN,
                passwordEncoder.encode("Secret123!")
        ));
        technicianToken = jwtService.generateToken(technician);

        supervisor = userRepository.save(new User(
                "Supervisor Sam",
                "sam." + suffix + "@liftcare.local",
                "+1-555-0105",
                UserRole.COMPANY_SUPERVISOR,
                passwordEncoder.encode("Secret123!")
        ));
        supervisorToken = jwtService.generateToken(supervisor);

        admin = userRepository.save(new User(
                "Admin Alex",
                "admin." + suffix + "@liftcare.local",
                "+1-555-0106",
                UserRole.ADMINISTRATOR,
                passwordEncoder.encode("Secret123!")
        ));
        adminToken = jwtService.generateToken(admin);

        buildingA = buildingRepository.save(new Building(
                "BLD-A-" + suffix,
                "Tower Alpha",
                "100 North Way",
                "Metro City",
                "node-alpha"
        ));

        buildingB = buildingRepository.save(new Building(
                "BLD-B-" + suffix,
                "Tower Beta",
                "200 South Way",
                "Metro City",
                "node-beta"
        ));

        elevator1 = elevatorRepository.save(new Elevator(
                "ELEV-01",
                buildingA,
                "Main Lobby",
                ElevatorOperationalStatus.ACTIVE
        ));

        elevator2 = elevatorRepository.save(new Elevator(
                "ELEV-02",
                buildingA,
                "Service Bay",
                ElevatorOperationalStatus.OUT_OF_SERVICE
        ));

        elevator3 = elevatorRepository.save(new Elevator(
                "ELEV-03",
                buildingB,
                "Beta Lobby",
                ElevatorOperationalStatus.MAINTENANCE
        ));

        entrapmentCategory = issueCategoryRepository.save(new IssueCategory(
                "PASSENGER_ENTRAPMENT",
                "Passenger Entrapment",
                "Passengers trapped inside cabin",
                SeverityLevel.CRITICAL,
                true
        ));

        noiseCategory = issueCategoryRepository.save(new IssueCategory(
                "MOTOR_NOISE",
                "Unusual Motor Noise",
                "High-pitch grinding from machine room",
                SeverityLevel.MEDIUM,
                false
        ));
    }

    @Test
    @DisplayName("GET /api/dashboard/summary without token returns 401 Unauthorized")
    void getDashboardSummary_unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/dashboard/summary as RESIDENT returns 403 Forbidden")
    void getDashboardSummary_asResident_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary")
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/dashboard/summary as SECURITY_STAFF returns correct calculated counts and emergency queue")
    void getDashboardSummary_asSecurityStaff_returnsCorrectCounts() throws Exception {
        // Setup 4 active incidents and 2 completed incidents:
        // 1. REPORTED (Awaiting Ack) - CRITICAL with Entrapment
        Incident inc1 = new Incident(
                "INC-TEST-001",
                buildingA,
                elevator1,
                resident,
                entrapmentCategory,
                "People stuck between floors 4 and 5",
                SeverityLevel.CRITICAL,
                true
        );
        inc1.setStatus(IncidentStatus.REPORTED);
        incidentRepository.save(inc1);

        // 2. ACKNOWLEDGED - HIGH severity
        Incident inc2 = new Incident(
                "INC-TEST-002",
                buildingA,
                elevator1,
                resident,
                noiseCategory,
                "Severe grinding noise during motion",
                SeverityLevel.HIGH,
                false
        );
        inc2.setStatus(IncidentStatus.ACKNOWLEDGED);
        incidentRepository.save(inc2);

        // 3. ASSIGNED - MEDIUM severity
        Incident inc3 = new Incident(
                "INC-TEST-003",
                buildingA,
                elevator2,
                resident,
                noiseCategory,
                "Door sticking on 2nd floor",
                SeverityLevel.MEDIUM,
                false
        );
        inc3.setStatus(IncidentStatus.ASSIGNED);
        inc3.setAssignedTo(technician);
        incidentRepository.save(inc3);

        // 4. IN_PROGRESS - LOW severity
        Incident inc4 = new Incident(
                "INC-TEST-004",
                buildingB,
                elevator3,
                resident,
                noiseCategory,
                "Display lamp flickering",
                SeverityLevel.LOW,
                false
        );
        inc4.setStatus(IncidentStatus.IN_PROGRESS);
        inc4.setAssignedTo(technician);
        incidentRepository.save(inc4);

        // 5. RESOLVED - should not be included in active counts
        Incident inc5 = new Incident(
                "INC-TEST-005",
                buildingA,
                elevator1,
                resident,
                noiseCategory,
                "Resolved issue",
                SeverityLevel.LOW,
                false
        );
        inc5.setStatus(IncidentStatus.RESOLVED);
        inc5.setResolvedAt(Instant.now());
        incidentRepository.save(inc5);

        // 6. CLOSED - should not be included in active counts
        Incident inc6 = new Incident(
                "INC-TEST-006",
                buildingA,
                elevator1,
                resident,
                noiseCategory,
                "Closed issue",
                SeverityLevel.LOW,
                false
        );
        inc6.setStatus(IncidentStatus.CLOSED);
        incidentRepository.save(inc6);

        mockMvc.perform(get("/api/dashboard/summary")
                        .header("Authorization", "Bearer " + securityToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalActiveIncidents", is(4)))
                .andExpect(jsonPath("$.criticalIncidents", is(1)))
                .andExpect(jsonPath("$.highSeverityIncidents", is(1)))
                .andExpect(jsonPath("$.awaitingAcknowledgementIncidents", is(1)))
                .andExpect(jsonPath("$.assignedIncidents", is(1)))
                .andExpect(jsonPath("$.inProgressIncidents", is(1)))
                .andExpect(jsonPath("$.activeEntrapments", is(1)))
                .andExpect(jsonPath("$.elevatorsOutOfService", is(1)))
                .andExpect(jsonPath("$.elevatorsUnderMaintenance", is(1)))
                .andExpect(jsonPath("$.elevatorsActive", is(1)))
                .andExpect(jsonPath("$.totalElevators", is(3)))
                .andExpect(jsonPath("$.totalBuildings", is(2)))
                .andExpect(jsonPath("$.emergencyIncidents", hasSize(1)))
                .andExpect(jsonPath("$.emergencyIncidents[0].ticketNumber", is("INC-TEST-001")))
                .andExpect(jsonPath("$.emergencyIncidents[0].entrapmentReported", is(true)));
    }

    @Test
    @DisplayName("GET /api/dashboard/summary as BUILDING_MANAGER returns 200 OK")
    void getDashboardSummary_asBuildingManager_returnsSuccess() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalActiveIncidents", is(0)))
                .andExpect(jsonPath("$.totalElevators", is(3)));
    }

    @Test
    @DisplayName("GET /api/dashboard/summary as TECHNICIAN returns 200 OK")
    void getDashboardSummary_asTechnician_returnsSuccess() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary")
                        .header("Authorization", "Bearer " + technicianToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalActiveIncidents", is(0)));
    }

    @Test
    @DisplayName("GET /api/dashboard/summary as COMPANY_SUPERVISOR returns 200 OK")
    void getDashboardSummary_asCompanySupervisor_returnsSuccess() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary")
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalActiveIncidents", is(0)));
    }

    @Test
    @DisplayName("GET /api/dashboard/summary as ADMINISTRATOR returns 200 OK")
    void getDashboardSummary_asAdministrator_returnsSuccess() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalActiveIncidents", is(0)));
    }

    @Test
    @DisplayName("GET /api/dashboard/summary with buildingId filter returns scoped metrics")
    void getDashboardSummary_withBuildingIdFilter_returnsScopedCounts() throws Exception {
        // Incident on buildingA
        Incident incA = new Incident(
                "INC-TEST-A",
                buildingA,
                elevator1,
                resident,
                entrapmentCategory,
                "Entrapment in building A",
                SeverityLevel.CRITICAL,
                true
        );
        incA.setStatus(IncidentStatus.REPORTED);
        incidentRepository.save(incA);

        // Incident on buildingB
        Incident incB = new Incident(
                "INC-TEST-B",
                buildingB,
                elevator3,
                resident,
                noiseCategory,
                "Noise in building B",
                SeverityLevel.MEDIUM,
                false
        );
        incB.setStatus(IncidentStatus.ASSIGNED);
        incidentRepository.save(incB);

        mockMvc.perform(get("/api/dashboard/summary?buildingId=" + buildingA.getId())
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalActiveIncidents", is(1)))
                .andExpect(jsonPath("$.criticalIncidents", is(1)))
                .andExpect(jsonPath("$.activeEntrapments", is(1)))
                .andExpect(jsonPath("$.totalElevators", is(2)))
                .andExpect(jsonPath("$.totalBuildings", is(1)))
                .andExpect(jsonPath("$.emergencyIncidents", hasSize(1)))
                .andExpect(jsonPath("$.emergencyIncidents[0].ticketNumber", is("INC-TEST-A")));
    }

    @Test
    @DisplayName("GET /api/dashboard/summary with non-existent buildingId returns 404 Not Found")
    void getDashboardSummary_withNonExistentBuilding_returnsNotFound() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary?buildingId=999999")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Building not found with id: 999999")));
    }

    @Test
    @DisplayName("GET /api/elevators returns all elevators across buildings")
    void getElevators_withoutFilter_returnsAllElevators() throws Exception {
        mockMvc.perform(get("/api/elevators")
                        .header("Authorization", "Bearer " + securityToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    @DisplayName("GET /api/elevators with status filter returns only matching operational status")
    void getElevators_withStatusFilter_returnsMatchingElevators() throws Exception {
        mockMvc.perform(get("/api/elevators?status=OUT_OF_SERVICE")
                        .header("Authorization", "Bearer " + securityToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].elevatorCode", is("ELEV-02")))
                .andExpect(jsonPath("$[0].operationalStatus", is("OUT_OF_SERVICE")));
    }

    @Test
    @DisplayName("GET /api/incidents with assignedToId returns only incidents assigned to specified technician")
    void getIncidents_filterByAssignedTechnician() throws Exception {
        Incident inc1 = new Incident(
                "INC-TEST-TECH1",
                buildingA,
                elevator1,
                resident,
                noiseCategory,
                "Assigned to Dave",
                SeverityLevel.MEDIUM,
                false
        );
        inc1.setStatus(IncidentStatus.ASSIGNED);
        inc1.setAssignedTo(technician);
        incidentRepository.save(inc1);

        Incident inc2 = new Incident(
                "INC-TEST-UNASSIGNED",
                buildingA,
                elevator1,
                resident,
                noiseCategory,
                "Unassigned ticket",
                SeverityLevel.LOW,
                false
        );
        inc2.setStatus(IncidentStatus.REPORTED);
        incidentRepository.save(inc2);

        mockMvc.perform(get("/api/incidents?assignedToId=" + technician.getId())
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].ticketNumber", is("INC-TEST-TECH1")))
                .andExpect(jsonPath("$[0].assignedToName", is("Tech Dave")));
    }
}
