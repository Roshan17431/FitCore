package com.fitcore.gym.repository;

import com.fitcore.gym.domain.*;
import com.fitcore.gym.entity.*;

import org.springframework.data.jpa.repository.*;

import java.time.*;
import java.util.*;

public interface WorkoutExerciseLogRepository extends TenantRepository<WorkoutExerciseLog, Long> {
    boolean existsByGymIdAndExerciseId(UUID gymId, Long exerciseId);
    List<WorkoutExerciseLog> findByGymIdAndWorkoutLogId(UUID gymId, Long workoutLogId);
}
