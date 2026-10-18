package com.fitcore.gym.service;

import com.fitcore.gym.dto.trainer.TrainerSessionDto;
import com.fitcore.gym.entity.TrainerProfile;
import com.fitcore.gym.entity.User;
import com.fitcore.gym.exception.BadRequestException;
import com.fitcore.gym.repository.TrainerProfileRepository;
import com.fitcore.gym.repository.TrainerSessionRepository;
import com.fitcore.gym.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class TrainerServiceTest {
    private final TrainerProfileRepository profiles = mock(TrainerProfileRepository.class);
    private final TrainerSessionRepository sessions = mock(TrainerSessionRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final TrainerService service = new TrainerService(profiles, sessions, users, new UserService(users));

    @Test
    void disabledTrainerIsExcludedFromPublicList() {
        var trainer = TrainerProfile.builder().user(User.builder().enabled(false).build()).build();
        when(profiles.findAll()).thenReturn(List.of(trainer));
        assertTrue(service.getAllTrainers().isEmpty());
    }

    @Test
    void disabledTrainerCannotReceiveNewBookings() {
        var memberId = UUID.randomUUID();
        when(users.findById(memberId)).thenReturn(Optional.of(User.builder().id(memberId).enabled(true).build()));
        when(profiles.findById(1L)).thenReturn(Optional.of(
                TrainerProfile.builder().id(1L).user(User.builder().enabled(false).build()).build()));
        var request = TrainerSessionDto.builder().sessionDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(10, 0)).endTime(LocalTime.of(11, 0)).build();
        when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertThrows(BadRequestException.class, () -> service.bookSession(memberId, 1L, request));
    }
}
