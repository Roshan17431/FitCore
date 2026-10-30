package com.fitcore.gym.entity;
import com.fitcore.gym.domain.Role;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name="gym_invitations") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
public class GymInvitation extends TenantOwnedEntity {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(nullable=false) private String email;
 @Column(nullable=false,unique=true) private String tokenHash;
 @Enumerated(EnumType.STRING) @Column(nullable=false) @Builder.Default private Role role=Role.MEMBER;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="created_by_user_id",nullable=false) private User createdBy;
 @Column(nullable=false) private LocalDateTime expiresAt;
 private LocalDateTime acceptedAt;
 private LocalDateTime revokedAt;
 @CreationTimestamp @Column(updatable=false,nullable=false) private LocalDateTime createdAt;
}
