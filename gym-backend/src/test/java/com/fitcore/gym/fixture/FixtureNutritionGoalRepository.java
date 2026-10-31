package com.fitcore.gym.fixture;

import com.fitcore.gym.entity.NutritionGoal;
import com.fitcore.gym.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FixtureNutritionGoalRepository extends JpaRepository<NutritionGoal, Long> {
    Optional<NutritionGoal> findByUser(User user);
}
