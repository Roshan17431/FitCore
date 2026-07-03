package com.fitcore.gym.repository;

import com.fitcore.gym.entity.TrainerProfile;
import com.fitcore.gym.entity.TrainerSession;
import com.fitcore.gym.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrainerSessionRepository extends JpaRepository<TrainerSession, Long> {
    List<TrainerSession> findByMember(User member);
    List<TrainerSession> findByMemberOrderBySessionDateAscStartTimeAsc(User member);
    List<TrainerSession> findByTrainer(TrainerProfile trainer);
}
