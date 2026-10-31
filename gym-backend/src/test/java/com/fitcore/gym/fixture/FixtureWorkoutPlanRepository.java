package com.fitcore.gym.fixture;

import com.fitcore.gym.entity.WorkoutPlan;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FixtureWorkoutPlanRepository extends JpaRepository<WorkoutPlan, Long> {}
