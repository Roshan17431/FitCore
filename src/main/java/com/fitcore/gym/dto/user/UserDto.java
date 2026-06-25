package com.fitcore.gym.dto.user;

import com.fitcore.gym.domain.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserDto {
    private UUID id;
    private String fullName;
    private String email;
    private Role role;
    private String fitnessGoal;
}
