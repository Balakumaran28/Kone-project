package com.liftcare;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liftcare.dto.LoginRequest;
import com.liftcare.dto.RegisterRequest;
import com.liftcare.model.User;
import com.liftcare.model.UserRole;
import com.liftcare.repository.UserRepository;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("1. Successful user registration defaults to RESIDENT")
    void testSuccessfulRegistration() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "Alice Resident",
                "alice@example.com",
                "SecurePass123!",
                "+1-555-0155",
                null
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.email", is("alice@example.com")))
                .andExpect(jsonPath("$.fullName", is("Alice Resident")))
                .andExpect(jsonPath("$.role", is("RESIDENT")))
                .andExpect(jsonPath("$.active", is(true)))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("2. Passwords must be hashed with BCrypt and never stored in plaintext")
    void testPasswordHashedNotPlaintext() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "Bob Builder",
                "bob@example.com",
                "SuperSecretPassword!",
                null,
                null
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmail("bob@example.com").orElseThrow();
        assertNotNull(user.getPasswordHash());
        assertNotEquals("SuperSecretPassword!", user.getPasswordHash());
        assertTrue(user.getPasswordHash().startsWith("$2a$") || user.getPasswordHash().startsWith("$2b$"));
        assertTrue(passwordEncoder.matches("SuperSecretPassword!", user.getPasswordHash()));
    }

    @Test
    @DisplayName("3. Duplicate registration with existing email must be rejected")
    void testDuplicateRegistrationRejection() throws Exception {
        userRepository.save(new User(
                "Existing User",
                "duplicate@example.com",
                null,
                UserRole.RESIDENT,
                passwordEncoder.encode("ExistingPass1!")
        ));

        RegisterRequest request = new RegisterRequest(
                "New User",
                "duplicate@example.com",
                "NewPassword123!",
                null,
                null
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", containsString("already exists")));
    }

    @Test
    @DisplayName("4. Successful login returns signed JWT and safe user details")
    void testSuccessfulLogin() throws Exception {
        userRepository.save(new User(
                "David Clark",
                "david@example.com",
                "+1-555-0188",
                UserRole.TECHNICIAN,
                passwordEncoder.encode("ValidPassword123!")
        ));

        LoginRequest login = new LoginRequest("david@example.com", "ValidPassword123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.user.email", is("david@example.com")))
                .andExpect(jsonPath("$.user.role", is("TECHNICIAN")))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("5. Incorrect password must be rejected without disclosing email existence")
    void testIncorrectPasswordRejection() throws Exception {
        userRepository.save(new User(
                "Eve Guard",
                "eve@example.com",
                null,
                UserRole.SECURITY_STAFF,
                passwordEncoder.encode("CorrectPass1!")
        ));

        LoginRequest badPassword = new LoginRequest("eve@example.com", "WrongPassword!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badPassword)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("Unauthorized")))
                .andExpect(jsonPath("$.message", is("Invalid email or password.")));

        // Non-existent email returns identical generic message
        LoginRequest nonExistent = new LoginRequest("nonexistent@example.com", "AnyPassword!");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nonExistent)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Invalid email or password.")));
    }

    @Test
    @DisplayName("6. Inactive user authentication must be rejected")
    void testInactiveUserRejection() throws Exception {
        User disabledUser = new User(
                "Deactivated User",
                "inactive@example.com",
                null,
                UserRole.RESIDENT,
                passwordEncoder.encode("MyPassword123!")
        );
        disabledUser.setActive(false);
        userRepository.save(disabledUser);

        LoginRequest request = new LoginRequest("inactive@example.com", "MyPassword123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("Forbidden")))
                .andExpect(jsonPath("$.message", containsString("deactivated")));
    }

    @Test
    @DisplayName("7. Access /api/auth/me with valid token returns current user profile")
    void testMeEndpointWithValidToken() throws Exception {
        User user = userRepository.save(new User(
                "Sarah Connor",
                "sarah@example.com",
                null,
                UserRole.BUILDING_MANAGER,
                passwordEncoder.encode("Password123!")
        ));

        String token = jwtService.generateToken(user);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("sarah@example.com")))
                .andExpect(jsonPath("$.role", is("BUILDING_MANAGER")))
                .andExpect(jsonPath("$.fullName", is("Sarah Connor")));
    }

    @Test
    @DisplayName("8. Rejection of unauthenticated requests to /api/auth/me")
    void testMeEndpointUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")));
    }

    @Test
    @DisplayName("9. Rejection of malformed or invalid JWT tokens")
    void testMalformedTokenRejection() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer this-is-clearly-a-malformed-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("10. Role-based authorization: RESIDENT is forbidden on admin endpoint, ADMINISTRATOR is allowed")
    void testRoleBasedAuthorization() throws Exception {
        User resident = userRepository.save(new User(
                "Regular Resident",
                "resident@example.com",
                null,
                UserRole.RESIDENT,
                passwordEncoder.encode("Pass12345!")
        ));

        User admin = userRepository.save(new User(
                "System Admin",
                "admin@example.com",
                null,
                UserRole.ADMINISTRATOR,
                passwordEncoder.encode("AdminPass123!")
        ));

        String residentToken = jwtService.generateToken(resident);
        String adminToken = jwtService.generateToken(admin);

        // Resident -> 403 Forbidden
        mockMvc.perform(get("/api/auth/admin-check")
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.error", is("Forbidden")));

        // Administrator -> 200 OK
        mockMvc.perform(get("/api/auth/admin-check")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("Authorized administrative access")));
    }

    @Test
    @DisplayName("11. Prevention of public registration with privileged roles")
    void testPublicRegistrationPrivilegedRoleBlocked() throws Exception {
        RegisterRequest adminAttempt = new RegisterRequest(
                "Sneaky User",
                "sneaky@example.com",
                "Password123!",
                null,
                UserRole.ADMINISTRATOR
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminAttempt)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("cannot assign privileged administrative roles")));

        RegisterRequest supervisorAttempt = new RegisterRequest(
                "Sneaky Supervisor",
                "supervisor@example.com",
                "Password123!",
                null,
                UserRole.COMPANY_SUPERVISOR
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(supervisorAttempt)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("cannot assign privileged administrative roles")));
    }

    @Test
    @DisplayName("12. Documented logout response")
    void testLogoutResponse() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("Logged out successfully")));
    }
}
