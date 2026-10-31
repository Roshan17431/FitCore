package com.fitcore.gym.repository;

import com.fitcore.gym.domain.*;
import com.fitcore.gym.entity.*;

import org.springframework.data.jpa.repository.*;

import java.time.*;
import java.util.*;

public interface ActiveMembershipRepository extends TenantRepository<ActiveMembership, Long> {
    List<ActiveMembership> findByGymIdAndUserIdOrderByStartDateDesc(UUID gymId, UUID userId);

    Optional<ActiveMembership> findByIdAndGymIdAndUserId(Long id, UUID gymId, UUID userId);

    List<ActiveMembership> findByGymIdAndStatus(UUID gymId, MembershipStatus status);

    boolean existsByGymIdAndPlanId(UUID gymId, Long planId);
}
