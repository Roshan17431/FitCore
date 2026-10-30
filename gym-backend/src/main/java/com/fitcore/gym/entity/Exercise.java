package com.fitcore.gym.entity;

import com.fitcore.gym.domain.DifficultyLevel;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.experimental.SuperBuilder;
import lombok.EqualsAndHashCode;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "exercises", uniqueConstraints={@jakarta.persistence.UniqueConstraint(columnNames={"gym_id","id"}),@jakarta.persistence.UniqueConstraint(columnNames={"gym_id","name"})})
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper=true)
public class Exercise extends TenantOwnedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String category; // e.g., Chest, Back, Legs

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DifficultyLevel difficulty;

    private Integer recommendedSets;

    private String recommendedReps;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String imageUrl;
}
