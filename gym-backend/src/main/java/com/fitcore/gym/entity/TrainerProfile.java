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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.experimental.SuperBuilder;
import lombok.EqualsAndHashCode;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Table(name = "trainer_profiles", uniqueConstraints={@jakarta.persistence.UniqueConstraint(columnNames={"gym_id","id"})})
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper=true)
public class TrainerProfile extends TenantOwnedEntity {

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

    private String photoUrl;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "trainer_certifications", joinColumns = {@JoinColumn(name = "trainer_id", referencedColumnName="id"), @JoinColumn(name="gym_id", referencedColumnName="gym_id")})
    @Column(name = "certification")
    private List<String> certifications;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "trainer_available_slots", joinColumns = {@JoinColumn(name = "trainer_id", referencedColumnName="id"), @JoinColumn(name="gym_id", referencedColumnName="gym_id")})
    @Column(name = "slot")
    private List<String> availableSlots;
}
