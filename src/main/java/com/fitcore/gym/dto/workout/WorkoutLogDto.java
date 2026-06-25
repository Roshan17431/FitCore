package com.fitcore.gym.dto.workout;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WorkoutLogDto {
    private Long id;
    private UUID userId;
    private LocalDate date;
    private Integer durationMinutes;
    private String notes;
    private List<WorkoutExerciseLogDto> exercises;
}
