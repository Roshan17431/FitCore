package com.fitcore.gym.dto.nutrition;

import com.fitcore.gym.dto.dashboard.StatCardDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NutritionDashboardDto {
    private List<StatCardDto> nutritionCards;
    private CalorieTrackerDto calorieTracker;
}
