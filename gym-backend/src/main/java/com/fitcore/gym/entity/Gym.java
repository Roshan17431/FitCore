package com.fitcore.gym.entity;
import com.fitcore.gym.domain.GymStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name="gyms") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Gym {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(nullable=false) private String name;
 @Column(nullable=false,unique=true) private String slug;
 @Enumerated(EnumType.STRING) @Column(nullable=false) private GymStatus status;
 // Deferred database checks permit construction in one transaction, never an ownerless commit.
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="primary_owner_user_id") private User primaryOwner;
 @CreationTimestamp @Column(updatable=false,nullable=false) private LocalDateTime createdAt;
 @UpdateTimestamp @Column(nullable=false) private LocalDateTime updatedAt;
}
