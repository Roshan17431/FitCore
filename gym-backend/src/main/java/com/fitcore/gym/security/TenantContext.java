package com.fitcore.gym.security;

import com.fitcore.gym.exception.UnauthorizedException;

import org.springframework.security.core.context.SecurityContextHolder;

public final class TenantContext {
    private TenantContext() {}

    public static TenantActor current() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof TenantActor actor))
            throw new UnauthorizedException("Authentication required");
        return actor;
    }
}
