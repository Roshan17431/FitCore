package com.fitcore.gym.repository;

import com.fitcore.gym.entity.NutritionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NutritionPlanRepository extends JpaRepository<NutritionPlan, Long> {
}
