package com.fitcore.gym.repository;

import com.fitcore.gym.domain.*;
import com.fitcore.gym.entity.*;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.*;

import java.time.*;
import java.util.*;

public interface TrainerProfileRepository extends TenantRepository<TrainerProfile, Long> {
    Optional<TrainerProfile> findByGymIdAndUserId(UUID gymId, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from TrainerProfile p where p.id=:id and p.gymId=:gymId")
    Optional<TrainerProfile> findByIdAndGymIdForUpdate(Long id, UUID gymId);
}
