package com.fitcore.gym.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.experimental.SuperBuilder;
import lombok.EqualsAndHashCode;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "nutrition_goals", uniqueConstraints={@jakarta.persistence.UniqueConstraint(columnNames={"gym_id","id"})})
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper=true)
public class NutritionGoal extends TenantOwnedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    private Integer targetCalories;
    private Integer targetProtein; // in grams
    private Integer targetCarbs;   // in grams
    private Integer targetFat;     // in grams

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
