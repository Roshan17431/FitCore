package com.fitcore.gym.fixture;

import com.fitcore.gym.entity.Exercise;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FixtureExerciseRepository extends JpaRepository<Exercise, Long> {
    List<Exercise> findByCategory(String category);
}
