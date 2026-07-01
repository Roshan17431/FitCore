package com.fitcore.gym.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "nutrition_goals")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NutritionGoal {

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
