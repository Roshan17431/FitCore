package com.fitcore.gym.dto.catalog;

import com.fitcore.gym.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

/** No writable tenant/owner fields. IDs select references, never authorize them. */
public final class CatalogDtos {
    private CatalogDtos() {}
    public record ExerciseInput(
        @NotBlank @Size(max=100) String name,
        @NotBlank @Size(max=100) String category,
        @NotNull DifficultyLevel difficulty,
        @Min(1) @Max(100) Integer recommendedSets,
        @Size(max=100) String recommendedReps,
        @Size(max=4000) String description,
        @Size(max=255) String imageUrl) {}
    public record ExerciseView(Long id,String name,String category,DifficultyLevel difficulty,Integer recommendedSets,
        String recommendedReps,String description,String imageUrl,boolean imageApproved,boolean archived) {}
    public record WorkoutInput(@NotBlank @Size(max=100) String level,@NotBlank @Size(max=100) String duration,
        @Size(max=31) List<@NotBlank @Size(max=255) String> schedule,
        @Size(max=100) List<@NotNull @Positive Long> exerciseIds,WorkoutPlanStatus status) {}
    public record WorkoutView(Long id,String level,String duration,List<String> schedule,List<String> exercises,
        List<Long> exerciseIds,List<String> unresolvedExercises,WorkoutPlanStatus status) {}
    public record NutritionInput(@NotBlank @Size(max=100) String category,
        @NotNull @Min(0) @Max(20000) Integer calories,@NotNull @Min(0) @Max(2000) Integer protein,
        @NotNull @Min(0) @Max(2000) Integer carbs,@NotNull @Min(0) @Max(2000) Integer fats,
        @NotNull @Size(max=31) List<@NotBlank @Size(max=255) String> meals) {}
    public record NutritionView(Long id,String category,Integer calories,Integer protein,Integer carbs,Integer fats,List<String> meals,boolean archived) {}
}
