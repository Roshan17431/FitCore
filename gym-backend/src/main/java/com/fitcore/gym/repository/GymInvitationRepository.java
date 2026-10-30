package com.fitcore.gym.repository;
import com.fitcore.gym.entity.GymInvitation;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
public interface GymInvitationRepository extends Repository<GymInvitation,UUID> {
 Optional<GymInvitation> findByIdAndGymId(UUID id,UUID gymId);
 <S extends GymInvitation> S save(S invitation);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select i from GymInvitation i where i.tokenHash = :hash")
 Optional<GymInvitation> findByTokenHashForUpdate(String hash);
}
