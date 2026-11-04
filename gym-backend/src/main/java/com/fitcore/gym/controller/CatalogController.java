package com.fitcore.gym.controller;

import com.fitcore.gym.dto.catalog.CatalogDtos.*;
import com.fitcore.gym.service.CatalogService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin/catalog") @RequiredArgsConstructor
public class CatalogController {
    private final CatalogService catalogs;
    @GetMapping("/exercises") public List<ExerciseView> exercises(){return catalogs.exercises();}
    @GetMapping("/exercises/{id}") public ExerciseView exercise(@PathVariable Long id){return catalogs.exercise(id);}
    @PostMapping("/exercises") public ResponseEntity<ExerciseView> createExercise(@Valid @RequestBody ExerciseInput r){return ResponseEntity.status(201).body(catalogs.createExercise(r));}
    @PutMapping("/exercises/{id}") public ExerciseView updateExercise(@PathVariable Long id,@Valid @RequestBody ExerciseInput r){return catalogs.updateExercise(id,r);}
    @PostMapping("/exercises/{id}/archive") public ExerciseView archiveExercise(@PathVariable Long id){return catalogs.archiveExercise(id);}
    @GetMapping("/workouts") public List<WorkoutView> workouts(){return catalogs.workouts();}
    @GetMapping("/workouts/{id}") public WorkoutView workout(@PathVariable Long id){return catalogs.workout(id);}
    @PostMapping("/workouts") public ResponseEntity<WorkoutView> createWorkout(@Valid @RequestBody WorkoutInput r){return ResponseEntity.status(201).body(catalogs.createWorkout(r));}
    @PutMapping("/workouts/{id}") public WorkoutView updateWorkout(@PathVariable Long id,@Valid @RequestBody WorkoutInput r){return catalogs.updateWorkout(id,r);}
    @PostMapping("/workouts/{id}/publish") public WorkoutView publishWorkout(@PathVariable Long id){return catalogs.publishWorkout(id);}
    @PostMapping("/workouts/{id}/archive") public WorkoutView archiveWorkout(@PathVariable Long id){return catalogs.archiveWorkout(id);}
    @GetMapping("/nutrition") public List<NutritionView> nutrition(){return catalogs.nutrition();}
    @GetMapping("/nutrition/{id}") public NutritionView nutrition(@PathVariable Long id){return catalogs.nutrition(id);}
    @PostMapping("/nutrition") public ResponseEntity<NutritionView> createNutrition(@Valid @RequestBody NutritionInput r){return ResponseEntity.status(201).body(catalogs.createNutrition(r));}
    @PutMapping("/nutrition/{id}") public NutritionView updateNutrition(@PathVariable Long id,@Valid @RequestBody NutritionInput r){return catalogs.updateNutrition(id,r);}
    @PostMapping("/nutrition/{id}/archive") public NutritionView archiveNutrition(@PathVariable Long id){return catalogs.archiveNutrition(id);}
}
