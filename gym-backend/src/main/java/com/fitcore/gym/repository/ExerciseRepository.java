package com.fitcore.gym.repository;

import com.fitcore.gym.domain.*;
import com.fitcore.gym.entity.*;

import org.springframework.data.jpa.repository.*;

import java.time.*;
import java.util.*;

public interface ExerciseRepository extends TenantRepository<Exercise, Long> {
    List<Exercise> findByGymIdAndArchivedFalse(UUID gymId);
    boolean existsByGymIdAndNameAndIdNot(UUID gymId, String name, Long id);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Exercise e where e.id=:id and e.gymId=:gym")
    Optional<Exercise> lockForManagement(@org.springframework.data.repository.query.Param("id") Long id, @org.springframework.data.repository.query.Param("gym") UUID gym);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    @Query("select e from Exercise e where e.id=:id and e.gymId=:gym")
    Optional<Exercise> lockForLogging(@org.springframework.data.repository.query.Param("id") Long id, @org.springframework.data.repository.query.Param("gym") UUID gym);
    List<Exercise> findByGymIdAndCategory(UUID gymId, String category);
}
