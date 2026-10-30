package com.fitcore.gym.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.experimental.SuperBuilder;
import lombok.EqualsAndHashCode;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Entity
@Table(name = "membership_plans", uniqueConstraints={@jakarta.persistence.UniqueConstraint(columnNames={"gym_id","id"}),@jakarta.persistence.UniqueConstraint(columnNames={"gym_id","name"})})
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper=true)
public class MembershipPlan extends TenantOwnedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer durationMonths;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "membership_plan_features", joinColumns = {@JoinColumn(name = "plan_id", referencedColumnName="id"), @JoinColumn(name="gym_id", referencedColumnName="gym_id")})
    @Column(name = "feature")
    private List<String> features;

    @Builder.Default
    @Column(nullable = false)
    private Boolean recommended = false;
}
