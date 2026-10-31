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
import com.fitcore.gym.exception.ResourceNotFoundException;
import com.fitcore.gym.repository.ExerciseRepository;
import com.fitcore.gym.repository.UserRepository;
import com.fitcore.gym.repository.WorkoutExerciseLogRepository;
import com.fitcore.gym.repository.WorkoutLogRepository;
import com.fitcore.gym.repository.WorkoutPlanRepository;
import com.fitcore.gym.security.TenantAccess;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@org.springframework.transaction.annotation.Transactional
@RequiredArgsConstructor
public class WorkoutService {
    private final TenantAccess tenantAccess;

    private final WorkoutLogRepository workoutLogRepository;
    private final WorkoutExerciseLogRepository workoutExerciseLogRepository;
    private final ExerciseRepository exerciseRepository;
    private final UserRepository userRepository;
    private final WorkoutPlanRepository workoutPlanRepository;

    public List<ExerciseDto> getAllExercises() {
        return exerciseRepository.findAllByGymId(tenantAccess.gym()).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public List<WorkoutPlanDto> getWorkoutPlans() {
        return workoutPlanRepository.findAllByGymId(tenantAccess.gym()).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @PreAuthorize("@recordAccess.user(authentication, #userId)")
    public List<WorkoutLogDto> getUserWorkoutLogs(UUID userId) {
        User user = tenantAccess.requireUser(userId);

        return workoutLogRepository
                .findByGymIdAndUserIdOrderByDateDesc(tenantAccess.gym(), user.getId())
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @PreAuthorize("@recordAccess.user(authentication, #userId)")
    public WorkoutLogDto logWorkout(UUID userId, WorkoutLogDto request) {
        User user = tenantAccess.requireUser(userId);

        if (request.getExercises() != null)
            for (var entry : request.getExercises()) {
                if (entry == null
                        || entry.getExercise() == null
                        || entry.getExercise().getId() == null)
                    throw new com.fitcore.gym.exception.BadRequestException(
                            "Exercise reference is required");
                exerciseRepository
                        .findByIdAndGymId(entry.getExercise().getId(), tenantAccess.gym())
                        .orElseThrow(() -> new ResourceNotFoundException("Exercise not found"));
            }
        WorkoutLog workoutLog =
                WorkoutLog.builder()
                        .gymId(user.getGymId())
                        .user(user)
                        .date(request.getDate() != null ? request.getDate() : LocalDate.now())
                        .durationMinutes(request.getDurationMinutes())
                        .notes(request.getNotes())
                        .build();

        workoutLog = workoutLogRepository.save(workoutLog);

        if (request.getExercises() != null && !request.getExercises().isEmpty()) {
            for (WorkoutExerciseLogDto exDto : request.getExercises()) {
                Exercise exercise =
                        exerciseRepository
                                .findByIdAndGymId(exDto.getExercise().getId(), tenantAccess.gym())
                                .orElseThrow(
                                        () -> new ResourceNotFoundException("Exercise not found"));

                WorkoutExerciseLog exLog =
                        WorkoutExerciseLog.builder()
                                .gymId(workoutLog.getGymId())
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
        tenantAccess.requireOwned(exercise);
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
        tenantAccess.requireOwned(workoutLog);
        List<WorkoutExerciseLogDto> exercises =
                workoutExerciseLogRepository
                        .findByGymIdAndWorkoutLogId(tenantAccess.gym(), workoutLog.getId())
                        .stream()
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
        tenantAccess.requireOwned(exLog);
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
        tenantAccess.requireOwned(plan);
        return WorkoutPlanDto.builder()
                .id(plan.getId())
                .level(plan.getLevel())
                .duration(plan.getDuration())
                .schedule(plan.getSchedule())
                .exercises(plan.getExercises())
                .build();
    }
}
