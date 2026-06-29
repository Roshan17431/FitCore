package com.fitcore.gym.service;

import com.fitcore.gym.dto.nutrition.FoodLogDto;
import com.fitcore.gym.dto.nutrition.NutritionGoalDto;
import com.fitcore.gym.entity.FoodLog;
import com.fitcore.gym.entity.NutritionGoal;
import com.fitcore.gym.entity.User;
import com.fitcore.gym.repository.FoodLogRepository;
import com.fitcore.gym.repository.NutritionGoalRepository;
import com.fitcore.gym.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NutritionService {

    private final NutritionGoalRepository nutritionGoalRepository;
    private final FoodLogRepository foodLogRepository;
    private final UserRepository userRepository;

    public NutritionGoalDto setGoal(UUID userId, NutritionGoalDto request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        NutritionGoal goal = nutritionGoalRepository.findByUser(user).orElse(new NutritionGoal());
        goal.setUser(user);
        goal.setTargetCalories(request.getTargetCalories());
        goal.setTargetProtein(request.getTargetProtein());
        goal.setTargetCarbs(request.getTargetCarbs());
        goal.setTargetFat(request.getTargetFat());

        return mapToDto(nutritionGoalRepository.save(goal));
    }

    public NutritionGoalDto getGoal(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return nutritionGoalRepository.findByUser(user)
                .map(this::mapToDto)
                .orElse(null);
    }

    public FoodLogDto logFood(UUID userId, FoodLogDto request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        FoodLog log = FoodLog.builder()
                .user(user)
                .mealName(request.getMealName())
                .mealType(request.getMealType())
                .calories(request.getCalories())
                .protein(request.getProtein())
                .carbs(request.getCarbs())
                .fat(request.getFat())
                .logDate(request.getLogDate() != null ? request.getLogDate() : LocalDate.now())
                .logTime(request.getLogTime())
                .build();

        return mapToDto(foodLogRepository.save(log));
    }

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
}
