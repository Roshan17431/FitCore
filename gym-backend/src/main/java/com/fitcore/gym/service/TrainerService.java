package com.fitcore.gym.service;

import com.fitcore.gym.domain.SessionStatus;
import com.fitcore.gym.dto.trainer.TrainerProfileDto;
import com.fitcore.gym.dto.trainer.TrainerSessionDto;
import com.fitcore.gym.entity.TrainerProfile;
import com.fitcore.gym.entity.TrainerSession;
import com.fitcore.gym.entity.User;
import com.fitcore.gym.repository.TrainerProfileRepository;
import com.fitcore.gym.repository.TrainerSessionRepository;
import com.fitcore.gym.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

    public List<TrainerProfileDto> getAllTrainers() {
        return trainerProfileRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public TrainerSessionDto bookSession(UUID memberId, Long trainerId, TrainerSessionDto request) {
        User member = userRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        TrainerProfile trainer = trainerProfileRepository.findById(trainerId)
                .orElseThrow(() -> new RuntimeException("Trainer not found"));

        TrainerSession session = TrainerSession.builder()
                .member(member)
                .trainer(trainer)
                .sessionDate(request.getSessionDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .status(SessionStatus.SCHEDULED)
                .build();

        return mapToDto(trainerSessionRepository.save(session));
    }

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
