package com.fitcore.gym.service;

import com.fitcore.gym.dto.user.UserDto;
import com.fitcore.gym.entity.User;
import com.fitcore.gym.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @PreAuthorize("@recordAccess.user(authentication, #id)")
    public UserDto getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return mapToDto(user);
    }

    public UserDto mapToDto(User user) {
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
