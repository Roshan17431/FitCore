package com.fitcore.gym.dto.trainer;

import com.fitcore.gym.dto.user.UserDto;
import com.fitcore.gym.domain.SessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TrainerSessionDto {
    private Long id;
    private UserDto member;
    private TrainerProfileDto trainer;
    private LocalDate sessionDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private SessionStatus status;
}
