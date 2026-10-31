package com.fitcore.gym.security;

import com.fitcore.gym.domain.Role;
import com.fitcore.gym.entity.*;
import com.fitcore.gym.exception.*;
import com.fitcore.gym.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TenantAccess {
    private final UserRepository users;

    public UUID gym() {
        return TenantContext.current().gymId();
    }

    public User requireUser(UUID id) {
        var actor = TenantContext.current();
        User user =
                users.findByIdAndGymId(id, actor.gymId())
                        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!actor.userId().equals(id) && !actor.manager())
            throw new ForbiddenException("Access denied");
        return user;
    }

    public void requireOwned(TenantOwnedEntity entity) {
        if (entity == null || !gym().equals(entity.getGymId()))
            throw new ResourceNotFoundException("Record not found");
    }

    public TenantActor manager() {
        var actor = TenantContext.current();
        if (!actor.manager()) throw new ForbiddenException("Access denied");
        return actor;
    }

    public void manageTarget(User target) {
        requireOwned(target);
        var actor = manager();
        if (target.getRole() == Role.OWNER
                || (actor.role() == Role.ADMIN && target.getRole() == Role.ADMIN))
            throw new ForbiddenException("Protected account");
    }

    public void assign(Role role) {
        var actor = manager();
        if (role == null || role == Role.OWNER)
            throw new BadRequestException("Invalid role assignment");
        if (actor.role() == Role.ADMIN && role == Role.ADMIN)
            throw new ForbiddenException("Role assignment denied");
    }
}
