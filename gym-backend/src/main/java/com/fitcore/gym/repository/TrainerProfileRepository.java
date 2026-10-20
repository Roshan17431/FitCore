package com.fitcore.gym.repository;

import com.fitcore.gym.entity.TrainerProfile;
import com.fitcore.gym.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TrainerProfileRepository extends JpaRepository<TrainerProfile, Long> {
    Optional<TrainerProfile> findByUser(User user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select profile from TrainerProfile profile where profile.id = :id")
    Optional<TrainerProfile> findByIdForUpdate(@Param("id") Long id);
}
