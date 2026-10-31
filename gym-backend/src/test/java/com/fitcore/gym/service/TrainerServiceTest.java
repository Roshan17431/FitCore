package com.fitcore.gym.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fitcore.gym.domain.Role;
import com.fitcore.gym.dto.trainer.TrainerSessionDto;
import com.fitcore.gym.entity.TrainerProfile;
import com.fitcore.gym.entity.User;
import com.fitcore.gym.exception.BadRequestException;
import com.fitcore.gym.repository.TrainerProfileRepository;
import com.fitcore.gym.repository.TrainerSessionRepository;
import com.fitcore.gym.repository.UserRepository;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class TrainerServiceTest {
    private final TrainerProfileRepository profiles = mock(TrainerProfileRepository.class);
    private final TrainerSessionRepository sessions = mock(TrainerSessionRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final com.fitcore.gym.security.TenantAccess access =
            mock(com.fitcore.gym.security.TenantAccess.class);
    private final UUID gym = UUID.randomUUID();

    @org.junit.jupiter.api.BeforeEach
    void tenant() {
        when(access.gym()).thenReturn(gym);
    }

    private final TrainerService service =
            new TrainerService(
                    access,
                    profiles,
                    sessions,
                    users,
                    new UserService(access, users),
                    Clock.fixed(Instant.parse("2030-06-03T10:00:00Z"), ZoneOffset.UTC));

    @Test
    void disabledTrainerIsExcludedFromPublicList() {
        var trainer = TrainerProfile.builder().user(User.builder().enabled(false).build()).build();
        when(profiles.findAllByGymId(gym)).thenReturn(List.of(trainer));
        assertTrue(service.getAllTrainers().isEmpty());
    }

    @Test
    void disabledTrainerCannotReceiveNewBookings() {
        var memberId = UUID.randomUUID();
        when(users.findByIdAndGymIdForUpdate(memberId, gym))
                .thenReturn(Optional.of(User.builder().id(memberId).enabled(true).build()));
        when(profiles.findByIdAndGymIdForUpdate(1L, gym))
                .thenReturn(
                        Optional.of(
                                TrainerProfile.builder()
                                        .id(1L)
                                        .user(
                                                User.builder()
                                                        .role(Role.TRAINER)
                                                        .enabled(false)
                                                        .build())
                                        .build()));
        var request =
                TrainerSessionDto.builder()
                        .sessionDate(LocalDate.of(2030, 6, 4))
                        .startTime(LocalTime.of(10, 0))
                        .endTime(LocalTime.of(11, 0))
                        .build();
        when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertThrows(BadRequestException.class, () -> service.bookSession(memberId, 1L, request));
    }
}
