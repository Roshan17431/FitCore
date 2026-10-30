package com.fitcore.gym.service;

import com.fitcore.gym.domain.Role;
import com.fitcore.gym.domain.SessionStatus;
import com.fitcore.gym.dto.trainer.TrainerProfileDto;
import com.fitcore.gym.dto.trainer.TrainerSessionDto;
import com.fitcore.gym.entity.TrainerProfile;
import com.fitcore.gym.entity.TrainerSession;
import com.fitcore.gym.entity.User;
import com.fitcore.gym.exception.BadRequestException;
import com.fitcore.gym.exception.DuplicateResourceException;
import com.fitcore.gym.exception.ResourceNotFoundException;
import com.fitcore.gym.repository.TrainerProfileRepository;
import com.fitcore.gym.repository.TrainerSessionRepository;
import com.fitcore.gym.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrainerService {

    private final TrainerProfileRepository trainerProfileRepository;
    private final TrainerSessionRepository trainerSessionRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final Clock clock;

    public List<TrainerProfileDto> getAllTrainers() {
        return trainerProfileRepository.findAll().stream()
                .filter(profile -> profile.getUser().isEnabled())
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @PreAuthorize("@recordAccess.user(authentication, #memberId)")
    @Transactional
    public TrainerSessionDto bookSession(UUID memberId, Long trainerId, TrainerSessionDto request) {
        // Lock in this order for every booking: it serializes both member and trainer contention.
        User member = userRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found"));

        TrainerProfile trainer = trainerProfileRepository.findByIdForUpdate(trainerId)
                .orElseThrow(() -> new ResourceNotFoundException("Trainer not found"));

        if (!member.isEnabled() || !trainer.getUser().isEnabled()) {
            throw new BadRequestException("Both accounts must be active to book a trainer session");
        }

        if (trainer.getUser().getRole() != Role.TRAINER) {
            throw new BadRequestException("The selected account must have the trainer role");
        }

        validateTimes(request);
        TrainerAvailability.requireAvailable(trainer.getAvailableSlots(), request.getSessionDate(),
                request.getStartTime(), request.getEndTime());

        // Query after obtaining both locks, so a waiting booking sees the previous committed insert.
        if (trainerSessionRepository.existsScheduledOverlap(trainerId, memberId, request.getSessionDate(),
                request.getStartTime(), request.getEndTime(), SessionStatus.SCHEDULED)) {
            throw new DuplicateResourceException("The trainer or member already has a session during this time");
        }

        TrainerSession session = TrainerSession.builder().gymId(member.getGymId())
                .member(member)
                .trainer(trainer)
                .sessionDate(request.getSessionDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .status(SessionStatus.SCHEDULED)
                .build();

        return mapToDto(trainerSessionRepository.save(session));
    }

    private void validateTimes(TrainerSessionDto request) {
        if (request == null || request.getSessionDate() == null || request.getStartTime() == null || request.getEndTime() == null) {
            throw new BadRequestException("Session date, start time and end time are required");
        }
        // Hibernate's TIME binder and PostgreSQL can round fractional times differently.
        // Whole-second appointments retain exactly the interval checked for conflicts.
        if (request.getStartTime().getNano() != 0 || request.getEndTime().getNano() != 0) {
            throw new BadRequestException("Session times must not contain fractional seconds");
        }
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new BadRequestException("End time must be after start time on the same day");
        }
        if (!request.getSessionDate().atTime(request.getStartTime()).toInstant(ZoneOffset.UTC).isAfter(clock.instant())) {
            throw new BadRequestException("Session start must be in the future (UTC)");
        }
    }

    @PreAuthorize("@recordAccess.user(authentication, #memberId)")
    public List<TrainerSessionDto> getMemberSessions(UUID memberId) {
        User member = userRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        return trainerSessionRepository.findByMemberOrderBySessionDateAscStartTimeAsc(member).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public TrainerProfileDto mapToDto(TrainerProfile profile) {
        return TrainerProfileDto.builder()
                .id(profile.getId())
                .user(userService.mapToDto(profile.getUser()))
                .specialization(profile.getSpecialization())
                .experienceYears(profile.getExperienceYears())
                .bio(profile.getBio())
                .photoUrl(profile.getPhotoUrl())
                .certifications(profile.getCertifications())
                .availableSlots(profile.getAvailableSlots())
                .build();
    }

    public TrainerSessionDto mapToDto(TrainerSession session) {
        return TrainerSessionDto.builder()
                .id(session.getId())
                .member(userService.mapToDto(session.getMember()))
                .trainer(mapToDto(session.getTrainer()))
                .sessionDate(session.getSessionDate())
                .startTime(session.getStartTime())
                .endTime(session.getEndTime())
                .status(session.getStatus())
                .build();
    }
}
