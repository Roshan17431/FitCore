package com.fitcore.gym.fixture;

import com.fitcore.gym.domain.SessionStatus;
import com.fitcore.gym.entity.TrainerProfile;
import com.fitcore.gym.entity.TrainerSession;
import com.fitcore.gym.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface FixtureTrainerSessionRepository extends JpaRepository<TrainerSession, Long> {
    List<TrainerSession> findByMember(User member);

    List<TrainerSession> findByMemberOrderBySessionDateAscStartTimeAsc(User member);

    List<TrainerSession> findByTrainer(TrainerProfile trainer);

    @Query(
            """
            select count(session) > 0 from TrainerSession session
            where session.status = :status
              and session.sessionDate = :sessionDate
              and session.startTime < :endTime
              and session.endTime > :startTime
              and (session.trainer.id = :trainerId or session.member.id = :memberId)
            """)
    boolean existsScheduledOverlap(
            @Param("trainerId") Long trainerId,
            @Param("memberId") UUID memberId,
            @Param("sessionDate") LocalDate sessionDate,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("status") SessionStatus status);
}
