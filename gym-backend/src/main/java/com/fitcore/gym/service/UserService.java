package com.fitcore.gym.service;

import com.fitcore.gym.dto.user.UserDto;
import com.fitcore.gym.entity.User;
import com.fitcore.gym.repository.UserRepository;
import com.fitcore.gym.security.TenantAccess;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@org.springframework.transaction.annotation.Transactional
@RequiredArgsConstructor
public class UserService {
    private final TenantAccess tenantAccess;

    private final UserRepository userRepository;

    @PreAuthorize("@recordAccess.user(authentication, #id)")
    public UserDto getUserById(UUID id) {
        User user = tenantAccess.requireUser(id);
        return mapToDto(user);
    }

    public UserDto mapToDto(User user) {
        tenantAccess.requireOwned(user);
        return UserDto.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .fitnessGoal(user.getFitnessGoal())
                .enabled(user.isEnabled())
                .build();
    }
}
