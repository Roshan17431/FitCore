package com.fitcore.gym.controller;

import com.fitcore.gym.dto.nutrition.FoodLogDto;
import com.fitcore.gym.dto.nutrition.NutritionDashboardDto;
import com.fitcore.gym.dto.nutrition.NutritionGoalDto;
import com.fitcore.gym.dto.nutrition.NutritionPlanDto;
import com.fitcore.gym.service.NutritionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/nutrition")
@RequiredArgsConstructor
@Tag(name = "Nutrition", description = "Endpoints for managing nutrition goals and logs")
@SecurityRequirement(name = "Bearer Authentication")
public class NutritionController {

    private final NutritionService nutritionService;

    @GetMapping("/dashboard/{userId}")
    public ResponseEntity<NutritionDashboardDto> getDashboard(@PathVariable UUID userId) {
        return ResponseEntity.ok(nutritionService.getDashboard(userId));
    }

    @GetMapping("/plans")
    public ResponseEntity<List<NutritionPlanDto>> getPlans() {
        return ResponseEntity.ok(nutritionService.getNutritionPlans());
    }

    @Operation(
            summary = "Set nutrition goal",
            description = "Creates or updates the daily nutrition goals for a user.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Goal successfully set"),
                    @ApiResponse(responseCode = "400", description = "Validation error"),
                    @ApiResponse(responseCode = "404", description = "User not found")
            }
    )
    @PostMapping("/goal/{userId}")
    public ResponseEntity<NutritionGoalDto> setGoal(
            @PathVariable UUID userId,
            @Valid @RequestBody NutritionGoalDto request
    ) {
        return ResponseEntity.ok(nutritionService.setGoal(userId, request));
    }

    @Operation(
            summary = "Get nutrition goal",
            description = "Retrieves the current daily nutrition goal for a user.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successfully retrieved goal"),
                    @ApiResponse(responseCode = "204", description = "No goal configured for this user"),
                    @ApiResponse(responseCode = "404", description = "User not found")
            }
    )
    @GetMapping("/goal/{userId}")
    public ResponseEntity<NutritionGoalDto> getGoal(@PathVariable UUID userId) {
        NutritionGoalDto goal = nutritionService.getGoal(userId);
        if (goal == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(goal);
    }

    @Operation(
            summary = "Log food entry",
            description = "Logs a food item or meal for the user.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successfully logged food"),
                    @ApiResponse(responseCode = "400", description = "Validation error"),
                    @ApiResponse(responseCode = "404", description = "User not found")
            }
    )
    @PostMapping("/log/{userId}")
    public ResponseEntity<FoodLogDto> logFood(
            @PathVariable UUID userId,
            @Valid @RequestBody FoodLogDto request
    ) {
        return ResponseEntity.ok(nutritionService.logFood(userId, request));
    }

    @Operation(
            summary = "Get daily food logs",
            description = "Retrieves all food entries for a user on a specific date (defaults to today).",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successfully retrieved daily food logs")
            }
    )
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
