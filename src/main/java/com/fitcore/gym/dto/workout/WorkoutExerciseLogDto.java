package com.fitcore.gym.dto.workout;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WorkoutExerciseLogDto {
    private Long id;
    private Long workoutLogId;
    private ExerciseDto exercise;
    private Integer setsCompleted;
    private String repsCompleted;
    private Double weightUsed;
}
