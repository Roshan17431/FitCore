package com.fitcore.gym.concurrency;

import com.fitcore.gym.domain.MembershipStatus;
import com.fitcore.gym.domain.Role;
import com.fitcore.gym.domain.SessionStatus;
import com.fitcore.gym.entity.ActiveMembership;
import com.fitcore.gym.entity.MembershipPlan;
import com.fitcore.gym.entity.TrainerProfile;
import com.fitcore.gym.entity.TrainerSession;
import com.fitcore.gym.entity.User;
import com.fitcore.gym.repository.ActiveMembershipRepository;
import com.fitcore.gym.repository.MembershipPlanRepository;
import com.fitcore.gym.repository.TrainerProfileRepository;
import com.fitcore.gym.repository.TrainerSessionRepository;
import com.fitcore.gym.repository.UserRepository;
import com.fitcore.gym.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Opt-in integration tests for the PostgreSQL locking semantics H2 cannot verify.
 * Run scripts/verify-postgres.sh to create the disposable database safely.
 * There is deliberately no test-level @Transactional: HTTP worker transactions
 * must see committed fixtures and commit their own writes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresConcurrencyTest.PostgresTestClock.class)
@Execution(ExecutionMode.SAME_THREAD)
@Timeout(45)
@EnabledIfEnvironmentVariable(named = "FITCORE_POSTGRES_TEST_URL", matches = PostgresConcurrencyTest.LOCAL_POSTGRES_URL)
class PostgresConcurrencyTest {
    @TestConfiguration
    static class PostgresTestClock {
        @Bean
        @Primary
        Clock postgresTestClock() {
            return Clock.fixed(Instant.parse("2030-06-03T10:00:00Z"), ZoneOffset.UTC);
        }
    }

    // No remote hosts, alternate databases, JDBC options, or out-of-range ports.
    static final String LOCAL_POSTGRES_URL = "jdbc:postgresql://127\\.0\\.0\\.1:"
            + "(6553[0-5]|655[0-2][0-9]|65[0-4][0-9]{2}|6[0-4][0-9]{3}|[1-5][0-9]{4}|[1-9][0-9]{0,3})"
            + "/fitcore_phase2";
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder()
            .encode("Disposable PostgreSQL concurrency fixtures only!");

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired UserRepository users;
    @Autowired MembershipPlanRepository plans;
    @Autowired ActiveMembershipRepository memberships;
    @Autowired TrainerProfileRepository trainers;
    @Autowired TrainerSessionRepository sessions;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired Clock clock;

    private final List<UUID> fixtureUsers = new ArrayList<>();
    private final List<Long> fixtureTrainers = new ArrayList<>();
    private final List<Long> fixturePlans = new ArrayList<>();
    private User member;
    private User otherMember;
    private TrainerProfile trainer;
    private TrainerProfile otherTrainer;
    private MembershipPlan plan;
    private LocalDate today;

    @DynamicPropertySource
    static void disposablePostgres(DynamicPropertyRegistry registry) {
        String url = requiredEnvironment("FITCORE_POSTGRES_TEST_URL");
        if (!url.matches(LOCAL_POSTGRES_URL)) {
            throw new IllegalStateException("PostgreSQL tests require an exact loopback fitcore_phase2 JDBC URL");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> requiredEnvironment("FITCORE_POSTGRES_TEST_USERNAME"));
        registry.add("spring.datasource.password", () -> requiredEnvironment("FITCORE_POSTGRES_TEST_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.sql.init.mode", () -> "never");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "8");
        registry.add("spring.datasource.hikari.connection-timeout", () -> "10000");
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing disposable PostgreSQL test setting: " + name);
        }
        return value;
    }

    @BeforeEach
    void committedFixtures() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("fitcore_phase2");
        today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        transaction().executeWithoutResult(ignored -> {
            member = user("member", Role.MEMBER);
            otherMember = user("other-member", Role.MEMBER);
            trainer = trainer("coach");
            otherTrainer = trainer("other-coach");
            plan = plans.saveAndFlush(MembershipPlan.builder()
                    .name("PostgreSQL concurrency " + UUID.randomUUID())
                    .price(new BigDecimal("40.00")).durationMonths(1)
                    .features(List.of("Gym access")).recommended(false).build());
            fixturePlans.add(plan.getId());
        });
    }

    @AfterEach
    void removeOnlyThisTestsCommittedFixtures() {
        transaction().executeWithoutResult(ignored -> {
            for (UUID userId : fixtureUsers) {
                jdbc.update("DELETE FROM trainer_sessions WHERE member_id = ?", userId);
                jdbc.update("DELETE FROM active_memberships WHERE user_id = ?", userId);
                jdbc.update("DELETE FROM notifications WHERE user_id = ?", userId);
            }
            for (Long trainerId : fixtureTrainers) {
                jdbc.update("DELETE FROM trainer_sessions WHERE trainer_id = ?", trainerId);
                jdbc.update("DELETE FROM trainer_certifications WHERE trainer_id = ?", trainerId);
                jdbc.update("DELETE FROM trainer_available_slots WHERE trainer_id = ?", trainerId);
                jdbc.update("DELETE FROM trainer_profiles WHERE id = ?", trainerId);
            }
            for (Long planId : fixturePlans) {
                jdbc.update("DELETE FROM membership_plan_features WHERE plan_id = ?", planId);
                jdbc.update("DELETE FROM membership_plans WHERE id = ?", planId);
            }
            for (UUID userId : fixtureUsers) {
                jdbc.update("DELETE FROM users WHERE id = ?", userId);
            }
        });
    }

    @Test
    void concurrentSubscriptionsCreateExactlyOneCurrentMembership() throws Exception {
        List<HttpResult> results = onLockedMember(member,
                () -> subscribe(member), () -> subscribe(member));

        assertOneSuccessAndOneConflict(results);
        assertThat(currentMembershipCount()).isEqualTo(1L);
        assertThat(membershipCount()).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT end_date FROM active_memberships WHERE user_id = ?",
                LocalDate.class, member.getId())).isEqualTo(today.plusMonths(plan.getDurationMonths()));
    }

    @Test
    void concurrentActiveRenewalsExtendThePeriodOnlyOnce() throws Exception {
        LocalDate originalStart = today.minusDays(10);
        LocalDate originalEnd = today.plusDays(20);
        ActiveMembership original = membership(originalStart, originalEnd, MembershipStatus.ACTIVE);

        List<HttpResult> results = onLockedMember(member,
                () -> renew(original, originalEnd), () -> renew(original, originalEnd));

        assertOneSuccessAndOneConflict(results);
        assertThat(membershipCount()).isEqualTo(1L);
        assertThat(currentMembershipCount()).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT start_date FROM active_memberships WHERE id = ?",
                LocalDate.class, original.getId())).isEqualTo(originalStart);
        assertThat(jdbc.queryForObject("SELECT end_date FROM active_memberships WHERE id = ?",
                LocalDate.class, original.getId())).isEqualTo(originalEnd.plusMonths(plan.getDurationMonths()));
    }

    @Test
    void concurrentRenewalsAtTheExclusiveEndDatePreserveHistoryAndCreateOneNewPeriod() throws Exception {
        LocalDate originalStart = today.minusMonths(1);
        // Persisted ACTIVE status must not make an end date of today current.
        ActiveMembership original = membership(originalStart, today, MembershipStatus.ACTIVE);

        List<HttpResult> results = onLockedMember(member,
                () -> renew(original, today), () -> renew(original, today));

        assertOneSuccessAndOneConflict(results);
        assertThat(membershipCount()).isEqualTo(2L);
        assertThat(currentMembershipCount()).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT start_date FROM active_memberships WHERE id = ?",
                LocalDate.class, original.getId())).isEqualTo(originalStart);
        assertThat(jdbc.queryForObject("SELECT end_date FROM active_memberships WHERE id = ?",
                LocalDate.class, original.getId())).isEqualTo(today);
        assertThat(jdbc.queryForObject("SELECT plan_id FROM active_memberships WHERE id = ?",
                Long.class, original.getId())).isEqualTo(plan.getId());
        assertThat(jdbc.queryForObject("SELECT start_date FROM active_memberships WHERE user_id = ? AND id <> ?",
                LocalDate.class, member.getId(), original.getId())).isEqualTo(today);
        assertThat(jdbc.queryForObject("SELECT end_date FROM active_memberships WHERE user_id = ? AND id <> ?",
                LocalDate.class, member.getId(), original.getId())).isEqualTo(today.plusMonths(plan.getDurationMonths()));
    }

    @Test
    void sameTrainerAndSlotCannotBeBookedByTwoMembersConcurrently() throws Exception {
        List<HttpResult> results = onLockedTrainer(trainer,
                () -> book(member, trainer, "10:00", "11:00"),
                () -> book(otherMember, trainer, "10:00", "11:00"));

        assertOneSuccessAndOneConflict(results);
        assertThat(scheduledSessionCount()).isEqualTo(1L);
    }

    @Test
    void partiallyOverlappingTrainerSlotsCannotBeBookedConcurrently() throws Exception {
        List<HttpResult> results = onLockedTrainer(trainer,
                () -> book(member, trainer, "10:00", "11:00"),
                () -> book(otherMember, trainer, "10:30", "11:30"));

        assertOneSuccessAndOneConflict(results);
        assertThat(scheduledSessionCount()).isEqualTo(1L);
    }

    @Test
    void sameMemberCannotConcurrentlyBookTheSameTimeWithDifferentTrainers() throws Exception {
        List<HttpResult> results = onLockedMember(member,
                () -> book(member, trainer, "10:00", "11:00"),
                () -> book(member, otherTrainer, "10:00", "11:00"));

        assertOneSuccessAndOneConflict(results);
        assertThat(scheduledSessionCount()).isEqualTo(1L);
    }

    @Test
    void sameMemberCannotConcurrentlyBookOverlappingTimesWithDifferentTrainers() throws Exception {
        List<HttpResult> results = onLockedMember(member,
                () -> book(member, trainer, "10:00", "11:00"),
                () -> book(member, otherTrainer, "10:30", "11:30"));

        assertOneSuccessAndOneConflict(results);
        assertThat(scheduledSessionCount()).isEqualTo(1L);
    }

    @Test
    void adjacentTrainerSlotsCanBothBeBookedConcurrently() throws Exception {
        List<HttpResult> results = onLockedTrainer(trainer,
                () -> book(member, trainer, "10:00", "11:00"),
                () -> book(otherMember, trainer, "11:00", "12:00"));

        assertThat(results).extracting(HttpResult::status).as(results.toString()).containsExactlyInAnyOrder(200, 200);
        assertThat(scheduledSessionCount()).isEqualTo(2L);
    }

    @Test
    void cancelledTrainerAndMemberSlotsCanBeReused() throws Exception {
        transaction().executeWithoutResult(ignored -> sessions.saveAndFlush(TrainerSession.builder()
                .member(member).trainer(trainer).sessionDate(today.plusDays(1))
                .startTime(LocalTime.of(10, 0)).endTime(LocalTime.of(11, 0))
                .status(SessionStatus.CANCELLED).build()));

        HttpResult result = book(member, trainer, "10:00", "11:00");

        assertThat(result.status()).as(result.toString()).isEqualTo(200);
        assertThat(scheduledSessionCount()).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trainer_sessions WHERE member_id = ? AND status = 'CANCELLED'",
                Long.class, member.getId())).isEqualTo(1L);
    }

    static Stream<Arguments> fractionalSecondBookings() {
        // Include sub-microsecond, microsecond, and millisecond values so the
        // API cannot silently depend on JDBC or PostgreSQL time rounding.
        return IntStream.of(1, 2, 1_000, 2_000, 1_000_000, 2_000_000).boxed()
                .flatMap(nanos -> Stream.of(
                        Arguments.of(LocalTime.of(10, 0).withNano(nanos), LocalTime.of(11, 0)),
                        Arguments.of(LocalTime.of(10, 0), LocalTime.of(11, 0).withNano(nanos))));
    }

    @ParameterizedTest(name = "fractional appointment {0} to {1} is rejected without a write")
    @MethodSource("fractionalSecondBookings")
    void fractionalSecondBookingsAreRejectedBeforePersistence(LocalTime start, LocalTime end) throws Exception {
        HttpResult result = book(member, trainer, start, end);

        assertThat(result.status()).as(result.toString()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trainer_sessions WHERE member_id = ?",
                Long.class, member.getId())).isZero();
    }

    @Test
    void nonzeroWholeSecondsSurvivePostgresSaveAndReloadExactly() throws Exception {
        LocalTime start = LocalTime.of(10, 0, 1);
        LocalTime end = LocalTime.of(10, 0, 2);

        HttpResult result = book(member, trainer, start, end);

        assertThat(result.status()).as(result.toString()).isEqualTo(200);
        List<TrainerSession> reloaded = transaction().execute(ignored ->
                sessions.findByMemberOrderBySessionDateAscStartTimeAsc(member));
        assertThat(reloaded).hasSize(1);
        assertThat(reloaded.get(0).getStartTime()).isEqualTo(start);
        assertThat(reloaded.get(0).getEndTime()).isEqualTo(end);
    }

    private User user(String label, Role role) {
        User user = users.saveAndFlush(User.builder().fullName("PostgreSQL " + label)
                .email(label + "-" + UUID.randomUUID() + "@postgres-concurrency.test")
                .passwordHash(PASSWORD_HASH).role(role).enabled(true).build());
        fixtureUsers.add(user.getId());
        return user;
    }

    private TrainerProfile trainer(String label) {
        TrainerProfile profile = trainers.saveAndFlush(TrainerProfile.builder().user(user(label, Role.TRAINER))
                .specialization("Strength").experienceYears(3)
                .certifications(List.of()).availableSlots(List.of()).build());
        fixtureTrainers.add(profile.getId());
        return profile;
    }

    private ActiveMembership membership(LocalDate start, LocalDate end, MembershipStatus status) {
        return transaction().execute(ignored -> memberships.saveAndFlush(ActiveMembership.builder()
                .user(member).plan(plan).startDate(start).endDate(end).status(status).build()));
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private HttpResult subscribe(User owner) throws Exception {
        return perform(post("/api/memberships/subscribe/{user}/{plan}", owner.getId(), plan.getId()), owner);
    }

    private HttpResult renew(ActiveMembership membership, LocalDate expectedEnd) throws Exception {
        return perform(post("/api/memberships/renew/{user}/{membership}", member.getId(), membership.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedEndDate\":\"" + expectedEnd + "\"}"), member);
    }

    private HttpResult book(User owner, TrainerProfile coach, String start, String end) throws Exception {
        return book(owner, coach, LocalTime.parse(start), LocalTime.parse(end));
    }

    private HttpResult book(User owner, TrainerProfile coach, LocalTime start, LocalTime end) throws Exception {
        return perform(post("/api/trainers/book/{member}/{trainer}", owner.getId(), coach.getId())
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"member":{"id":"%s","fullName":"PostgreSQL member","email":"member@postgres-concurrency.test","role":"MEMBER"},
                         "trainer":{"id":%d,"specialization":"Strength"},
                         "sessionDate":"%s","startTime":"%s","endTime":"%s"}
                        """.formatted(owner.getId(), coach.getId(), today.plusDays(1),
                        start.format(DateTimeFormatter.ISO_LOCAL_TIME), end.format(DateTimeFormatter.ISO_LOCAL_TIME))), owner);
    }

    private HttpResult perform(MockHttpServletRequestBuilder request, User owner) throws Exception {
        var response = mvc.perform(request.header("Authorization", "Bearer " + jwt.generateToken(owner)))
                .andReturn().getResponse();
        return new HttpResult(response.getStatus(), response.getContentAsString());
    }

    private List<HttpResult> onLockedMember(User owner, Callable<HttpResult> first, Callable<HttpResult> second)
            throws Exception {
        return concurrentWithParentLock("users", owner.getId(), first, second);
    }

    private List<HttpResult> onLockedTrainer(TrainerProfile coach, Callable<HttpResult> first, Callable<HttpResult> second)
            throws Exception {
        return concurrentWithParentLock("trainer_profiles", coach.getId(), first, second);
    }

    /**
     * Both requests start at a barrier while a separate transaction holds their
     * stable parent row. The fixed services wait on that row before checking;
     * the original services reach their inserts and wait on the foreign key.
     * PostgreSQL's lock graph, rather than an arbitrary sleep, confirms that
     * both requests contend before the holder releases them.
     */
    private List<HttpResult> concurrentWithParentLock(String table, Object id,
            Callable<HttpResult> first, Callable<HttpResult> second) throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        Future<HttpResult> left = null;
        Future<HttpResult> right = null;
        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try (var statement = holder.prepareStatement("SELECT id FROM " + table + " WHERE id = ? FOR UPDATE")) {
                statement.setObject(1, id);
                try (var locked = statement.executeQuery()) {
                    assertThat(locked.next()).as("Committed parent fixture must exist").isTrue();
                }
            }
            try {
                CyclicBarrier start = new CyclicBarrier(3);
                left = workers.submit(() -> { start.await(5, TimeUnit.SECONDS); return first.call(); });
                right = workers.submit(() -> { start.await(5, TimeUnit.SECONDS); return second.call(); });
                start.await(5, TimeUnit.SECONDS);
                awaitBothRequestsContendingOrFinished(left, right);
            } finally {
                holder.rollback();
            }
            return List.of(left.get(15, TimeUnit.SECONDS), right.get(15, TimeUnit.SECONDS));
        } finally {
            if (left != null && !left.isDone()) left.cancel(true);
            if (right != null && !right.isDone()) right.cancel(true);
            workers.shutdownNow();
            assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).as("HTTP workers must terminate").isTrue();
        }
    }

    private void awaitBothRequestsContendingOrFinished(Future<HttpResult> left, Future<HttpResult> right) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        do {
            // Invalid/missing endpoints should report their real HTTP failures.
            if (left.isDone() && right.isDone()) return;
            Long blocked = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM pg_stat_activity
                    WHERE datname = current_database() AND pid <> pg_backend_pid()
                      AND cardinality(pg_blocking_pids(pid)) > 0
                    """, Long.class);
            if (blocked != null && blocked >= 2) return;
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Two HTTP requests did not reach PostgreSQL contention within 10 seconds");
    }

    private void assertOneSuccessAndOneConflict(List<HttpResult> results) {
        assertThat(results).extracting(HttpResult::status).as(results.toString()).containsExactlyInAnyOrder(200, 409);
    }

    private long membershipCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM active_memberships WHERE user_id = ?", Long.class, member.getId());
    }

    private long currentMembershipCount() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM active_memberships
                WHERE user_id = ? AND status = 'ACTIVE' AND start_date <= ? AND end_date > ?
                """, Long.class, member.getId(), today, today);
    }

    private long scheduledSessionCount() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM trainer_sessions
                WHERE member_id IN (?, ?) AND status = 'SCHEDULED'
                """, Long.class, member.getId(), otherMember.getId());
    }

    private record HttpResult(int status, String body) {}
}
