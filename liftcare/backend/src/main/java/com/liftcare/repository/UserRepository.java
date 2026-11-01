package com.liftcare.repository;

import com.liftcare.model.User;
import com.liftcare.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByRole(UserRole role);

    List<User> findByRoleAndActiveTrue(UserRole role);

    List<User> findByRoleInAndActiveTrue(java.util.Collection<UserRole> roles);

    List<User> findByActiveTrue();
}
