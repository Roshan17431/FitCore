package com.fitcore.gym.entity;

import com.fitcore.gym.domain.MealType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "food_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FoodLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String mealName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MealType mealType;

    @Column(nullable = false)
    private Integer calories;

    private Integer protein;
    private Integer carbs;
    private Integer fat;

    @Column(nullable = false)
    private LocalDate logDate;

    @Column(nullable = false)
    private LocalTime logTime;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
