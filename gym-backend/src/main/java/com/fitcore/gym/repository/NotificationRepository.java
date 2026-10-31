package com.fitcore.gym.repository;

import com.fitcore.gym.domain.*;
import com.fitcore.gym.entity.*;

import org.springframework.data.jpa.repository.*;

import java.time.*;
import java.util.*;

public interface NotificationRepository extends TenantRepository<Notification, Long> {
    List<Notification> findByGymIdAndUserIdOrderByCreatedAtDesc(UUID gymId, UUID userId);
}
