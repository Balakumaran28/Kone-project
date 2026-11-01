package com.liftcare.service;

import com.liftcare.dto.AuthResponse;
import com.liftcare.dto.LoginRequest;
import com.liftcare.dto.RegisterRequest;
import com.liftcare.dto.UserResponse;
import com.liftcare.model.User;
import com.liftcare.model.UserRole;
import com.liftcare.repository.UserRepository;
import com.liftcare.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        // 1. Validate requested role first (fail fast before database query)
        UserRole assignedRole = UserRole.RESIDENT;
        if (request.requestedRole() != null) {
            if (request.requestedRole() == UserRole.ADMINISTRATOR || request.requestedRole() == UserRole.COMPANY_SUPERVISOR) {
                throw new IllegalArgumentException("Public registration cannot assign privileged administrative roles (" + request.requestedRole() + ").");
            }
            assignedRole = request.requestedRole();
        }

        String normalizedEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("An account with this email address already exists.");
        }

        String passwordHash = passwordEncoder.encode(request.password());

        User user = new User(
                request.fullName().trim(),
                normalizedEmail,
                request.phoneNumber() != null ? request.phoneNumber().trim() : null,
                assignedRole,
                passwordHash
        );

        User saved = userRepository.save(user);
        log.info("Registered new user ID: {} with role: {}", saved.getId(), saved.getRole());

        return UserResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password."));

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password.");
        }

        if (!user.isActive()) {
            throw new DisabledException("Account is deactivated. Please contact your facility administrator.");
        }

        String token = jwtService.generateToken(user);
        log.info("User ID: {} successfully authenticated via local node", user.getId());

        return AuthResponse.of(token, jwtService.getExpirationMs(), UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String email) {
        if (email == null) {
            throw new BadCredentialsException("No authenticated principal provided.");
        }
        User user = userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new BadCredentialsException("Authenticated user record not found."));

        if (!user.isActive()) {
            throw new DisabledException("Account is deactivated.");
        }

        return UserResponse.from(user);
    }
}
