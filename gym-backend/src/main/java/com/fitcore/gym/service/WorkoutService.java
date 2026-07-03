package com.fitcore.gym.service;

import com.fitcore.gym.dto.workout.ExerciseDto;
import com.fitcore.gym.dto.workout.WorkoutExerciseLogDto;
import com.fitcore.gym.dto.workout.WorkoutLogDto;
import com.fitcore.gym.dto.workout.WorkoutPlanDto;
import com.fitcore.gym.entity.Exercise;
import com.fitcore.gym.entity.User;
import com.fitcore.gym.entity.WorkoutExerciseLog;
import com.fitcore.gym.entity.WorkoutLog;
import com.fitcore.gym.entity.WorkoutPlan;
import com.fitcore.gym.repository.ExerciseRepository;
import com.fitcore.gym.repository.UserRepository;
import com.fitcore.gym.repository.WorkoutExerciseLogRepository;
import com.fitcore.gym.repository.WorkoutLogRepository;
import com.fitcore.gym.repository.WorkoutPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkoutService {

    private final WorkoutLogRepository workoutLogRepository;
    private final WorkoutExerciseLogRepository workoutExerciseLogRepository;
    private final ExerciseRepository exerciseRepository;
    private final UserRepository userRepository;
    private final WorkoutPlanRepository workoutPlanRepository;

    public List<ExerciseDto> getAllExercises() {
        return exerciseRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public List<WorkoutPlanDto> getWorkoutPlans() {
        return workoutPlanRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public List<WorkoutLogDto> getUserWorkoutLogs(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return workoutLogRepository.findByUserOrderByDateDesc(user).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public WorkoutLogDto logWorkout(UUID userId, WorkoutLogDto request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        WorkoutLog workoutLog = WorkoutLog.builder()
                .user(user)
                .date(request.getDate() != null ? request.getDate() : LocalDate.now())
                .durationMinutes(request.getDurationMinutes())
                .notes(request.getNotes())
                .build();

        workoutLog = workoutLogRepository.save(workoutLog);

        if (request.getExercises() != null && !request.getExercises().isEmpty()) {
            for (WorkoutExerciseLogDto exDto : request.getExercises()) {
                Exercise exercise = exerciseRepository.findById(exDto.getExercise().getId())
                        .orElseThrow(() -> new RuntimeException("Exercise not found"));

                WorkoutExerciseLog exLog = WorkoutExerciseLog.builder()
                        .workoutLog(workoutLog)
                        .exercise(exercise)
                        .setsCompleted(exDto.getSetsCompleted())
                        .repsCompleted(exDto.getRepsCompleted())
                        .weightUsed(exDto.getWeightUsed())
                        .build();

                workoutExerciseLogRepository.save(exLog);
            }
        }

        return mapToDto(workoutLog);
    }

    private ExerciseDto mapToDto(Exercise exercise) {
        return ExerciseDto.builder()
                .id(exercise.getId())
                .name(exercise.getName())
                .category(exercise.getCategory())
                .difficulty(exercise.getDifficulty())
                .recommendedSets(exercise.getRecommendedSets())
                .recommendedReps(exercise.getRecommendedReps())
                .description(exercise.getDescription())
                .imageUrl(exercise.getImageUrl())
                .build();
    }

    private WorkoutLogDto mapToDto(WorkoutLog workoutLog) {
        List<WorkoutExerciseLogDto> exercises = workoutExerciseLogRepository.findByWorkoutLog(workoutLog).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());

        return WorkoutLogDto.builder()
                .id(workoutLog.getId())
                .userId(workoutLog.getUser().getId())
                .date(workoutLog.getDate())
                .durationMinutes(workoutLog.getDurationMinutes())
                .notes(workoutLog.getNotes())
                .exercises(exercises)
                .build();
    }

    private WorkoutExerciseLogDto mapToDto(WorkoutExerciseLog exLog) {
        return WorkoutExerciseLogDto.builder()
                .id(exLog.getId())
                .workoutLogId(exLog.getWorkoutLog().getId())
                .exercise(mapToDto(exLog.getExercise()))
                .setsCompleted(exLog.getSetsCompleted())
                .repsCompleted(exLog.getRepsCompleted())
                .weightUsed(exLog.getWeightUsed())
                .build();
    }

    private WorkoutPlanDto mapToDto(WorkoutPlan plan) {
        return WorkoutPlanDto.builder()
                .id(plan.getId())
                .level(plan.getLevel())
                .duration(plan.getDuration())
                .schedule(plan.getSchedule())
                .exercises(plan.getExercises())
                .build();
    }
}
