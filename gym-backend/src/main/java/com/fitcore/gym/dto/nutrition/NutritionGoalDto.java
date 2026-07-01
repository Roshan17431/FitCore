package com.fitcore.gym.dto.nutrition;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NutritionGoalDto {
    private Long id;
    
    @NotNull(message = "User ID is required")
    private UUID userId;
    
    @Positive(message = "Target calories must be positive")
    private Integer targetCalories;
    
    @Positive(message = "Target protein must be positive")
    private Integer targetProtein;
    
    @Positive(message = "Target carbs must be positive")
    private Integer targetCarbs;
    
    @Positive(message = "Target fat must be positive")
    private Integer targetFat;
}
