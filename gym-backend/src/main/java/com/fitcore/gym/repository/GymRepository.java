package com.fitcore.gym.repository;
import com.fitcore.gym.entity.Gym;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
public interface GymRepository extends Repository<Gym,UUID> {
 Optional<Gym> findById(UUID id);
 <S extends Gym> S save(S gym);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select g from Gym g where g.id = :id")
 Optional<Gym> findByIdForUpdate(UUID id);
}
