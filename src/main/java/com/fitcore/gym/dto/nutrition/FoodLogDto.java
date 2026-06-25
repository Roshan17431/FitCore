package com.fitcore.gym.dto.nutrition;

import com.fitcore.gym.domain.MealType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FoodLogDto {
    private Long id;
    private UUID userId;
    private String mealName;
    private MealType mealType;
    private Integer calories;
    private Integer protein;
    private Integer carbs;
    private Integer fat;
    private LocalDate logDate;
    private LocalTime logTime;
}
