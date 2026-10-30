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
@Table(name = "nutrition_plans", uniqueConstraints={@jakarta.persistence.UniqueConstraint(columnNames={"gym_id","id"}),@jakarta.persistence.UniqueConstraint(columnNames={"gym_id","category"})})
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper=true)
public class NutritionPlan extends TenantOwnedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private Integer calories;

    @Column(nullable = false)
    private Integer protein;

    @Column(nullable = false)
    private Integer carbs;

    @Column(nullable = false)
    private Integer fats;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "nutrition_plan_meals", joinColumns = {@JoinColumn(name = "plan_id", referencedColumnName="id"), @JoinColumn(name="gym_id", referencedColumnName="gym_id")})
    @Column(name = "meal")
    private List<String> meals;
}
