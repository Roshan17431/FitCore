package com.fitcore.gym.fixture;

import com.fitcore.gym.entity.WorkoutExerciseLog;
import com.fitcore.gym.entity.WorkoutLog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FixtureWorkoutExerciseLogRepository
        extends JpaRepository<WorkoutExerciseLog, Long> {
    List<WorkoutExerciseLog> findByWorkoutLog(WorkoutLog workoutLog);
}
