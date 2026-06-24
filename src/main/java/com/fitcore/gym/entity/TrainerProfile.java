package com.fitcore.gym.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "trainer_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private String specialization;

    @Column(nullable = false)
    private Integer experienceYears;

    @Column(columnDefinition = "TEXT")
    private String bio;
}
