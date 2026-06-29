package com.fitcore.gym.controller;

import com.fitcore.gym.dto.workout.ExerciseDto;
import com.fitcore.gym.dto.workout.WorkoutLogDto;
import com.fitcore.gym.service.WorkoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workouts")
@RequiredArgsConstructor
public class WorkoutController {

    private final WorkoutService workoutService;

    @GetMapping("/exercises")
    public ResponseEntity<List<ExerciseDto>> getAllExercises() {
        return ResponseEntity.ok(workoutService.getAllExercises());
    }

    @GetMapping("/logs/{userId}")
    public ResponseEntity<List<WorkoutLogDto>> getUserWorkoutLogs(@PathVariable UUID userId) {
        return ResponseEntity.ok(workoutService.getUserWorkoutLogs(userId));
    }

    @PostMapping("/logs/{userId}")
    public ResponseEntity<WorkoutLogDto> logWorkout(
            @PathVariable UUID userId,
            @RequestBody WorkoutLogDto request
    ) {
        return ResponseEntity.ok(workoutService.logWorkout(userId, request));
    }
}
