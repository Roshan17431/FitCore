package com.fitcore.gym.controller;

import com.fitcore.gym.dto.workout.ExerciseDto;
import com.fitcore.gym.dto.workout.WorkoutLogDto;
import com.fitcore.gym.service.WorkoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/workouts")
@RequiredArgsConstructor
@Tag(name = "Workout", description = "Endpoints for managing exercises and workout logs")
@SecurityRequirement(name = "Bearer Authentication")
public class WorkoutController {

    private final WorkoutService workoutService;

    @Operation(
            summary = "Get all exercises",
            description = "Retrieves a list of all available exercises in the system.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successfully retrieved exercises")
            }
    )
    @GetMapping("/exercises")
    public ResponseEntity<List<ExerciseDto>> getAllExercises() {
        return ResponseEntity.ok(workoutService.getAllExercises());
    }

    @Operation(
            summary = "Get user workout logs",
            description = "Retrieves all past workout logs for a specific user.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successfully retrieved workout logs")
            }
    )
    @GetMapping("/logs/{userId}")
    public ResponseEntity<List<WorkoutLogDto>> getUserWorkoutLogs(@PathVariable UUID userId) {
        return ResponseEntity.ok(workoutService.getUserWorkoutLogs(userId));
    }

    @Operation(
            summary = "Log a new workout",
            description = "Saves a new workout log with the associated exercises and sets.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Workout successfully logged"),
                    @ApiResponse(responseCode = "400", description = "Validation error"),
                    @ApiResponse(responseCode = "404", description = "User not found")
            }
    )
    @PostMapping("/logs/{userId}")
    public ResponseEntity<WorkoutLogDto> logWorkout(
            @PathVariable UUID userId,
            @Valid @RequestBody WorkoutLogDto request
    ) {
        return ResponseEntity.ok(workoutService.logWorkout(userId, request));
    }
}
