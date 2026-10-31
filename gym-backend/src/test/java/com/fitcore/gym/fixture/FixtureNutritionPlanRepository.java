package com.fitcore.gym.fixture;

import com.fitcore.gym.entity.NutritionPlan;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FixtureNutritionPlanRepository extends JpaRepository<NutritionPlan, Long> {}
