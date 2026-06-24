package com.fitcore.gym.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "progress_metrics")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProgressMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDate logDate;

    private Double weight; // in kg
    private Double bodyFatPercentage;
    
    // Measurements in cm
    private Double chest;
    private Double waist;
    private Double arms;
    private Double legs;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
