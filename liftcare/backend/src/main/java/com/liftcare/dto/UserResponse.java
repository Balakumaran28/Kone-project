package com.liftcare.dto;

import com.liftcare.model.User;
import com.liftcare.model.UserRole;

import java.time.Instant;

/**
 * Safe user view DTO that never exposes sensitive fields like password hashes.
 */
public record UserResponse(
        Long id,
        String fullName,
        String email,
        String phoneNumber,
        UserRole role,
        boolean active,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        if (user == null) {
            return null;
        }
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt()
        );
    }
}
