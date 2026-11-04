package com.fitcore.gym.repository;

import com.fitcore.gym.domain.*;
import com.fitcore.gym.entity.*;

import org.springframework.data.jpa.repository.*;

import java.time.*;
import java.util.*;

public interface WorkoutPlanRepository extends TenantRepository<WorkoutPlan, Long> {
    List<WorkoutPlan> findByGymIdAndStatus(UUID gymId, WorkoutPlanStatus status);
    boolean existsByGymIdAndLevelAndIdNot(UUID gymId, String level, Long id);
    @Query("select count(p) from WorkoutPlan p join p.exercises e where p.gymId=:gym and e=:name")
    long countExerciseReferences(@org.springframework.data.repository.query.Param("gym") UUID gym, @org.springframework.data.repository.query.Param("name") String name);
    @Query("select count(p) from WorkoutPlan p join p.exercises e where p.gymId=:gym and p.status=com.fitcore.gym.domain.WorkoutPlanStatus.PUBLISHED and e=:name")
    long countPublishedExerciseReferences(@org.springframework.data.repository.query.Param("gym") UUID gym, @org.springframework.data.repository.query.Param("name") String name);
}
