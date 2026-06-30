package com.fitcore.gym.dto.trainer;

import com.fitcore.gym.dto.user.UserDto;
import com.fitcore.gym.domain.SessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TrainerSessionDto {
    private Long id;
    
    @NotNull(message = "Member is required")
    @Valid
    private UserDto member;
    
    @NotNull(message = "Trainer is required")
    @Valid
    private TrainerProfileDto trainer;
    
    @NotNull(message = "Session date is required")
    @FutureOrPresent(message = "Session date must be today or in the future")
    private LocalDate sessionDate;
    
    @NotNull(message = "Start time is required")
    private LocalTime startTime;
    
    @NotNull(message = "End time is required")
    private LocalTime endTime;
    
    private SessionStatus status;
}
