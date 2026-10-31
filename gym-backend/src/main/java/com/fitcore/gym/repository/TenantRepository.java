package com.fitcore.gym.repository;

import com.fitcore.gym.domain.*;
import com.fitcore.gym.entity.*;

import org.springframework.data.jpa.repository.*;

import java.time.*;
import java.util.*;

@org.springframework.data.repository.NoRepositoryBean
public interface TenantRepository<T extends TenantOwnedEntity, ID>
        extends org.springframework.data.repository.Repository<T, ID> {
    Optional<T> findByIdAndGymId(ID id, UUID gymId);

    List<T> findAllByGymId(UUID gymId);

    <S extends T> S save(S entity);

    <S extends T> S saveAndFlush(S entity);

    void delete(T entity);

    void flush();
}
