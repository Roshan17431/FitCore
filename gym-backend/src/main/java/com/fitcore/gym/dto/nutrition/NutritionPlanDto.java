package com.fitcore.gym.dto.nutrition;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NutritionPlanDto {
    private Long id;
    private String category;
    private Integer calories;
    private Integer protein;
    private Integer carbs;
    private Integer fats;
    private List<String> meals;
}
