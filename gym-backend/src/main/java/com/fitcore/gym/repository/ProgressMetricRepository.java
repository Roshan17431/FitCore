package com.fitcore.gym.repository;

import com.fitcore.gym.entity.ProgressMetric;
import com.fitcore.gym.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProgressMetricRepository extends JpaRepository<ProgressMetric, Long> {
    List<ProgressMetric> findByUserOrderByLogDateDesc(User user);
    Optional<ProgressMetric> findFirstByUserOrderByLogDateDesc(User user);
}
