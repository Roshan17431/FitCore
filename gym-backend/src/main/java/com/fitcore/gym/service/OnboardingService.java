package com.fitcore.gym.service;

import com.fitcore.gym.domain.*;
import com.fitcore.gym.dto.auth.*;
import com.fitcore.gym.dto.gym.*;
import com.fitcore.gym.dto.user.SessionUserDto;
import com.fitcore.gym.entity.*;
import com.fitcore.gym.exception.*;
import com.fitcore.gym.repository.*;
import com.fitcore.gym.security.*;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OnboardingService {
    private final GymRepository gyms;
    private final UserRepository users;
    private final GymInvitationRepository invitations;
    private final TrainerProfileRepository trainers;
    private final IdentityUniqueness uniqueness;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final TenantAccess access;
    private final PlatformTransactionManager transactions;
    private final Clock clock;

    @Value("${fitcore.onboarding.demo-enabled:false}")
    private boolean demo;

    record Created(User user, Gym gym) {}

    public record GymView(
            UUID id,
            String name,
            String slug,
            GymStatus status,
            long members,
            long trainers,
            long administrators) {}

    public record InvitationView(
            UUID id,
            String email,
            Role role,
            LocalDateTime expiresAt,
            LocalDateTime acceptedAt,
            LocalDateTime revokedAt) {}

    public record IssuedInvitation(
            UUID id, String invitationToken, LocalDateTime expiresAt, Role role) {}

    private void enabled() {
        if (!demo) throw new ForbiddenException("DEVELOPMENT_ONBOARDING_DISABLED");
    }

    private <T> T write(
            java.util.function.Function<org.springframework.transaction.TransactionStatus, T>
                    action) {
        var tx = new TransactionTemplate(transactions);
        tx.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            return tx.execute(action::apply);
        } catch (DataIntegrityViolationException collision) {
            throw new DuplicateResourceException(
                    "Account or gym conflicts with an existing record");
        }
    }

    private AuthResponse session(Created created) {
        return AuthResponse.builder()
                .token(jwt.generateToken(created.user()))
                .user(SessionUserDto.from(created.user(), created.gym()))
                .build();
    }

    public AuthResponse owner(OwnerRegistrationRequest request) {
        enabled();
        return session(
                write(
                        status -> {
                            password(request.password());
                            uniqueness.requireAvailable(request.email(), null);
                            Gym gym =
                                    gyms.save(
                                            Gym.builder()
                                                    .name(request.gymName().trim())
                                                    .slug(request.slug())
                                                    .status(GymStatus.ACTIVE)
                                                    .build());
                            User user =
                                    users.saveAndFlush(
                                            User.builder()
                                                    .gymId(gym.getId())
                                                    .fullName(request.fullName().trim())
                                                    .email(request.email())
                                                    .passwordHash(
                                                            encoder.encode(request.password()))
                                                    .role(Role.OWNER)
                                                    .enabled(true)
                                                    .build());
                            gym.setPrimaryOwner(user);
                            gyms.save(gym);
                            return new Created(user, gym);
                        }));
    }

    public GymView view() {
        var actor = TenantContext.current();
        var gym =
                gyms.findById(actor.gymId())
                        .orElseThrow(() -> new ResourceNotFoundException("Gym not found"));
        return new GymView(
                gym.getId(),
                gym.getName(),
                gym.getSlug(),
                gym.getStatus(),
                users.countByGymIdAndRole(gym.getId(), Role.MEMBER),
                users.countByGymIdAndRole(gym.getId(), Role.TRAINER),
                users.countByGymIdAndRole(gym.getId(), Role.ADMIN));
    }

    public GymView update(GymUpdateRequest request) {
        if (access.manager().role() != Role.OWNER) throw new ForbiddenException("OWNER required");
        write(
                status -> {
                    var g =
                            gyms.findByIdForUpdate(access.gym())
                                    .orElseThrow(
                                            () -> new ResourceNotFoundException("Gym not found"));
                    g.setName(request.name().trim());
                    gyms.save(g);
                    return true;
                });
        return view();
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static String digest(String token) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public IssuedInvitation invite(InvitationRequest request) {
        enabled();
        var actor = access.manager();
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return write(
                status -> {
                    gyms.findByIdForUpdate(actor.gymId())
                            .orElseThrow(() -> new ResourceNotFoundException("Gym not found"));
                    uniqueness.requireAvailable(request.email(), null);
                    var creator =
                            users.findByIdAndGymId(actor.userId(), actor.gymId())
                                    .orElseThrow(
                                            () -> new ResourceNotFoundException("User not found"));
                    var invite =
                            invitations.save(
                                    GymInvitation.builder()
                                            .gymId(actor.gymId())
                                            .email(request.email())
                                            .role(Role.valueOf(request.role().name()))
                                            .tokenHash(digest(secret))
                                            .createdBy(creator)
                                            .expiresAt(now().plusHours(48))
                                            .build());
                    return new IssuedInvitation(
                            invite.getId(), secret, invite.getExpiresAt(), invite.getRole());
                });
    }

    public java.util.List<InvitationView> list() {
        var actor = access.manager();
        return invitations.findByGymIdOrderByCreatedAtDesc(actor.gymId()).stream()
                .map(
                        i ->
                                new InvitationView(
                                        i.getId(),
                                        i.getEmail(),
                                        i.getRole(),
                                        i.getExpiresAt(),
                                        i.getAcceptedAt(),
                                        i.getRevokedAt()))
                .toList();
    }

    public void revoke(UUID id) {
        var actor = access.manager();
        write(
                status -> {
                    gyms.findByIdForUpdate(actor.gymId())
                            .orElseThrow(() -> new ResourceNotFoundException("Gym not found"));
                    var i =
                            invitations
                                    .findByIdAndGymIdForUpdate(id, actor.gymId())
                                    .orElseThrow(
                                            () ->
                                                    new ResourceNotFoundException(
                                                            "Invitation not found"));
                    if (i.getAcceptedAt() != null)
                        throw new BadRequestException("Invitation is already accepted");
                    if (i.getRevokedAt() == null) i.setRevokedAt(now());
                    invitations.save(i);
                    return true;
                });
    }

    public AuthResponse accept(RegisterRequest request) {
        if (request.getInvitationToken() == null)
            throw new ForbiddenException("INVITATION_REQUIRED");
        enabled();
        password(request.getPassword());
        if (!request.getInvitationToken().matches("[A-Za-z0-9_-]{43}")) throw invalid();
        return session(
                write(
                        status -> {
                            String hash = digest(request.getInvitationToken());
                            var gymId =
                                    invitations
                                            .findGymIdByTokenHash(hash)
                                            .orElseThrow(this::invalid);
                            var gym = gyms.findByIdForUpdate(gymId).orElseThrow(this::invalid);
                            var i =
                                    invitations
                                            .findByTokenHashForUpdate(hash)
                                            .orElseThrow(this::invalid);
                            if (gym.getStatus() != GymStatus.ACTIVE
                                    || i.getRevokedAt() != null
                                    || i.getAcceptedAt() != null
                                    || !i.getExpiresAt().isAfter(now())
                                    || !i.getEmail().equals(request.getEmail())
                                    || !(i.getRole() == Role.MEMBER || i.getRole() == Role.TRAINER))
                                throw invalid();
                            uniqueness.requireAvailable(request.getEmail(), null);
                            var user =
                                    users.saveAndFlush(
                                            User.builder()
                                                    .gymId(gym.getId())
                                                    .email(i.getEmail())
                                                    .fullName(request.getFullName())
                                                    .passwordHash(
                                                            encoder.encode(request.getPassword()))
                                                    .role(i.getRole())
                                                    .fitnessGoal(request.getFitnessGoal())
                                                    .enabled(true)
                                                    .build());
                            if (i.getRole() == Role.TRAINER)
                                trainers.save(
                                        TrainerProfile.builder()
                                                .gymId(gym.getId())
                                                .user(user)
                                                .specialization("General fitness")
                                                .experienceYears(0)
                                                .build());
                            i.setAcceptedAt(now());
                            invitations.save(i);
                            return new Created(user, gym);
                        }));
    }

    private void password(String value) {
        if (value == null
                || value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
                || value.length() < 12)
            throw new BadRequestException(
                    "Password must contain 12–64 characters and at most 72 UTF-8 bytes");
    }

    private BadRequestException invalid() {
        return new BadRequestException("Invalid or unavailable invitation");
    }
}
