package com.fitcore.gym.repository;

import com.fitcore.gym.domain.*;
import com.fitcore.gym.entity.*;

import org.springframework.data.jpa.repository.*;

import java.time.*;
import java.util.*;

public interface TrainerSessionRepository extends TenantRepository<TrainerSession, Long> {
    List<TrainerSession> findByGymIdAndMemberIdOrderBySessionDateAscStartTimeAsc(
            UUID gymId, UUID memberId);

    List<TrainerSession> findByGymIdAndTrainerId(UUID gymId, Long trainerId);

    @Query(
            "select count(s)>0 from TrainerSession s where s.gymId=:gymId and s.status=:status and"
                + " s.sessionDate=:sessionDate and s.startTime<:endTime and s.endTime>:startTime"
                + " and (s.trainer.id=:trainerId or s.member.id=:memberId)")
    boolean existsScheduledOverlap(
            UUID gymId,
            Long trainerId,
            UUID memberId,
            LocalDate sessionDate,
            LocalTime startTime,
            LocalTime endTime,
            SessionStatus status);
}
