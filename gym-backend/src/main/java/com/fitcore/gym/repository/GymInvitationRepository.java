package com.fitcore.gym.repository;

import com.fitcore.gym.entity.GymInvitation;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

public interface GymInvitationRepository extends Repository<GymInvitation, UUID> {
    Optional<GymInvitation> findByIdAndGymId(UUID id, UUID gymId);

    java.util.List<GymInvitation> findByGymIdOrderByCreatedAtDesc(UUID gymId);

    @Query("select i.gymId from GymInvitation i where i.tokenHash=:hash")
    Optional<UUID> findGymIdByTokenHash(String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from GymInvitation i where i.id=:id and i.gymId=:gymId")
    Optional<GymInvitation> findByIdAndGymIdForUpdate(UUID id, UUID gymId);

    <S extends GymInvitation> S save(S invitation);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from GymInvitation i where i.tokenHash = :hash")
    Optional<GymInvitation> findByTokenHashForUpdate(String hash);
}
