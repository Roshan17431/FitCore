package com.fitcore.gym.controller;

import com.fitcore.gym.dto.nutrition.FoodLogDto;
import com.fitcore.gym.dto.nutrition.NutritionGoalDto;
import com.fitcore.gym.service.NutritionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/nutrition")
@RequiredArgsConstructor
public class NutritionController {

    private final NutritionService nutritionService;

    @PostMapping("/goal/{userId}")
    public ResponseEntity<NutritionGoalDto> setGoal(
            @PathVariable UUID userId,
            @RequestBody NutritionGoalDto request
    ) {
        return ResponseEntity.ok(nutritionService.setGoal(userId, request));
    }

    @GetMapping("/goal/{userId}")
    public ResponseEntity<NutritionGoalDto> getGoal(@PathVariable UUID userId) {
        NutritionGoalDto goal = nutritionService.getGoal(userId);
        if (goal == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(goal);
    }

    @PostMapping("/log/{userId}")
    public ResponseEntity<FoodLogDto> logFood(
            @PathVariable UUID userId,
            @RequestBody FoodLogDto request
    ) {
        return ResponseEntity.ok(nutritionService.logFood(userId, request));
    }

    @GetMapping("/logs/{userId}")
    public ResponseEntity<List<FoodLogDto>> getDailyLogs(
            @PathVariable UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        if (date == null) {
            date = LocalDate.now();
        }
        return ResponseEntity.ok(nutritionService.getDailyLogs(userId, date));
    }
}
