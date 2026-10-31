package com.fitcore.gym.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitcore.gym.domain.Role;
import com.fitcore.gym.domain.SessionStatus;
import com.fitcore.gym.entity.TrainerProfile;
import com.fitcore.gym.entity.TrainerSession;
import com.fitcore.gym.entity.User;
import com.fitcore.gym.fixture.FixtureTrainerProfileRepository;
import com.fitcore.gym.fixture.FixtureTrainerSessionRepository;
import com.fitcore.gym.fixture.FixtureUserRepository;
import com.fitcore.gym.security.JwtService;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@org.junit.jupiter.api.extension.ExtendWith(com.fitcore.gym.tenancy.RegressionGym.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(BookingRegressionTest.BookingClock.class)
class BookingRegressionTest {
    private static final LocalDate BOOKING_DATE = LocalDate.of(2030, 6, 4); // Tuesday
    private static final String PASSWORD_HASH =
            new BCryptPasswordEncoder().encode("Private booking fixture 2030!");

    @TestConfiguration
    static class BookingClock {
        @Bean
        @Primary
        Clock bookingTestClock() {
            // The non-UTC clock zone catches accidental use of LocalDate.now(clock) as the business
            // zone.
            return Clock.fixed(Instant.parse("2030-06-03T10:00:00Z"), ZoneOffset.ofHours(14));
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired FixtureUserRepository users;
    @Autowired FixtureTrainerProfileRepository trainers;
    @Autowired FixtureTrainerSessionRepository sessions;
    @Autowired EntityManager entityManager;

    private User member;
    private User otherMember;
    private User admin;
    private TrainerProfile trainer;
    private TrainerProfile otherTrainer;

    @BeforeEach
    void fixtures() {
        member = user(Role.MEMBER);
        otherMember = user(Role.MEMBER);
        admin = user(Role.ADMIN);
        trainer = trainer();
        otherTrainer = trainer();
    }

    @AfterEach
    void removeCommittedConcurrencyFixtures() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            sessions.deleteAll(sessions.findByTrainer(trainer));
            sessions.deleteAll(sessions.findByTrainer(otherTrainer));
            trainers.deleteAll(List.of(trainer, otherTrainer));
            users.deleteAll(
                    List.of(member, otherMember, admin, trainer.getUser(), otherTrainer.getUser()));
        }
    }

    @Test
    void successfulBookingPersistsTheAuthorizedMemberAndTrainerWithScheduledStatus()
            throws Exception {
        book(member, trainer, BOOKING_DATE, "10:00", "11:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.member.id").value(member.getId().toString()))
                .andExpect(jsonPath("$.trainer.id").value(trainer.getId()))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
        var stored = sessions.findByMember(member);
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getStartTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(stored.get(0).getEndTime()).isEqualTo(LocalTime.of(11, 0));
    }

    @ParameterizedTest
    @CsvSource({"10:00,11:00", "09:30,10:30", "10:30,11:30", "09:00,12:00", "10:15,10:45"})
    void scheduledTrainerOverlapIsRejectedIncludingExactAndContainingIntervals(
            String start, String end) throws Exception {
        existing(otherMember, trainer, BOOKING_DATE, "10:00", "11:00", SessionStatus.SCHEDULED);
        book(member, trainer, BOOKING_DATE, start, end).andExpect(status().isConflict());
        assertThat(sessions.findByMember(member)).isEmpty();
        assertThat(sessions.findByTrainer(trainer)).hasSize(1);
    }

    @ParameterizedTest
    @CsvSource({"09:00,10:00", "11:00,12:00"})
    void adjacentTrainerBookingsAreAllowed(String start, String end) throws Exception {
        existing(otherMember, trainer, BOOKING_DATE, "10:00", "11:00", SessionStatus.SCHEDULED);
        book(member, trainer, BOOKING_DATE, start, end).andExpect(status().isOk());
        assertThat(sessions.findByTrainer(trainer)).hasSize(2);
    }

    @ParameterizedTest
    @CsvSource({"10:00,11:00", "09:30,10:30", "10:30,11:30", "09:00,12:00", "10:15,10:45"})
    void memberCannotOverlapTheirOwnSessionWithADifferentTrainer(String start, String end)
            throws Exception {
        existing(member, otherTrainer, BOOKING_DATE, "10:00", "11:00", SessionStatus.SCHEDULED);
        book(member, trainer, BOOKING_DATE, start, end).andExpect(status().isConflict());
        assertThat(sessions.findByMember(member)).hasSize(1);
        assertThat(sessions.findByTrainer(trainer)).isEmpty();
    }

    @Test
    void sameMemberMayBookAnAdjacentSessionWithADifferentTrainer() throws Exception {
        existing(member, otherTrainer, BOOKING_DATE, "10:00", "11:00", SessionStatus.SCHEDULED);
        book(member, trainer, BOOKING_DATE, "11:00", "12:00").andExpect(status().isOk());
        assertThat(sessions.findByMember(member)).hasSize(2);
    }

    @ParameterizedTest
    @EnumSource(
            value = SessionStatus.class,
            names = {"CANCELLED", "COMPLETED"})
    void nonScheduledSessionsReleaseBothTrainerAndMemberSlots(SessionStatus oldStatus)
            throws Exception {
        existing(otherMember, trainer, BOOKING_DATE, "10:00", "11:00", oldStatus);
        existing(member, otherTrainer, BOOKING_DATE, "10:00", "11:00", oldStatus);
        book(member, trainer, BOOKING_DATE, "10:00", "11:00").andExpect(status().isOk());
        assertThat(sessions.findByMember(member)).hasSize(2);
    }

    @Test
    void sameTimeOnADifferentDateDoesNotConflict() throws Exception {
        existing(
                member,
                trainer,
                BOOKING_DATE.minusDays(1),
                "10:00",
                "11:00",
                SessionStatus.SCHEDULED);
        book(member, trainer, BOOKING_DATE, "10:00", "11:00").andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "09:00-12:00",
                "Tue 09:00-12:00",
                "Tuesday: 09:00-12:00",
                "TUESDAY 09:00-12:00"
            })
    void publishedAvailabilityAllowsItsExactBoundaries(String window) throws Exception {
        availability(List.of(window));
        book(member, trainer, BOOKING_DATE, "09:00", "12:00").andExpect(status().isOk());
    }

    @ParameterizedTest
    @CsvSource({"08:59,10:00", "11:00,12:01", "08:00,13:00"})
    void sessionMustFitEntirelyInsideAnAvailabilityWindow(String start, String end)
            throws Exception {
        availability(List.of("09:00-12:00"));
        book(member, trainer, BOOKING_DATE, start, end).andExpect(status().isBadRequest());
        assertThat(sessions.findByMember(member)).isEmpty();
    }

    @Test
    void weekdayAvailabilityDoesNotApplyToOtherDays() throws Exception {
        availability(List.of("Mon 09:00-12:00"));
        book(member, trainer, BOOKING_DATE, "10:00", "11:00").andExpect(status().isBadRequest());
    }

    @Test
    void gapsBetweenPublishedWindowsCannotBeBooked() throws Exception {
        availability(List.of("09:00-10:00", "11:00-12:00"));
        book(member, trainer, BOOKING_DATE, "09:30", "11:30").andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "any time",
                "25:00-26:00",
                "12:00-09:00",
                "10:00-10:00",
                "Funday 09:00-12:00"
            })
    void malformedPublishedAvailabilityFailsClosedEvenAlongsideAValidWindow(String invalidWindow)
            throws Exception {
        availability(List.of("09:00-12:00", invalidWindow));
        book(member, trainer, BOOKING_DATE, "10:00", "11:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
        assertThat(sessions.findByMember(member)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "\"sessionDate\":null,\"startTime\":\"10:00\",\"endTime\":\"11:00\"",
                "\"sessionDate\":\"2030-06-04\",\"startTime\":null,\"endTime\":\"11:00\"",
                "\"sessionDate\":\"2030-06-04\",\"startTime\":\"10:00\",\"endTime\":null",
                "\"sessionDate\":\"invalid\",\"startTime\":\"10:00\",\"endTime\":\"11:00\"",
                "\"sessionDate\":\"2030-06-04\",\"startTime\":\"25:00\",\"endTime\":\"11:00\""
            })
    void missingOrMalformedDatesAndTimesReturn400WithoutBooking(String fields) throws Exception {
        perform(member, trainer, body(member, trainer, fields)).andExpect(status().isBadRequest());
        assertThat(sessions.findByMember(member)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"11:00,10:00", "10:00,10:00", "23:00,01:00"})
    void reversedZeroLengthAndOvernightIntervalsAreRejected(String start, String end)
            throws Exception {
        book(member, trainer, BOOKING_DATE, start, end).andExpect(status().isBadRequest());
        assertThat(sessions.findByMember(member)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
        "10:00:00.000000001,10:00:00.000000002",
        "10:00:00.000001,10:00:00.000002",
        "10:00:00.001,11:00:00"
    })
    void fractionalSecondAppointmentTimesAreRejectedWithoutPersistingABooking(
            String start, String end) throws Exception {
        book(member, trainer, BOOKING_DATE, start, end).andExpect(status().isBadRequest());
        assertThat(sessions.findByMember(member)).isEmpty();
    }

    @Test
    void wholeSecondAppointmentTimesArePreservedInTheDatabase() throws Exception {
        book(member, trainer, BOOKING_DATE, "10:00:01", "11:00:01").andExpect(status().isOk());
        sessions.flush();
        entityManager.clear();
        var stored = sessions.findByMember(member);
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getStartTime()).isEqualTo(LocalTime.of(10, 0, 1));
        assertThat(stored.get(0).getEndTime()).isEqualTo(LocalTime.of(11, 0, 1));
    }

    @ParameterizedTest
    @CsvSource({"2030-06-02,11:00,12:00", "2030-06-03,09:59,11:00", "2030-06-03,10:00,11:00"})
    void sessionStartMustBeStrictlyAfterTheCurrentUtcInstant(
            LocalDate date, String start, String end) throws Exception {
        book(member, trainer, date, start, end).andExpect(status().isBadRequest());
        assertThat(sessions.findByMember(member)).isEmpty();
    }

    @Test
    void futureSessionOnTheCurrentUtcDayIsAllowedRegardlessOfClockZone() throws Exception {
        book(member, trainer, LocalDate.of(2030, 6, 3), "10:01", "11:00")
                .andExpect(status().isOk());
    }

    @Test
    void disabledTrainerCannotBeBooked() throws Exception {
        trainer.getUser().setEnabled(false);
        users.saveAndFlush(trainer.getUser());
        book(member, trainer, BOOKING_DATE, "10:00", "11:00").andExpect(status().isBadRequest());
        assertThat(sessions.findByMember(member)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(
            value = Role.class,
            names = {"MEMBER", "ADMIN"})
    void trainerProfileWhoseAccountIsNoLongerATrainerCannotBeBooked(Role role) throws Exception {
        trainer.getUser().setRole(role);
        users.saveAndFlush(trainer.getUser());
        book(member, trainer, BOOKING_DATE, "10:00", "11:00").andExpect(status().isBadRequest());
        assertThat(sessions.findByMember(member)).isEmpty();
    }

    @Test
    void administratorsCannotBookForADisabledMember() throws Exception {
        member.setEnabled(false);
        users.saveAndFlush(member);
        perform(
                        admin,
                        trainer,
                        body(member, trainer, fields(BOOKING_DATE, "10:00", "11:00")),
                        member)
                .andExpect(status().isBadRequest());
        assertThat(sessions.findByMember(member)).isEmpty();
    }

    @Test
    void bodyIdentitiesAndStatusCannotOverrideTheAuthorizedPath() throws Exception {
        String payload =
                body(
                        otherMember,
                        otherTrainer,
                        fields(BOOKING_DATE, "10:00", "11:00") + ",\"status\":\"CANCELLED\"");
        perform(member, trainer, payload).andExpect(status().isOk());
        assertThat(sessions.findByMember(otherMember)).isEmpty();
        var stored = sessions.findByMember(member);
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getTrainer().getId()).isEqualTo(trainer.getId());
        assertThat(stored.get(0).getStatus()).isEqualTo(SessionStatus.SCHEDULED);
    }

    @Test
    void anotherMembersSessionsCannotBeReadOrBooked() throws Exception {
        mvc.perform(
                        get("/api/trainers/sessions/" + otherMember.getId())
                                .header("Authorization", "Bearer " + jwt.generateToken(member)))
                .andExpect(status().isForbidden());
        perform(
                        member,
                        trainer,
                        body(otherMember, trainer, fields(BOOKING_DATE, "10:00", "11:00")),
                        otherMember)
                .andExpect(status().isForbidden());
        assertThat(sessions.findByMember(otherMember)).isEmpty();
    }

    @Test
    void unauthenticatedAndDisabledTokensCannotBook() throws Exception {
        String payload = body(member, trainer, fields(BOOKING_DATE, "10:00", "11:00"));
        mvc.perform(
                        post(path(member, trainer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                .andExpect(status().isUnauthorized());
        String previousToken = jwt.generateToken(member);
        member.setEnabled(false);
        users.saveAndFlush(member);
        mvc.perform(
                        post(path(member, trainer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload)
                                .header("Authorization", "Bearer " + previousToken))
                .andExpect(status().isUnauthorized());
        assertThat(sessions.findByMember(member)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentConflictingRequestsPersistExactlyOneBooking(boolean sameTrainer)
            throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var first =
                    executor.submit(
                            () -> {
                                start.await();
                                return book(member, trainer, BOOKING_DATE, "10:00", "11:00")
                                        .andReturn()
                                        .getResponse()
                                        .getStatus();
                            });
            var second =
                    executor.submit(
                            () -> {
                                start.await();
                                return book(
                                                sameTrainer ? otherMember : member,
                                                sameTrainer ? trainer : otherTrainer,
                                                BOOKING_DATE,
                                                "10:30",
                                                "11:30")
                                        .andReturn()
                                        .getResponse()
                                        .getStatus();
                            });
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
            assertThat(
                            sessions.findByTrainer(trainer).size()
                                    + sessions.findByTrainer(otherTrainer).size())
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private User user(Role role) {
        return users.saveAndFlush(
                User.builder()
                        .gymId(com.fitcore.gym.tenancy.RegressionGym.ID)
                        .fullName("Private booking fixture")
                        .email(UUID.randomUUID() + "@booking.test")
                        .passwordHash(PASSWORD_HASH)
                        .role(role)
                        .enabled(true)
                        .build());
    }

    private TrainerProfile trainer() {
        return trainers.saveAndFlush(
                TrainerProfile.builder()
                        .gymId(com.fitcore.gym.tenancy.RegressionGym.ID)
                        .user(user(Role.TRAINER))
                        .specialization("Strength")
                        .experienceYears(3)
                        .certifications(new ArrayList<>())
                        .availableSlots(new ArrayList<>())
                        .build());
    }

    private void availability(List<String> windows) {
        trainer.setAvailableSlots(new ArrayList<>(windows));
        trainers.saveAndFlush(trainer);
    }

    private void existing(
            User owner,
            TrainerProfile coach,
            LocalDate date,
            String start,
            String end,
            SessionStatus sessionStatus) {
        sessions.saveAndFlush(
                TrainerSession.builder()
                        .gymId(com.fitcore.gym.tenancy.RegressionGym.ID)
                        .member(owner)
                        .trainer(coach)
                        .sessionDate(date)
                        .startTime(LocalTime.parse(start))
                        .endTime(LocalTime.parse(end))
                        .status(sessionStatus)
                        .build());
    }

    private ResultActions book(
            User owner, TrainerProfile coach, LocalDate date, String start, String end)
            throws Exception {
        return perform(owner, coach, body(owner, coach, fields(date, start, end)));
    }

    private ResultActions perform(User actor, TrainerProfile coach, String payload)
            throws Exception {
        return perform(actor, coach, payload, actor);
    }

    private ResultActions perform(User actor, TrainerProfile coach, String payload, User owner)
            throws Exception {
        return mvc.perform(
                post(path(owner, coach))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("Authorization", "Bearer " + jwt.generateToken(actor)));
    }

    private String path(User owner, TrainerProfile coach) {
        return "/api/trainers/book/" + owner.getId() + "/" + coach.getId();
    }

    private String fields(LocalDate date, String start, String end) {
        return "\"sessionDate\":\""
                + date
                + "\",\"startTime\":\""
                + start
                + "\",\"endTime\":\""
                + end
                + "\"";
    }

    private String body(User owner, TrainerProfile coach, String fields) {
        return "{\"member\":{\"id\":\""
                + owner.getId()
                + "\",\"fullName\":\"Booking member\","
                + "\"email\":\"member@booking.test\",\"role\":\"MEMBER\"},\"trainer\":{\"id\":"
                + coach.getId()
                + ",\"specialization\":\"Strength\"},"
                + fields
                + "}";
    }
}
