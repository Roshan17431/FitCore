package com.fitcore.gym.dto.workout;

import com.fitcore.gym.domain.DifficultyLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExerciseDto {
    private Long id;
    private String name;
    private String category;
    private DifficultyLevel difficulty;
    private Integer recommendedSets;
    private String recommendedReps;
    private String description;
    private String imageUrl;
}
