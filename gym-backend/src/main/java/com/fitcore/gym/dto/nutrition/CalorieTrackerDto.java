package com.fitcore.gym.dto.nutrition;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CalorieTrackerDto {
    private Integer dailyGoal;
    private Integer consumedCalories;
    private Integer remainingCalories;
}
