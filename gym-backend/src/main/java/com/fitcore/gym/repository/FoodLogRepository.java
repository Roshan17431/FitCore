package com.fitcore.gym.repository;

import com.fitcore.gym.domain.*;
import com.fitcore.gym.entity.*;

import org.springframework.data.jpa.repository.*;

import java.time.*;
import java.util.*;

public interface FoodLogRepository extends TenantRepository<FoodLog, Long> {
    List<FoodLog> findByGymIdAndUserIdAndLogDateOrderByLogTimeDesc(
            UUID gymId, UUID userId, LocalDate logDate);

    List<FoodLog> findByGymIdAndUserIdAndLogDateBetweenOrderByLogDateDescLogTimeDesc(
            UUID gymId, UUID userId, LocalDate start, LocalDate end);
}
