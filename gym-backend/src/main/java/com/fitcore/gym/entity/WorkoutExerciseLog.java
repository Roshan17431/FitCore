package com.fitcore.gym.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.experimental.SuperBuilder;
import lombok.EqualsAndHashCode;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "workout_exercise_logs", uniqueConstraints={@jakarta.persistence.UniqueConstraint(columnNames={"gym_id","id"})})
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper=true)
public class WorkoutExerciseLog extends TenantOwnedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workout_log_id", nullable = false)
    private WorkoutLog workoutLog;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercise_id", nullable = false)
    private Exercise exercise;

    private Integer setsCompleted;

    private String repsCompleted;

    private Double weightUsed; // Optional weight used during the exercise
}
