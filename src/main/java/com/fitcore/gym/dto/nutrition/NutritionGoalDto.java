package com.fitcore.gym.dto.nutrition;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NutritionGoalDto {
    private Long id;
    private UUID userId;
    private Integer targetCalories;
    private Integer targetProtein;
    private Integer targetCarbs;
    private Integer targetFat;
}
