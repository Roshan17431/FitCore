package com.fitcore.gym.dto.trainer;

import com.fitcore.gym.dto.user.UserDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TrainerProfileDto {
    private Long id;
    private UserDto user;
    private String specialization;
    private Integer experienceYears;
    private String bio;
}
