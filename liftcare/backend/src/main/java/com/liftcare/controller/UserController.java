package com.liftcare.controller;

import com.liftcare.dto.UserResponse;
import com.liftcare.model.UserRole;
import com.liftcare.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/technicians")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'BUILDING_MANAGER', 'SECURITY_STAFF', 'COMPANY_SUPERVISOR')")
    public ResponseEntity<List<UserResponse>> getActiveTechnicians() {
        List<UserResponse> technicians = userRepository.findByRoleAndActiveTrue(UserRole.TECHNICIAN).stream()
                .map(UserResponse::from)
                .toList();
        return ResponseEntity.ok(technicians);
    }
}
