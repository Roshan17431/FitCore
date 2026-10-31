package com.fitcore.gym.dto.user;

import com.fitcore.gym.domain.Role;
import com.fitcore.gym.entity.User;

import java.util.UUID;

public record SessionUserDto(
        UUID id,
        String fullName,
        String email,
        Role role,
        String fitnessGoal,
        boolean enabled,
        UUID gymId,
        String gymName,
        long authzVersion) {
    public static SessionUserDto from(User u, com.fitcore.gym.entity.Gym gym) {
        return new SessionUserDto(
                u.getId(),
                u.getFullName(),
                u.getEmail(),
                u.getRole(),
                u.getFitnessGoal(),
                u.isEnabled(),
                u.getGymId(),
                gym.getName(),
                u.getAuthzVersion());
    }
}
