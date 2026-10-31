package com.fitcore.gym.repository;

import com.fitcore.gym.domain.*;
import com.fitcore.gym.entity.*;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.*;

import java.time.*;
import java.util.*;

public interface UserRepository extends TenantRepository<User, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id=:id and u.gymId=:gymId")
    Optional<User> findByIdAndGymIdForUpdate(UUID id, UUID gymId);

    List<User> findByGymIdAndRole(UUID gymId, Role role);

    long countByGymIdAndRole(UUID gymId, Role role);
}
