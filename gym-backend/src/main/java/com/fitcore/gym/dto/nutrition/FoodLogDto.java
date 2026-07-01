package com.fitcore.gym.dto.nutrition;

import com.fitcore.gym.domain.MealType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FoodLogDto {
    private Long id;
    
    @NotNull(message = "User ID is required")
    private UUID userId;
    
    @NotBlank(message = "Meal name is required")
    private String mealName;
    
    @NotNull(message = "Meal type is required")
    private MealType mealType;
    
    @PositiveOrZero(message = "Calories cannot be negative")
    private Integer calories;
    
    @PositiveOrZero(message = "Protein cannot be negative")
    private Integer protein;
    
    @PositiveOrZero(message = "Carbs cannot be negative")
    private Integer carbs;
    
    @PositiveOrZero(message = "Fat cannot be negative")
    private Integer fat;
    
    private LocalDate logDate;
    private LocalTime logTime;
}
