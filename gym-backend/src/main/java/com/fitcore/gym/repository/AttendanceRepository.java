package com.fitcore.gym.repository;

import com.fitcore.gym.domain.*;
import com.fitcore.gym.entity.*;

import org.springframework.data.jpa.repository.*;

import java.time.*;
import java.util.*;

public interface AttendanceRepository extends TenantRepository<Attendance, Long> {
    List<Attendance> findByGymIdAndUserIdOrderByCheckInTimeDesc(UUID gymId, UUID userId);

    List<Attendance> findAllByGymIdOrderByCheckInTimeDesc(UUID gymId);

    List<Attendance> findByGymIdAndCheckInDateBetween(UUID gymId, LocalDate start, LocalDate end);

    Optional<Attendance> findFirstByGymIdAndUserIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(
            UUID gymId, UUID userId);
}
