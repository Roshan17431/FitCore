package com.fitcore.gym.dto.workout;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WorkoutLogDto {
    private Long id;
    
    @NotNull(message = "User ID is required")
    private UUID userId;
    
    private LocalDate date;
    
    @Positive(message = "Duration must be positive")
    private Integer durationMinutes;
    
    private String notes;
    
    @Valid
    private List<WorkoutExerciseLogDto> exercises;
}
