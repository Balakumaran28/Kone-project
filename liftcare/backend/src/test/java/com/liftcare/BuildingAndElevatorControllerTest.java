package com.liftcare;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liftcare.dto.*;
import com.liftcare.model.Building;
import com.liftcare.model.Elevator;
import com.liftcare.model.ElevatorOperationalStatus;
import com.liftcare.model.User;
import com.liftcare.model.UserRole;
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

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class BuildingAndElevatorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private ElevatorRepository elevatorRepository;

    @Autowired
    private IncidentHistoryRepository incidentHistoryRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String managerToken;
    private String techToken;
    private String residentToken;
    private String supervisorToken;

    @BeforeEach
    void setUp() {
        incidentHistoryRepository.deleteAll();
        incidentRepository.deleteAll();
        elevatorRepository.deleteAll();
        buildingRepository.deleteAll();
        userRepository.deleteAll();

        User admin = createUser("admin@liftcare.local", UserRole.ADMINISTRATOR, "System Admin");
        User manager = createUser("manager@liftcare.local", UserRole.BUILDING_MANAGER, "Building Manager");
        User tech = createUser("tech@liftcare.local", UserRole.TECHNICIAN, "Elevator Tech");
        User resident = createUser("resident@liftcare.local", UserRole.RESIDENT, "John Resident");
        User supervisor = createUser("supervisor@liftcare.local", UserRole.COMPANY_SUPERVISOR, "Supervisor Sarah");

        adminToken = jwtService.generateToken(admin);
        managerToken = jwtService.generateToken(manager);
        techToken = jwtService.generateToken(tech);
        residentToken = jwtService.generateToken(resident);
        supervisorToken = jwtService.generateToken(supervisor);
    }

    private User createUser(String email, UserRole role, String name) {
        User user = new User(name, email, "+1-555-0100", role, passwordEncoder.encode("Password123!"));
        return userRepository.save(user);
    }

    // =========================================================================
    // PART A: BUILDING MANAGEMENT TESTS
    // =========================================================================

    @Test
    @DisplayName("1. Authorized building creation by ADMINISTRATOR")
    void testAuthorizedBuildingCreation() throws Exception {
        BuildingCreateRequest request = new BuildingCreateRequest(
                "BLDG-A", "Skyline Tower A", "100 Grand Ave", "Metropolis", "node-metropolis-01"
        );

        mockMvc.perform(post("/api/buildings")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.buildingCode", is("BLDG-A")))
                .andExpect(jsonPath("$.name", is("Skyline Tower A")))
                .andExpect(jsonPath("$.active", is(true)))
                .andExpect(jsonPath("$.elevatorCount", is(0)));
    }

    @Test
    @DisplayName("2. Rejection of unauthorized building creation (RESIDENT, TECHNICIAN, etc.)")
    void testUnauthorizedBuildingCreation() throws Exception {
        BuildingCreateRequest request = new BuildingCreateRequest(
                "BLDG-R", "Resident Tower", "200 Elm St", "Gotham", null
        );

        // RESIDENT should be 403 Forbidden
        mockMvc.perform(post("/api/buildings")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));

        // TECHNICIAN should be 403 Forbidden
        mockMvc.perform(post("/api/buildings")
                        .header("Authorization", "Bearer " + techToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("3. Listing buildings for authenticated users with elevator counts")
    void testListingBuildings() throws Exception {
        Building b1 = buildingRepository.save(new Building("B-01", "Tower One", "100 Main", "City", "node-1"));
        Building b2 = buildingRepository.save(new Building("B-02", "Tower Two", "200 Main", "City", "node-1"));

        // Add elevator to b1
        elevatorRepository.save(new Elevator("E-1", b1, "North", ElevatorOperationalStatus.ACTIVE));

        mockMvc.perform(get("/api/buildings")
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].buildingCode", is("B-01")))
                .andExpect(jsonPath("$[0].elevatorCount", is(1)))
                .andExpect(jsonPath("$[1].buildingCode", is("B-02")))
                .andExpect(jsonPath("$[1].elevatorCount", is(0)));
    }

    @Test
    @DisplayName("4. Retrieving a nonexistent building returns 404")
    void testGetNonexistentBuildingReturns404() throws Exception {
        mockMvc.perform(get("/api/buildings/99999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", containsString("Building not found with id: 99999")));
    }

    @Test
    @DisplayName("5. Updating building details by authorized roles")
    void testUpdateBuildingDetails() throws Exception {
        Building b = buildingRepository.save(new Building("BLDG-UPDATE", "Old Name", "Old Address", "Old City", "node-1"));

        BuildingUpdateRequest updateRequest = new BuildingUpdateRequest(
                "Updated Name", "New Address 123", "New City", "node-updated", true
        );

        // Building Manager is authorized to update building
        mockMvc.perform(put("/api/buildings/" + b.getId())
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Updated Name")))
                .andExpect(jsonPath("$.address", is("New Address 123")))
                .andExpect(jsonPath("$.localNodeId", is("node-updated")));

        // Resident attempting to update should be rejected (403)
        mockMvc.perform(put("/api/buildings/" + b.getId())
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("6. Duplicate building code rejection (409 Conflict)")
    void testDuplicateBuildingCodeRejection() throws Exception {
        buildingRepository.save(new Building("BLDG-DUP", "First Building", "Address 1", "City", null));

        BuildingCreateRequest duplicateRequest = new BuildingCreateRequest(
                "BLDG-DUP", "Second Building", "Address 2", "City", null
        );

        mockMvc.perform(post("/api/buildings")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", containsString("already exists")));
    }

    @Test
    @DisplayName("6b. Deactivating a building marks it inactive without deletion")
    void testDeactivateBuilding() throws Exception {
        Building b = buildingRepository.save(new Building("BLDG-DEACT", "Tower To Deactivate", "Address", "City", null));

        mockMvc.perform(patch("/api/buildings/" + b.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)));

        Building reloaded = buildingRepository.findById(b.getId()).orElseThrow();
        assertFalse(reloaded.isActive());
    }

    // =========================================================================
    // PART B: ELEVATOR MANAGEMENT TESTS
    // =========================================================================

    @Test
    @DisplayName("7. Creating an elevator under a valid building")
    void testCreateElevatorUnderValidBuilding() throws Exception {
        Building b = buildingRepository.save(new Building("BLDG-ELEV", "Elevator Building", "Address", "City", null));

        ElevatorCreateRequest request = new ElevatorCreateRequest(
                "LIFT-01", "West Wing Lobby", "Otis", "Gen2", "Otis Service Ltd", 15, ElevatorOperationalStatus.ACTIVE
        );

        mockMvc.perform(post("/api/buildings/" + b.getId() + "/elevators")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.elevatorCode", is("LIFT-01")))
                .andExpect(jsonPath("$.buildingId", is(b.getId().intValue())))
                .andExpect(jsonPath("$.buildingCode", is("BLDG-ELEV")))
                .andExpect(jsonPath("$.operationalStatus", is("ACTIVE")))
                .andExpect(jsonPath("$.totalFloors", is(15)));
    }

    @Test
    @DisplayName("8. Rejection when creating an elevator under a nonexistent building (404)")
    void testCreateElevatorNonexistentBuilding() throws Exception {
        ElevatorCreateRequest request = new ElevatorCreateRequest(
                "LIFT-99", "Lobby", "Kone", "MonoSpace", null, 10, ElevatorOperationalStatus.ACTIVE
        );

        mockMvc.perform(post("/api/buildings/88888/elevators")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", containsString("Building not found with id: 88888")));
    }

    @Test
    @DisplayName("9. Listing elevators for a building")
    void testListElevatorsForBuilding() throws Exception {
        Building b = buildingRepository.save(new Building("BLDG-LIST", "List Tower", "Address", "City", null));
        elevatorRepository.save(new Elevator("LIFT-A", b, "Shaft A", ElevatorOperationalStatus.ACTIVE));
        elevatorRepository.save(new Elevator("LIFT-B", b, "Shaft B", ElevatorOperationalStatus.MAINTENANCE));

        mockMvc.perform(get("/api/buildings/" + b.getId() + "/elevators")
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].elevatorCode", is("LIFT-A")))
                .andExpect(jsonPath("$[1].elevatorCode", is("LIFT-B")));
    }

    @Test
    @DisplayName("10. Duplicate elevator code within the same building rejected (409 Conflict)")
    void testDuplicateElevatorCodeWithinSameBuilding() throws Exception {
        Building b = buildingRepository.save(new Building("BLDG-SAME", "Same Building", "Address", "City", null));
        elevatorRepository.save(new Elevator("LIFT-DUP", b, "Shaft 1", ElevatorOperationalStatus.ACTIVE));

        ElevatorCreateRequest request = new ElevatorCreateRequest(
                "LIFT-DUP", "Shaft 2", "Schindler", null, null, 10, ElevatorOperationalStatus.ACTIVE
        );

        mockMvc.perform(post("/api/buildings/" + b.getId() + "/elevators")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", containsString("already exists in building")));
    }

    @Test
    @DisplayName("10b. Same elevator code in a DIFFERENT building is permitted")
    void testSameElevatorCodeDifferentBuildingPermitted() throws Exception {
        Building b1 = buildingRepository.save(new Building("BLDG-1", "Building 1", "Address 1", "City", null));
        Building b2 = buildingRepository.save(new Building("BLDG-2", "Building 2", "Address 2", "City", null));

        elevatorRepository.save(new Elevator("LIFT-01", b1, "Lobby 1", ElevatorOperationalStatus.ACTIVE));

        ElevatorCreateRequest request = new ElevatorCreateRequest(
                "LIFT-01", "Lobby 2", "Otis", null, null, 8, ElevatorOperationalStatus.ACTIVE
        );

        // Allowed because elevator codes are scoped to building
        mockMvc.perform(post("/api/buildings/" + b2.getId() + "/elevators")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.elevatorCode", is("LIFT-01")))
                .andExpect(jsonPath("$.buildingId", is(b2.getId().intValue())));
    }

    @Test
    @DisplayName("11. Updating elevator details and operational status")
    void testUpdatingElevatorDetailsAndStatus() throws Exception {
        Building b = buildingRepository.save(new Building("BLDG-MOD", "Mod Tower", "Address", "City", null));
        Elevator e = elevatorRepository.save(new Elevator("LIFT-ORIG", b, "Lobby", ElevatorOperationalStatus.ACTIVE));

        // 1. Update details via Manager
        ElevatorUpdateRequest updateRequest = new ElevatorUpdateRequest(
                "LIFT-RENAMED", "Floor 1-20 Express", "Kone", "UltraRope", "Kone Care", 25, ElevatorOperationalStatus.MAINTENANCE
        );

        mockMvc.perform(put("/api/elevators/" + e.getId())
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elevatorCode", is("LIFT-RENAMED")))
                .andExpect(jsonPath("$.manufacturer", is("Kone")))
                .andExpect(jsonPath("$.operationalStatus", is("MAINTENANCE")));

        // 2. Technician updating status to OUT_OF_SERVICE
        ElevatorStatusUpdateRequest statusRequest = new ElevatorStatusUpdateRequest(ElevatorOperationalStatus.OUT_OF_SERVICE);

        mockMvc.perform(patch("/api/elevators/" + e.getId() + "/status")
                        .header("Authorization", "Bearer " + techToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationalStatus", is("OUT_OF_SERVICE")));

        Elevator updated = elevatorRepository.findById(e.getId()).orElseThrow();
        assertEquals(ElevatorOperationalStatus.OUT_OF_SERVICE, updated.getOperationalStatus());
    }

    @Test
    @DisplayName("12. Rejection of unauthorized elevator modifications")
    void testUnauthorizedElevatorModifications() throws Exception {
        Building b = buildingRepository.save(new Building("BLDG-UNAUTH", "Unauth Tower", "Address", "City", null));
        Elevator e = elevatorRepository.save(new Elevator("LIFT-RO", b, "Lobby", ElevatorOperationalStatus.ACTIVE));

        ElevatorStatusUpdateRequest statusRequest = new ElevatorStatusUpdateRequest(ElevatorOperationalStatus.OUT_OF_SERVICE);

        // Resident attempting to change status -> 403 Forbidden
        mockMvc.perform(patch("/api/elevators/" + e.getId() + "/status")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().isForbidden());

        // Resident attempting to edit elevator -> 403 Forbidden
        ElevatorUpdateRequest updateRequest = new ElevatorUpdateRequest(
                "LIFT-HACK", "Lobby", "Hacked", null, null, 10, ElevatorOperationalStatus.ACTIVE
        );

        mockMvc.perform(put("/api/elevators/" + e.getId())
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("13. Authentication requirements: protected endpoints reject unauthenticated requests (401)")
    void testUnauthenticatedAccessRejected() throws Exception {
        mockMvc.perform(get("/api/buildings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));

        mockMvc.perform(post("/api/buildings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/buildings/1/elevators"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/elevators/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
