package com.fitcore.gym.repository;

import com.fitcore.gym.domain.*;
import com.fitcore.gym.entity.*;

import org.springframework.data.jpa.repository.*;

import java.time.*;
import java.util.*;

public interface ProgressMetricRepository extends TenantRepository<ProgressMetric, Long> {
    List<ProgressMetric> findByGymIdAndUserIdOrderByLogDateDesc(UUID gymId, UUID userId);

    Optional<ProgressMetric> findFirstByGymIdAndUserIdOrderByLogDateDesc(UUID gymId, UUID userId);
}
