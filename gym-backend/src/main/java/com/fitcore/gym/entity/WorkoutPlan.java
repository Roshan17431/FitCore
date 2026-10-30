package com.fitcore.gym.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.experimental.SuperBuilder;
import lombok.EqualsAndHashCode;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Table(name = "workout_plans", uniqueConstraints={@jakarta.persistence.UniqueConstraint(columnNames={"gym_id","id"}),@jakarta.persistence.UniqueConstraint(columnNames={"gym_id","level"})})
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper=true)
public class WorkoutPlan extends TenantOwnedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String level;

    @Column(nullable = false)
    private String duration;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "workout_plan_schedule", joinColumns = {@JoinColumn(name = "plan_id", referencedColumnName="id"), @JoinColumn(name="gym_id", referencedColumnName="gym_id")})
    @Column(name = "schedule_item")
    private List<String> schedule;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "workout_plan_exercises", joinColumns = {@JoinColumn(name = "plan_id", referencedColumnName="id"), @JoinColumn(name="gym_id", referencedColumnName="gym_id")})
    @Column(name = "exercise_name")
    private List<String> exercises;
}
