package com.fitcore.gym.dto.trainer;

import com.fitcore.gym.dto.user.UserDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TrainerProfileDto {
    private Long id;
    
    @Valid
    private UserDto user;
    
    @NotBlank(message = "Specialization is required")
    private String specialization;
    
    @PositiveOrZero(message = "Experience years cannot be negative")
    private Integer experienceYears;
    
    private String bio;
}
