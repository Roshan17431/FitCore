package com.fitcore.gym.service;

import com.fitcore.gym.dto.dashboard.StatCardDto;
import com.fitcore.gym.dto.nutrition.CalorieTrackerDto;
import com.fitcore.gym.dto.nutrition.FoodLogDto;
import com.fitcore.gym.dto.nutrition.NutritionDashboardDto;
import com.fitcore.gym.dto.nutrition.NutritionGoalDto;
import com.fitcore.gym.dto.nutrition.NutritionPlanDto;
import com.fitcore.gym.entity.FoodLog;
import com.fitcore.gym.entity.NutritionGoal;
import com.fitcore.gym.entity.NutritionPlan;
import com.fitcore.gym.entity.User;
import com.fitcore.gym.repository.FoodLogRepository;
import com.fitcore.gym.repository.NutritionGoalRepository;
import com.fitcore.gym.repository.NutritionPlanRepository;
import com.fitcore.gym.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NutritionService {

    private final NutritionGoalRepository nutritionGoalRepository;
    private final FoodLogRepository foodLogRepository;
    private final NutritionPlanRepository nutritionPlanRepository;
    private final UserRepository userRepository;

    @PreAuthorize("@recordAccess.user(authentication, #userId)")
    public NutritionDashboardDto getDashboard(UUID userId) {
        NutritionGoalDto goal = getGoal(userId);
        List<FoodLogDto> logs = getDailyLogs(userId, LocalDate.now());

        int calories = logs.stream().mapToInt(log -> valueOrZero(log.getCalories())).sum();
        int protein = logs.stream().mapToInt(log -> valueOrZero(log.getProtein())).sum();
        int carbs = logs.stream().mapToInt(log -> valueOrZero(log.getCarbs())).sum();
        int fat = logs.stream().mapToInt(log -> valueOrZero(log.getFat())).sum();

        int calorieGoal = goal != null && goal.getTargetCalories() != null ? goal.getTargetCalories() : 0;
        int proteinGoal = goal != null && goal.getTargetProtein() != null ? goal.getTargetProtein() : 0;
        int carbsGoal = goal != null && goal.getTargetCarbs() != null ? goal.getTargetCarbs() : 0;
        int fatGoal = goal != null && goal.getTargetFat() != null ? goal.getTargetFat() : 0;

        return NutritionDashboardDto.builder()
                .nutritionCards(List.of(
                        card("Daily Calories", calories + " kcal", "Goal: " + calorieGoal + " kcal", "emerald"),
                        card("Protein Intake", protein + " g", "Goal: " + proteinGoal + " g", "sky"),
                        card("Carbs Intake", carbs + " g", "Goal: " + carbsGoal + " g", "amber"),
                        card("Fat Intake", fat + " g", "Goal: " + fatGoal + " g", "rose")
                ))
                .calorieTracker(CalorieTrackerDto.builder()
                        .dailyGoal(calorieGoal)
                        .consumedCalories(calories)
                        .remainingCalories(Math.max(calorieGoal - calories, 0))
                        .build())
                .build();
    }

    public List<NutritionPlanDto> getNutritionPlans() {
        return nutritionPlanRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @PreAuthorize("@recordAccess.user(authentication, #userId)")
    public NutritionGoalDto setGoal(UUID userId, NutritionGoalDto request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        NutritionGoal goal = nutritionGoalRepository.findByUser(user).orElse(new NutritionGoal());
        goal.setUser(user);
        goal.setGymId(user.getGymId());
        goal.setTargetCalories(request.getTargetCalories());
        goal.setTargetProtein(request.getTargetProtein());
        goal.setTargetCarbs(request.getTargetCarbs());
        goal.setTargetFat(request.getTargetFat());

        return mapToDto(nutritionGoalRepository.save(goal));
    }

    @PreAuthorize("@recordAccess.user(authentication, #userId)")
    public NutritionGoalDto getGoal(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return nutritionGoalRepository.findByUser(user)
                .map(this::mapToDto)
                .orElse(null);
    }

    @PreAuthorize("@recordAccess.user(authentication, #userId)")
    public FoodLogDto logFood(UUID userId, FoodLogDto request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        FoodLog log = FoodLog.builder().gymId(user.getGymId())
                .user(user)
                .mealName(request.getMealName())
                .mealType(request.getMealType())
                .calories(request.getCalories())
                .protein(request.getProtein())
                .carbs(request.getCarbs())
                .fat(request.getFat())
                .logDate(request.getLogDate() != null ? request.getLogDate() : LocalDate.now())
                .logTime(request.getLogTime() != null ? request.getLogTime() : LocalTime.now())
                .build();

        return mapToDto(foodLogRepository.save(log));
    }

    @PreAuthorize("@recordAccess.user(authentication, #userId)")
    public List<FoodLogDto> getDailyLogs(UUID userId, LocalDate date) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return foodLogRepository.findByUserAndLogDateOrderByLogTimeDesc(user, date).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private NutritionGoalDto mapToDto(NutritionGoal goal) {
        return NutritionGoalDto.builder()
                .id(goal.getId())
                .userId(goal.getUser().getId())
                .targetCalories(goal.getTargetCalories())
                .targetProtein(goal.getTargetProtein())
                .targetCarbs(goal.getTargetCarbs())
                .targetFat(goal.getTargetFat())
                .build();
    }

    private FoodLogDto mapToDto(FoodLog log) {
        return FoodLogDto.builder()
                .id(log.getId())
                .userId(log.getUser().getId())
                .mealName(log.getMealName())
                .mealType(log.getMealType())
                .calories(log.getCalories())
                .protein(log.getProtein())
                .carbs(log.getCarbs())
                .fat(log.getFat())
                .logDate(log.getLogDate())
                .logTime(log.getLogTime())
                .build();
    }

    private NutritionPlanDto mapToDto(NutritionPlan plan) {
        return NutritionPlanDto.builder()
                .id(plan.getId())
                .category(plan.getCategory())
                .calories(plan.getCalories())
                .protein(plan.getProtein())
                .carbs(plan.getCarbs())
                .fats(plan.getFats())
                .meals(plan.getMeals())
                .build();
    }

    private StatCardDto card(String title, String value, String detail, String tone) {
        return StatCardDto.builder()
                .title(title)
                .value(value)
                .detail(detail)
                .tone(tone)
                .build();
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }
}
