package com.fitcore.gym.security;

import com.fitcore.gym.domain.Role;

import java.util.Objects;
import java.util.UUID;

/** Trusted principal: constructed only after database-backed account validation. */
public record TenantActor(UUID userId, UUID gymId, Role role, long authzVersion) {
    public TenantActor {
        Objects.requireNonNull(userId);
        Objects.requireNonNull(gymId);
        Objects.requireNonNull(role);
    }

    public boolean manager() {
        return role == Role.OWNER || role == Role.ADMIN;
    }
}
