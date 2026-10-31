package com.fitcore.gym.security;

import com.fitcore.gym.exception.DuplicateResourceException;
import com.fitcore.gym.repository.UserIdentityRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class IdentityUniqueness {
    private final UserIdentityRepository identities;

    public void requireAvailable(String email, UUID existingId) {
        if (existingId == null
                ? identities.existsByEmail(email)
                : identities.existsByEmailAndIdNot(email, existingId))
            throw new DuplicateResourceException("Email already in use");
    }
}
