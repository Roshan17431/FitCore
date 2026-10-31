package com.fitcore.gym.fixture;

import com.fitcore.gym.entity.User;
import com.fitcore.gym.entity.WorkoutLog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface FixtureWorkoutLogRepository extends JpaRepository<WorkoutLog, Long> {
    List<WorkoutLog> findByUserOrderByDateDesc(User user);

    List<WorkoutLog> findByUserAndDateBetweenOrderByDateAsc(
            User user, LocalDate start, LocalDate end);
}
