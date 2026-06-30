package com.fitcore.gym.dto.workout;

import com.fitcore.gym.domain.DifficultyLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExerciseDto {
    private Long id;
    
    @NotBlank(message = "Exercise name is required")
    @Size(max = 100, message = "Exercise name must not exceed 100 characters")
    private String name;
    
    @NotBlank(message = "Category is required")
    private String category;
    
    @NotNull(message = "Difficulty level is required")
    private DifficultyLevel difficulty;
    
    @Positive(message = "Recommended sets must be positive")
    private Integer recommendedSets;
    
    private String recommendedReps;
    private String description;
    private String imageUrl;
}
