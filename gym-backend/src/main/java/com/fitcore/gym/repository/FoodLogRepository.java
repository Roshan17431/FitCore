package com.fitcore.gym.repository;

import com.fitcore.gym.entity.FoodLog;
import com.fitcore.gym.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface FoodLogRepository extends JpaRepository<FoodLog, Long> {
    List<FoodLog> findByUserAndLogDateOrderByLogTimeDesc(User user, LocalDate logDate);
    List<FoodLog> findByUserAndLogDateBetweenOrderByLogDateDescLogTimeDesc(User user, LocalDate start, LocalDate end);
}
