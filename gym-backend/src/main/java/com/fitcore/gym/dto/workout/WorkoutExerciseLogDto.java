package com.fitcore.gym.dto.workout;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WorkoutExerciseLogDto {
    private Long id;
    private Long workoutLogId;
    
    @NotNull(message = "Exercise is required")
    @Valid
    private ExerciseDto exercise;
    
    @PositiveOrZero(message = "Sets completed cannot be negative")
    private Integer setsCompleted;
    
    private String repsCompleted;
    
    @PositiveOrZero(message = "Weight used cannot be negative")
    private Double weightUsed;
}
