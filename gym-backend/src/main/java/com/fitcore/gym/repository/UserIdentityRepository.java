package com.fitcore.gym.repository;

import com.fitcore.gym.entity.User;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.Repository;

import java.util.*;

/** Global identity access is restricted to authentication and uniqueness validation. */
public interface UserIdentityRepository extends Repository<User, UUID> {
    @EntityGraph(attributePaths = "gym")
    Optional<User> findById(UUID id);

    @EntityGraph(attributePaths = "gym")
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, UUID id);
}
