package com.fitcore.gym.tenancy;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fitcore.gym.domain.*;
import com.fitcore.gym.entity.*;
import com.fitcore.gym.security.*;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.*;

/** Signed HTTP requests through the actual backend and Flyway-created PostgreSQL schema. */
@EnabledIfEnvironmentVariable(named = "FITCORE_TENANCY_TEST_TOKEN", matches = "[a-f0-9]{64}")
@SpringBootTest(properties = "fitcore.onboarding.demo-enabled=true")
@AutoConfigureMockMvc
@Transactional
class TenantIsolationTest {
    static final String RUNTIME = "fitcore_http_runtime";

    static void prepareDisposableSchema() {
        String url = System.getenv("FITCORE_POSTGRES_TEST_URL"),
                token = System.getenv("FITCORE_TENANCY_TEST_TOKEN");
        if (url == null
                || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/fitcore_phase2")
                || token == null
                || !token.matches("[a-f0-9]{64}"))
            throw new IllegalStateException("Owned disposable PostgreSQL required");
        String username = System.getenv("FITCORE_POSTGRES_TEST_USERNAME"),
                password = System.getenv("FITCORE_POSTGRES_TEST_PASSWORD");
        try (var c = java.sql.DriverManager.getConnection(url, username, password);
                var stmt = c.createStatement()) {
            try (var r =
                    stmt.executeQuery(
                            "select shobj_description(oid,'pg_database') from pg_database where"
                                    + " datname=current_database()")) {
                r.next();
                if (!("fitcore-disposable:" + token).equals(r.getString(1)))
                    throw new IllegalStateException("Disposable marker mismatch");
            }
            org.flywaydb.core.Flyway.configure()
                    .dataSource(url, username, password)
                    .defaultSchema("tenant_http")
                    .schemas("tenant_http")
                    .baselineOnMigrate(false)
                    .cleanDisabled(true)
                    .load()
                    .migrate();
            stmt.execute(
                    "create role "
                            + RUNTIME
                            + " login password 'synthetic-runtime-only' nosuperuser nocreatedb"
                            + " nocreaterole noreplication");
            stmt.execute("grant usage on schema tenant_http to " + RUNTIME);
            stmt.execute(
                    "grant select,insert,update,delete on all tables in schema tenant_http to "
                            + RUNTIME);
            stmt.execute("revoke all on tenant_http.flyway_schema_history from " + RUNTIME);
            stmt.execute("grant usage,select on all sequences in schema tenant_http to " + RUNTIME);
            stmt.execute("create schema tenant_operator_input");
            stmt.execute("create table tenant_operator_input.synthetic_manifest(id int)");
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry p) {
        prepareDisposableSchema();
        p.add("spring.datasource.url", () -> System.getenv("FITCORE_POSTGRES_TEST_URL"));
        p.add("spring.datasource.username", () -> RUNTIME);
        p.add("spring.datasource.password", () -> "synthetic-runtime-only");
        p.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        p.add("spring.datasource.hikari.schema", () -> "tenant_http");
        p.add("spring.jpa.properties.hibernate.default_schema", () -> "tenant_http");
        p.add(
                "spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
        p.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        p.add("spring.jpa.defer-datasource-initialization", () -> "false");
        p.add("spring.flyway.enabled", () -> "false");
        p.add("spring.flyway.default-schema", () -> "tenant_http");
    }

    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired javax.sql.DataSource datasource;
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    @Autowired RequestMappingHandlerMapping mappings;
    @Autowired com.fitcore.gym.service.WorkoutService workouts;
    @Autowired com.fitcore.gym.service.AdminService adminService;
    static final String HASH = new BCryptPasswordEncoder().encode("SyntheticOnly!Phase3");
    static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

    record Fixture(
            Gym gym,
            User owner,
            User admin,
            User trainerUser,
            User member,
            User other,
            MembershipPlan plan,
            MembershipPlan upgrade,
            MembershipPlan unused,
            TrainerProfile trainer,
            Exercise exercise,
            ActiveMembership period,
            Attendance attendance) {}

    Fixture a, b;

    <T> T persist(T row) {
        em.persist(row);
        return row;
    }

    User user(Gym gym, Role role, String label) {
        return persist(
                User.builder()
                        .gymId(gym.getId())
                        .fullName(label)
                        .email(UUID.randomUUID() + "@fixture.invalid")
                        .passwordHash(HASH)
                        .role(role)
                        .enabled(true)
                        .build());
    }

    Fixture fixture(String sentinel) {
        Gym gym =
                persist(
                        Gym.builder()
                                .name(sentinel)
                                .slug(UUID.randomUUID().toString())
                                .status(GymStatus.ACTIVE)
                                .build());
        User owner = user(gym, Role.OWNER, sentinel + " owner");
        gym.setPrimaryOwner(owner);
        User admin = user(gym, Role.ADMIN, sentinel + " admin"),
                tu = user(gym, Role.TRAINER, sentinel + " trainer"),
                member = user(gym, Role.MEMBER, sentinel + " member"),
                other = user(gym, Role.MEMBER, sentinel + " other");
        MembershipPlan plan =
                persist(
                        MembershipPlan.builder()
                                .gymId(gym.getId())
                                .name("Premium")
                                .price(new BigDecimal("79"))
                                .durationMonths(3)
                                .recommended(false)
                                .features(List.of(sentinel + " feature"))
                                .build());
        MembershipPlan upgrade =
                persist(
                        MembershipPlan.builder()
                                .gymId(gym.getId())
                                .name("Elite")
                                .price(new BigDecimal("149"))
                                .durationMonths(6)
                                .recommended(false)
                                .features(List.of(sentinel + " upgrade"))
                                .build());
        MembershipPlan unused =
                persist(
                        MembershipPlan.builder()
                                .gymId(gym.getId())
                                .name("Unused")
                                .price(new BigDecimal("29"))
                                .durationMonths(1)
                                .recommended(false)
                                .features(List.of(sentinel + " unused"))
                                .build());
        TrainerProfile trainer =
                persist(
                        TrainerProfile.builder()
                                .gymId(gym.getId())
                                .user(tu)
                                .specialization(sentinel)
                                .experienceYears(3)
                                .certifications(List.of(sentinel + " cert"))
                                .availableSlots(List.of())
                                .build());
        Exercise exercise =
                persist(
                        Exercise.builder()
                                .gymId(gym.getId())
                                .name("Bench")
                                .description(sentinel)
                                .category("Chest")
                                .difficulty(DifficultyLevel.BEGINNER)
                                .build());
        persist(
                WorkoutPlan.builder()
                        .gymId(gym.getId())
                        .level("Beginner")
                        .duration("30 min")
                        .schedule(List.of(sentinel + " schedule"))
                        .exercises(List.of(sentinel + " exercise"))
                        .build());
        persist(
                NutritionPlan.builder()
                        .gymId(gym.getId())
                        .category("Balanced")
                        .calories(2000)
                        .protein(100)
                        .carbs(250)
                        .fats(60)
                        .meals(List.of(sentinel + " meal"))
                        .build());
        ActiveMembership period =
                persist(
                        ActiveMembership.builder()
                                .gymId(gym.getId())
                                .user(member)
                                .plan(plan)
                                .startDate(TODAY.minusDays(7))
                                .endDate(TODAY.plusDays(30))
                                .status(MembershipStatus.ACTIVE)
                                .build());
        Attendance attendance =
                persist(
                        Attendance.builder()
                                .gymId(gym.getId())
                                .user(member)
                                .checkInDate(TODAY)
                                .checkInTime(TODAY.atTime(9, 0))
                                .build());
        WorkoutLog log =
                persist(
                        WorkoutLog.builder()
                                .gymId(gym.getId())
                                .user(member)
                                .date(TODAY)
                                .notes(sentinel)
                                .durationMinutes(30)
                                .build());
        persist(
                WorkoutExerciseLog.builder()
                        .gymId(gym.getId())
                        .workoutLog(log)
                        .exercise(exercise)
                        .setsCompleted(3)
                        .repsCompleted("10")
                        .build());
        persist(
                FoodLog.builder()
                        .gymId(gym.getId())
                        .user(member)
                        .mealName(sentinel)
                        .mealType(MealType.LUNCH)
                        .calories(200)
                        .logDate(TODAY)
                        .logTime(LocalTime.NOON)
                        .build());
        persist(
                NutritionGoal.builder()
                        .gymId(gym.getId())
                        .user(member)
                        .targetCalories(2000)
                        .build());
        persist(
                ProgressMetric.builder()
                        .gymId(gym.getId())
                        .user(member)
                        .logDate(TODAY)
                        .weight(73.5)
                        .build());
        persist(
                Notification.builder()
                        .gymId(gym.getId())
                        .user(member)
                        .title(sentinel)
                        .type("INFO")
                        .readFlag(false)
                        .build());
        persist(
                TrainerSession.builder()
                        .gymId(gym.getId())
                        .member(member)
                        .trainer(trainer)
                        .sessionDate(TODAY.plusDays(3))
                        .startTime(LocalTime.of(10, 0))
                        .endTime(LocalTime.of(11, 0))
                        .status(SessionStatus.SCHEDULED)
                        .build());
        return new Fixture(
                gym,
                owner,
                admin,
                tu,
                member,
                other,
                plan,
                upgrade,
                unused,
                trainer,
                exercise,
                period,
                attendance);
    }

    @BeforeEach
    void seed() {
        Runnable fixture =
                () -> {
                    a = fixture("GYM_A_SENTINEL");
                    b = fixture("GYM_B_SENTINEL");
                    em.flush();
                };
        if (org.springframework.transaction.support.TransactionSynchronizationManager
                .isActualTransactionActive()) fixture.run();
        else
            new org.springframework.transaction.support.TransactionTemplate(transactions)
                    .executeWithoutResult(status -> fixture.run());
    }

    enum Endpoint {
        REGISTER("POST", "/api/auth/register"),
        LOGIN("POST", "/api/auth/login"),
        FORGOT("POST", "/api/auth/forgot-password"),
        HEALTH("GET", "/api/health"),
        PROFILE("GET", "/api/users/{id}"),
        DASHBOARD("GET", "/api/dashboard/{id}"),
        NOTIFICATIONS("GET", "/api/notifications/{id}"),
        PLANS("GET", "/api/memberships/plans"),
        SUBSCRIBE("POST", "/api/memberships/subscribe/{id}/{plan}"),
        HISTORY("GET", "/api/memberships/user/{id}"),
        ACTIVE("GET", "/api/memberships/user/{id}/active"),
        RENEW("POST", "/api/memberships/renew/{id}/{period}"),
        UPGRADE("POST", "/api/memberships/upgrade/{id}/{upgrade}"),
        TRAINERS("GET", "/api/trainers"),
        BOOK("POST", "/api/trainers/book/{id}/{trainer}"),
        SESSIONS("GET", "/api/trainers/sessions/{id}"),
        EXERCISES("GET", "/api/workouts/exercises"),
        WORKOUT_PLANS("GET", "/api/workouts/plans"),
        WORKOUT_HISTORY("GET", "/api/workouts/logs/{id}"),
        WORKOUT_WRITE("POST", "/api/workouts/logs/{id}"),
        NUTRITION("GET", "/api/nutrition/dashboard/{id}"),
        NUTRITION_PLANS("GET", "/api/nutrition/plans"),
        GOAL_WRITE("POST", "/api/nutrition/goal/{id}"),
        GOAL("GET", "/api/nutrition/goal/{id}"),
        FOOD_WRITE("POST", "/api/nutrition/log/{id}"),
        FOOD("GET", "/api/nutrition/logs/{id}"),
        PROGRESS_WRITE("POST", "/api/tracking/progress/{id}"),
        PROGRESS("GET", "/api/tracking/progress/{id}"),
        CHECKIN("POST", "/api/tracking/attendance/check-in/{id}"),
        CHECKOUT("PUT", "/api/tracking/attendance/check-out/{attendance}"),
        ATTENDANCE("GET", "/api/tracking/attendance/{id}"),
        ADMIN_DASHBOARD("GET", "/api/admin/dashboard"),
        ADMIN_USERS("GET", "/api/admin/users"),
        USER_UPDATE("PUT", "/api/admin/users/{id}"),
        USER_DISABLE("DELETE", "/api/admin/users/{id}"),
        TRAINER_CREATE("POST", "/api/admin/trainers"),
        TRAINER_UPDATE("PUT", "/api/admin/trainers/{trainer}"),
        TRAINER_DISABLE("DELETE", "/api/admin/trainers/{trainer}"),
        PLAN_CREATE("POST", "/api/admin/membership-plans"),
        PLAN_UPDATE("PUT", "/api/admin/membership-plans/{plan}"),
        PLAN_DELETE("DELETE", "/api/admin/membership-plans/{unused}"),
        ADMIN_ATTENDANCE("GET", "/api/admin/attendance"),
        IMAGE_UPLOAD("POST", "/api/images/upload"),
        IMAGE_DELETE("DELETE", "/api/images/delete");
        final String method, path;

        Endpoint(String m, String p) {
            method = m;
            path = p;
        }

        boolean targeted() {
            return path.contains("{");
        }

        boolean admin() {
            return path.startsWith("/api/admin");
        }
    }

    MockHttpServletRequestBuilder request(Endpoint e, Fixture f) {
        UUID target = e == Endpoint.SUBSCRIBE ? f.other().getId() : f.member().getId();
        String path =
                e.path.replace("{id}", target.toString())
                        .replace("{plan}", f.plan().getId().toString())
                        .replace("{upgrade}", f.upgrade().getId().toString())
                        .replace("{period}", f.period().getId().toString())
                        .replace("{trainer}", f.trainer().getId().toString())
                        .replace("{attendance}", f.attendance().getId().toString())
                        .replace("{unused}", f.unused().getId().toString());
        String body =
                switch (e) {
                    case RENEW -> "{\"expectedEndDate\":\"" + f.period().getEndDate() + "\"}";
                    case UPGRADE ->
                            "{\"membershipId\":"
                                    + f.period().getId()
                                    + ",\"expectedEndDate\":\""
                                    + f.period().getEndDate()
                                    + "\"}";
                    case BOOK ->
                            "{\"member\":{\"fullName\":\"Informational"
                                + " member\",\"email\":\"body@fixture.invalid\",\"role\":\"MEMBER\",\"id\":\""
                                    + target
                                    + "\"},\"trainer\":{\"specialization\":\"Informational"
                                    + " strength\",\"id\":"
                                    + f.trainer().getId()
                                    + "},\"sessionDate\":\""
                                    + TODAY.plusDays(1)
                                    + "\",\"startTime\":\"10:00\",\"endTime\":\"11:00\"}";
                    case WORKOUT_WRITE ->
                            "{\"userId\":\""
                                    + target
                                    + "\",\"notes\":\"local\",\"exercises\":[{\"exercise\":{\"id\":"
                                    + f.exercise().getId()
                                    + ",\"name\":\"Bench\",\"category\":\"Chest\",\"difficulty\":\"BEGINNER\"}}]}";
                    case GOAL_WRITE -> "{\"userId\":\"" + target + "\",\"targetCalories\":2100}";
                    case FOOD_WRITE ->
                            "{\"userId\":\""
                                    + target
                                    + "\",\"mealName\":\"Local\",\"mealType\":\"LUNCH\",\"calories\":100}";
                    case PROGRESS_WRITE -> "{\"userId\":\"" + target + "\",\"weight\":74}";
                    case USER_UPDATE ->
                            "{\"fullName\":\"Updated member\",\"email\":\""
                                    + f.member().getEmail()
                                    + "\",\"role\":\"MEMBER\",\"enabled\":true}";
                    case TRAINER_CREATE, TRAINER_UPDATE ->
                            "{\"fullName\":\"Local trainer\",\"email\":\""
                                    + (e == Endpoint.TRAINER_UPDATE
                                            ? f.trainerUser().getEmail()
                                            : UUID.randomUUID() + "@fixture.invalid")
                                    + "\",\"password\":\"SyntheticOnly!Phase3\",\"specialization\":\"Strength\",\"experienceYears\":3}";
                    case PLAN_CREATE, PLAN_UPDATE ->
                            "{\"name\":\"Edited local"
                                + " plan\",\"price\":30,\"durationMonths\":1,\"features\":[\"local\"]}";
                    case LOGIN ->
                            "{\"email\":\""
                                    + f.member().getEmail()
                                    + "\",\"password\":\"SyntheticOnly!Phase3\"}";
                    case REGISTER ->
                            "{\"fullName\":\"New"
                                + " account\",\"email\":\"new@fixture.invalid\",\"password\":\"SyntheticOnly!Phase3\"}";
                    case FORGOT -> "{\"email\":\"missing@fixture.invalid\"}";
                    default -> "{}";
                };
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(
                        org.springframework.http.HttpMethod.valueOf(e.method), path)
                .contentType("application/json")
                .content(body);
    }

    User actor(Fixture f, Role role) {
        return switch (role) {
            case OWNER -> f.owner();
            case ADMIN -> f.admin();
            case TRAINER -> f.trainerUser();
            case MEMBER -> f.member();
        };
    }

    String token(User u) {
        return "Bearer " + jwt.generateToken(u);
    }

    static Stream<Arguments> foreignTargets() {
        return Stream.of(false, true)
                .flatMap(
                        reverse ->
                                Arrays.stream(Role.values())
                                        .flatMap(
                                                role ->
                                                        Arrays.stream(Endpoint.values())
                                                                .filter(Endpoint::targeted)
                                                                .map(
                                                                        e ->
                                                                                Arguments.of(
                                                                                        reverse,
                                                                                        role, e))));
    }

    Map<String, String> snapshot() {
        Map<String, String> state = new TreeMap<>();
        for (String table :
                List.of(
                        "gyms",
                        "users",
                        "active_memberships",
                        "attendance",
                        "exercises",
                        "food_logs",
                        "membership_plans",
                        "notifications",
                        "nutrition_goals",
                        "nutrition_plans",
                        "progress_metrics",
                        "trainer_profiles",
                        "trainer_sessions",
                        "workout_exercise_logs",
                        "workout_logs",
                        "workout_plans",
                        "membership_plan_features",
                        "nutrition_plan_meals",
                        "trainer_available_slots",
                        "trainer_certifications",
                        "workout_plan_exercises",
                        "workout_plan_schedule"))
            state.put(
                    table,
                    jdbc.queryForObject(
                            "select coalesce(string_agg(row_to_json(t)::text,'|' order by"
                                    + " row_to_json(t)::text),'') from "
                                    + table
                                    + " t",
                            String.class));
        return state;
    }

    @ParameterizedTest
    @MethodSource("foreignTargets")
    void foreignTargetsDeniedWithoutMutation(boolean reverse, Role role, Endpoint endpoint)
            throws Exception {
        Fixture own = reverse ? b : a, foreign = reverse ? a : b;
        var before = snapshot();
        mvc.perform(request(endpoint, foreign).header("Authorization", token(actor(own, role))))
                .andExpect(
                        status().is(
                                        endpoint.admin()
                                                        && (role == Role.MEMBER
                                                                || role == Role.TRAINER)
                                                ? 403
                                                : 404));
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @EnumSource(Endpoint.class)
    void everyEndpointRequiresSessionOrExplicitPublicPolicy(Endpoint e) throws Exception {
        int expected =
                switch (e) {
                    case REGISTER -> 403;
                    case LOGIN, HEALTH -> 200;
                    case FORGOT -> 202;
                    default -> 401;
                };
        mvc.perform(request(e, a)).andExpect(status().is(expected));
    }

    @Test
    void endpointMatrixMatchesAllControllerRoutes() {
        Set<String> actual = new TreeSet<>();
        mappings.getHandlerMethods()
                .forEach(
                        (info, handler) -> {
                            if (handler.getBeanType()
                                    .getPackageName()
                                    .equals("com.fitcore.gym.controller"))
                                for (String p : info.getPatternValues())
                                    for (var m : info.getMethodsCondition().getMethods())
                                        actual.add(m + " " + p.replaceAll("\\{[^}]+}", "{}"));
                        });
        Set<String> expected =
                Arrays.stream(Endpoint.values())
                        .filter(e -> !e.name().startsWith("IMAGE_"))
                        .map(e -> e.method + " " + e.path.replaceAll("\\{[^}]+}", "{}"))
                        .collect(Collectors.toCollection(TreeSet::new));
        expected.add("GET /api/auth/me");
        expected.addAll(
                List.of(
                        "POST /api/auth/register-owner",
                        "GET /api/gym",
                        "PATCH /api/gym",
                        "GET /api/gym/invitations",
                        "POST /api/gym/invitations",
                        "DELETE /api/gym/invitations/{}"));
        assertThat(actual).isEqualTo(expected);
        assertThat(Endpoint.values()).hasSize(44);
    }

    @ParameterizedTest
    @CsvSource({
        "false,OWNER",
        "false,ADMIN",
        "true,OWNER",
        "true,ADMIN",
        "false,MEMBER",
        "false,TRAINER",
        "true,MEMBER",
        "true,TRAINER"
    })
    void localListsAndAggregatesExcludeEntireForeignGym(boolean reverse, Role role)
            throws Exception {
        Fixture own = reverse ? b : a;
        String foreign = reverse ? "GYM_A_SENTINEL" : "GYM_B_SENTINEL";
        for (Endpoint e :
                List.of(
                        Endpoint.PLANS,
                        Endpoint.TRAINERS,
                        Endpoint.EXERCISES,
                        Endpoint.WORKOUT_PLANS,
                        Endpoint.NUTRITION_PLANS,
                        Endpoint.DASHBOARD,
                        Endpoint.ADMIN_DASHBOARD,
                        Endpoint.ADMIN_USERS,
                        Endpoint.ADMIN_ATTENDANCE)) {
            if (e.admin() && role != Role.ADMIN && role != Role.OWNER) continue;
            if (e == Endpoint.DASHBOARD && role == Role.TRAINER) continue;
            var response =
                    mvc.perform(request(e, own).header("Authorization", token(actor(own, role))))
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            assertThat(response).doesNotContain(foreign);
            if (e == Endpoint.ADMIN_DASHBOARD) {
                var json =
                        tools.jackson.databind.json.JsonMapper.builder().build().readTree(response);
                assertThat(json.get("adminCards").get(0).get("value").asText()).isEqualTo("2");
                assertThat(json.get("adminCards").get(1).get("value").asText()).isEqualTo("1");
                assertThat(json.get("adminCards").get(2).get("value").asText()).isEqualTo("1");
                assertThat(json.get("adminCards").get(3).get("value").asText()).isEqualTo("$79");
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"false", "true"})
    void mixedWorkoutForeignExerciseIsAtomic(boolean reverse) throws Exception {
        Fixture own = reverse ? b : a, foreign = reverse ? a : b;
        var before = snapshot();
        String body =
                "{\"userId\":\""
                        + own.member().getId()
                        + "\",\"exercises\":[{\"exercise\":{\"id\":"
                        + own.exercise().getId()
                        + ",\"name\":\"Bench\",\"category\":\"Chest\",\"difficulty\":\"BEGINNER\"}},{\"exercise\":{\"id\":"
                        + foreign.exercise().getId()
                        + ",\"name\":\"Bench\",\"category\":\"Chest\",\"difficulty\":\"BEGINNER\"}}]}";
        mvc.perform(
                        post("/api/workouts/logs/" + own.member().getId())
                                .header("Authorization", token(own.member()))
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isNotFound());
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @CsvSource({"false", "true"})
    void foreignNestedPlansTrainersAndPeriodsAreRejected(boolean reverse) throws Exception {
        Fixture own = reverse ? b : a, foreign = reverse ? a : b;
        var before = snapshot();
        mvc.perform(
                        post("/api/memberships/subscribe/"
                                        + own.other().getId()
                                        + "/"
                                        + foreign.plan().getId())
                                .header("Authorization", token(own.owner())))
                .andExpect(status().isNotFound());
        mvc.perform(
                        post("/api/trainers/book/"
                                        + own.member().getId()
                                        + "/"
                                        + foreign.trainer().getId())
                                .header("Authorization", token(own.member()))
                                .contentType("application/json")
                                .content(
                                        "{\"member\":{\"fullName\":\"Informational"
                                            + " member\",\"email\":\"body@fixture.invalid\",\"role\":\"MEMBER\",\"id\":\""
                                                + own.member().getId()
                                                + "\"},\"trainer\":{\"specialization\":\"Informational"
                                                + " strength\",\"id\":"
                                                + foreign.trainer().getId()
                                                + "},\"sessionDate\":\""
                                                + TODAY.plusDays(1)
                                                + "\",\"startTime\":\"10:00\",\"endTime\":\"11:00\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(
                        post("/api/memberships/renew/"
                                        + own.member().getId()
                                        + "/"
                                        + foreign.period().getId())
                                .header("Authorization", token(own.member()))
                                .contentType("application/json")
                                .content(
                                        "{\"expectedEndDate\":\""
                                                + foreign.period().getEndDate()
                                                + "\"}"))
                .andExpect(status().isNotFound());
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @EnumSource(
            value = Endpoint.class,
            names = {
                "ADMIN_DASHBOARD",
                "ADMIN_USERS",
                "ADMIN_ATTENDANCE",
                "TRAINER_CREATE",
                "PLAN_CREATE",
                "USER_UPDATE",
                "USER_DISABLE",
                "TRAINER_UPDATE",
                "TRAINER_DISABLE",
                "PLAN_UPDATE",
                "PLAN_DELETE"
            })
    void ownerAndAdminLocalManagementWorks(Endpoint e) throws Exception {
        for (Fixture f : List.of(a, b))
            mvc.perform(request(e, f).header("Authorization", token(f.owner())))
                    .andExpect(status().is2xxSuccessful());
    }

    @Test
    void sameGymTrainerCannotReadMemberPrivateRecords() throws Exception {
        for (Endpoint e :
                Arrays.stream(Endpoint.values())
                        .filter(Endpoint::targeted)
                        .filter(e -> !e.admin())
                        .toList())
            mvc.perform(request(e, a).header("Authorization", token(a.trainerUser())))
                    .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource({"false", "true"})
    void privilegedTargetsAndAssignmentsAreRestricted(boolean reverse) throws Exception {
        Fixture f = reverse ? b : a;
        var before = snapshot();
        for (User target : List.of(f.owner(), f.admin())) {
            String body =
                    "{\"fullName\":\"Blocked\",\"email\":\""
                            + target.getEmail()
                            + "\",\"role\":\"MEMBER\",\"enabled\":false}";
            mvc.perform(
                            put("/api/admin/users/" + target.getId())
                                    .header("Authorization", token(f.admin()))
                                    .contentType("application/json")
                                    .content(body))
                    .andExpect(status().isForbidden());
            mvc.perform(
                            delete("/api/admin/users/" + target.getId())
                                    .header("Authorization", token(f.admin())))
                    .andExpect(status().isForbidden());
        }
        String grant =
                "{\"fullName\":\"Blocked grant\",\"email\":\""
                        + f.member().getEmail()
                        + "\",\"role\":\"ADMIN\",\"enabled\":true}";
        mvc.perform(
                        put("/api/admin/users/" + f.member().getId())
                                .header("Authorization", token(f.admin()))
                                .contentType("application/json")
                                .content(grant))
                .andExpect(status().isForbidden());
        mvc.perform(
                        delete("/api/admin/users/" + f.owner().getId())
                                .header("Authorization", token(f.owner())))
                .andExpect(status().isForbidden());
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void securityChangesAtomicallyBumpAuthorizationVersion() throws Exception {
        String old = token(a.member());
        UUID id = a.member().getId();
        long before =
                jdbc.queryForObject("select authz_version from users where id=?", Long.class, id);
        jdbc.update("update users set enabled=false where id=?", id);
        jdbc.update("update users set enabled=true where id=?", id);
        assertThat(
                        jdbc.queryForObject(
                                "select authz_version from users where id=?", Long.class, id))
                .isEqualTo(before + 2);
        em.clear();
        mvc.perform(get("/api/users/" + id).header("Authorization", old))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @CsvSource({"false", "true"})
    void ownerMayPromoteLocalAdminAndOldSessionStaysRevoked(boolean reverse) throws Exception {
        Fixture f = reverse ? b : a;
        String old = token(f.member());
        mvc.perform(
                        put("/api/admin/users/" + f.member().getId())
                                .header("Authorization", token(f.owner()))
                                .contentType("application/json")
                                .content(
                                        "{\"fullName\":\"Promoted\",\"email\":\""
                                                + f.member().getEmail()
                                                + "\",\"role\":\"ADMIN\",\"enabled\":true}"))
                .andExpect(status().isOk());
        em.flush();
        em.clear();
        mvc.perform(get("/api/users/" + f.member().getId()).header("Authorization", old))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void directServicesRequireTenantAndApplyForeignChecks() {
        assertThatThrownBy(() -> workouts.getAllExercises())
                .isInstanceOf(com.fitcore.gym.exception.UnauthorizedException.class);
        var principal = new TenantActor(a.owner().getId(), a.gym().getId(), Role.OWNER, 0);
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(
                        new org.springframework.security.authentication
                                .UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                List.of(
                                        new org.springframework.security.core.authority
                                                .SimpleGrantedAuthority("ROLE_OWNER"))));
        try {
            assertThatThrownBy(() -> adminService.disableUser(b.member().getId()))
                    .isInstanceOf(com.fitcore.gym.exception.ResourceNotFoundException.class);
            assertThat(workouts.getAllExercises()).hasSize(1);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    static Stream<Arguments> localPersonal() {
        return Stream.of(false, true)
                .flatMap(
                        reverse ->
                                Arrays.stream(Role.values())
                                        .flatMap(
                                                role ->
                                                        Arrays.stream(Endpoint.values())
                                                                .filter(Endpoint::targeted)
                                                                .filter(e -> !e.admin())
                                                                .map(
                                                                        e ->
                                                                                Arguments.of(
                                                                                        reverse,
                                                                                        role, e))));
    }

    @ParameterizedTest
    @MethodSource("localPersonal")
    void localPersonalOperationsRetainBusinessBehavior(boolean reverse, Role role, Endpoint e)
            throws Exception {
        Fixture f = reverse ? b : a;
        User caller;
        if (role == Role.ADMIN || role == Role.OWNER) caller = actor(f, role);
        else {
            caller = e == Endpoint.SUBSCRIBE ? f.other() : f.member();
            caller.setRole(role);
            em.flush();
            em.refresh(caller);
        }
        mvc.perform(request(e, f).header("Authorization", token(caller)))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void authenticatedImageRoutesRemainAbsent() throws Exception {
        for (var e : List.of(Endpoint.IMAGE_UPLOAD, Endpoint.IMAGE_DELETE))
            mvc.perform(request(e, a).header("Authorization", token(a.member())))
                    .andExpect(status().isNotFound());
    }

    @Test
    @Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentGymsKeepSessionsAndWritesIndependent() throws Exception {
        var start = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            List<java.util.concurrent.Future<Integer>> responses = new ArrayList<>();
            for (Fixture f : List.of(a, b)) {
                String memberToken = token(f.member()), otherToken = token(f.other());
                responses.add(
                        pool.submit(
                                () -> {
                                    start.await();
                                    int status =
                                            mvc.perform(
                                                            request(Endpoint.SUBSCRIBE, f)
                                                                    .header(
                                                                            "Authorization",
                                                                            otherToken))
                                                    .andReturn()
                                                    .getResponse()
                                                    .getStatus();
                                    assertThat(
                                                    org.springframework.security.core.context
                                                            .SecurityContextHolder.getContext()
                                                            .getAuthentication())
                                            .isNull();
                                    return status;
                                }));
                responses.add(
                        pool.submit(
                                () -> {
                                    start.await();
                                    int status =
                                            mvc.perform(
                                                            request(Endpoint.BOOK, f)
                                                                    .header(
                                                                            "Authorization",
                                                                            memberToken))
                                                    .andReturn()
                                                    .getResponse()
                                                    .getStatus();
                                    assertThat(
                                                    org.springframework.security.core.context
                                                            .SecurityContextHolder.getContext()
                                                            .getAuthentication())
                                            .isNull();
                                    return status;
                                }));
            }
            start.countDown();
            for (var response : responses)
                assertThat(response.get(20, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(200);
            for (Fixture f : List.of(a, b)) {
                assertThat(
                                jdbc.queryForObject(
                                        "select count(*) from active_memberships where gym_id=?",
                                        Long.class,
                                        f.gym().getId()))
                        .isEqualTo(2);
                assertThat(
                                jdbc.queryForObject(
                                        "select count(*) from trainer_sessions where gym_id=?",
                                        Long.class,
                                        f.gym().getId()))
                        .isEqualTo(2);
            }
            for (int round = 0; round < 2; round++)
                for (Fixture f : List.of(a, b)) {
                    var body =
                            mvc.perform(
                                            request(Endpoint.ADMIN_USERS, f)
                                                    .header("Authorization", token(f.owner())))
                                    .andExpect(status().isOk())
                                    .andReturn()
                                    .getResponse()
                                    .getContentAsString();
                    assertThat(body).doesNotContain(f == a ? "GYM_B_SENTINEL" : "GYM_A_SENTINEL");
                    mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
                }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void runtimeCredentialsCannotRunDdlOrReadMigrationInputs() throws Exception {
        try (var c = datasource.getConnection();
                var statement = c.createStatement()) {
            for (String forbidden :
                    List.of(
                            "alter table tenant_http.users add column forbidden text",
                            "select tenant_http.fitcore_scope_catalogs()",
                            "select * from tenant_operator_input.synthetic_manifest",
                            "select * from tenant_http.flyway_schema_history")) {
                assertThatThrownBy(() -> statement.execute(forbidden))
                        .isInstanceOf(java.sql.SQLException.class)
                        .satisfies(
                                error ->
                                        assertThat(((java.sql.SQLException) error).getSQLState())
                                                .isEqualTo("42501"));
            }
        }
    }

    @Test
    void onboardingCreatesCommittedGymAndOwner() throws Exception {
        mvc.perform(
                        post("/api/auth/register-owner")
                                .contentType("application/json")
                                .content(
                                        "{\"fullName\":\"Demo"
                                            + " Owner\",\"email\":\"newowner@fixture.invalid\",\"password\":\"StrongPass!2026\",\"gymName\":\"Demo"
                                            + " Gym\",\"slug\":\"demo-gym\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("user.role").value("OWNER"));
    }

    @Test
    @Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void invitationLifecycleAndTrainerRole() throws Exception {
        var response =
                mvc.perform(
                                post("/api/gym/invitations")
                                        .header("Authorization", token(a.owner()))
                                        .contentType("application/json")
                                        .content(
                                                "{\"email\":\"invite@fixture.invalid\",\"role\":\"TRAINER\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        var node = tools.jackson.databind.json.JsonMapper.builder().build().readTree(response);
        assertThat(node.get("invitationToken").asText()).hasSize(43);
        mvc.perform(get("/api/gym/invitations").header("Authorization", token(a.owner())))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.not(
                                                org.hamcrest.Matchers.containsString(
                                                        node.get("invitationToken").asText()))));
        mvc.perform(
                        delete("/api/gym/invitations/" + node.get("id").asText())
                                .header("Authorization", token(b.owner())))
                .andExpect(status().isNotFound());
    }

    @Test
    @Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void gymInformationIsScopedAndOwnerOnlyEditable() throws Exception {
        mvc.perform(get("/api/gym").header("Authorization", token(a.member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("id").value(a.gym().getId().toString()));
        mvc.perform(
                        patch("/api/gym")
                                .header("Authorization", token(a.admin()))
                                .contentType("application/json")
                                .content("{\"name\":\"Changed\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        patch("/api/gym")
                                .header("Authorization", token(a.owner()))
                                .contentType("application/json")
                                .content("{\"name\":\"Changed\"}"))
                .andExpect(status().isOk());
    }

    String ownerBody(String email, String slug) {
        return "{\"fullName\":\"Synthetic Owner\",\"email\":\""
                + email
                + "\",\"password\":\"StrongPass!2026\",\"gymName\":\"Synthetic Gym\",\"slug\":\""
                + slug
                + "\"}";
    }

    String acceptance(String email, String token) {
        return "{\"fullName\":\"Invited Person\",\"email\":\""
                + email
                + "\",\"password\":\"StrongPass!2026\",\"invitationToken\":\""
                + token
                + "\"}";
    }

    tools.jackson.databind.JsonNode invitation(Fixture f, String email, String role)
            throws Exception {
        return tools.jackson.databind.json.JsonMapper.builder()
                .build()
                .readTree(
                        mvc.perform(
                                        post("/api/gym/invitations")
                                                .header("Authorization", token(f.admin()))
                                                .contentType("application/json")
                                                .content(
                                                        "{\"email\":\""
                                                                + email
                                                                + "\",\"role\":\""
                                                                + role
                                                                + "\"}"))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
    }

    @Test
    @Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void ownerSignupDuplicatesLeaveNoOrphansAndBothGymsWork() throws Exception {
        for (int n = 0; n < 2; n++) {
            String email = UUID.randomUUID() + "@fixture.invalid",
                    slug = "gym-" + UUID.randomUUID();
            String body = ownerBody(email, slug);
            var response =
                    mvc.perform(
                                    post("/api/auth/register-owner")
                                            .contentType("application/json")
                                            .content(body))
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            var json = tools.jackson.databind.json.JsonMapper.builder().build().readTree(response);
            mvc.perform(
                            get("/api/gym")
                                    .header(
                                            "Authorization",
                                            "Bearer " + json.get("token").asText()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("id").value(json.get("user").get("gymId").asText()));
            long count = jdbc.queryForObject("select count(*) from gyms", Long.class);
            mvc.perform(
                            post("/api/auth/register-owner")
                                    .contentType("application/json")
                                    .content(body))
                    .andExpect(status().isConflict());
            mvc.perform(
                            post("/api/auth/register-owner")
                                    .contentType("application/json")
                                    .content(
                                            ownerBody(
                                                    UUID.randomUUID() + "@fixture.invalid", slug)))
                    .andExpect(status().isConflict());
            assertThat(jdbc.queryForObject("select count(*) from gyms", Long.class))
                    .isEqualTo(count);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"MEMBER", "TRAINER"})
    @Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void invitationAcceptanceBindsIdentityAndCannotReplay(String role) throws Exception {
        for (Fixture f : List.of(a, b)) {
            String email = UUID.randomUUID() + "@fixture.invalid";
            var issued = invitation(f, email, role);
            String secret = issued.get("invitationToken").asText();
            mvc.perform(
                            post("/api/auth/register")
                                    .contentType("application/json")
                                    .content(acceptance("wrong@fixture.invalid", secret)))
                    .andExpect(status().isBadRequest());
            var r =
                    mvc.perform(
                                    post("/api/auth/register")
                                            .contentType("application/json")
                                            .content(acceptance(email, secret)))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("user.gymId").value(f.gym().getId().toString()))
                            .andExpect(jsonPath("user.role").value(role))
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            var node = tools.jackson.databind.json.JsonMapper.builder().build().readTree(r);
            mvc.perform(
                            get("/api/auth/me")
                                    .header(
                                            "Authorization",
                                            "Bearer " + node.get("token").asText()))
                    .andExpect(status().isOk());
            mvc.perform(
                            post("/api/auth/register")
                                    .contentType("application/json")
                                    .content(acceptance(email, secret)))
                    .andExpect(status().isBadRequest());
            assertThat(
                            jdbc.queryForObject(
                                    "select token_hash from gym_invitations where id=?",
                                    String.class,
                                    UUID.fromString(issued.get("id").asText())))
                    .doesNotContain(secret);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"expired", "revoked", "collision"})
    @Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void unusableInvitationsCannotCreateOrConsumeAccounts(String reason) throws Exception {
        String email = UUID.randomUUID() + "@fixture.invalid";
        var issued = invitation(a, email, "MEMBER");
        UUID id = UUID.fromString(issued.get("id").asText());
        if (reason.equals("expired"))
            jdbc.update(
                    "update gym_invitations set expires_at=timestamp '2000-01-01 00:00:00' where"
                        + " id=?",
                    id);
        if (reason.equals("revoked"))
            mvc.perform(
                            delete("/api/gym/invitations/" + id)
                                    .header("Authorization", token(a.owner())))
                    .andExpect(status().isNoContent());
        if (reason.equals("collision"))
            new org.springframework.transaction.support.TransactionTemplate(transactions)
                    .executeWithoutResult(
                            status -> {
                                user(b.gym(), Role.MEMBER, "Collision").setEmail(email);
                                em.flush();
                            });
        long count = jdbc.queryForObject("select count(*) from users", Long.class);
        mvc.perform(
                        post("/api/auth/register")
                                .contentType("application/json")
                                .content(acceptance(email, issued.get("invitationToken").asText())))
                .andExpect(status().is(reason.equals("collision") ? 409 : 400));
        assertThat(jdbc.queryForObject("select count(*) from users", Long.class)).isEqualTo(count);
        assertThat(
                        jdbc.queryForObject(
                                "select accepted_at from gym_invitations where id=?",
                                Object.class,
                                id))
                .isNull();
    }

    @Test
    @Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentAcceptanceAndSignupHaveOneWinner() throws Exception {
        String email = UUID.randomUUID() + "@fixture.invalid";
        String secret = invitation(a, email, "MEMBER").get("invitationToken").asText();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            for (boolean owner : List.of(false, true)) {
                String path = owner ? "/api/auth/register-owner" : "/api/auth/register";
                String body =
                        owner
                                ? ownerBody(
                                        UUID.randomUUID() + "@fixture.invalid",
                                        "race-" + UUID.randomUUID())
                                : acceptance(email, secret);
                var start = new java.util.concurrent.CountDownLatch(1);
                List<java.util.concurrent.Future<Integer>> results = new ArrayList<>();
                for (int n = 0; n < 2; n++)
                    results.add(
                            pool.submit(
                                    () -> {
                                        start.await();
                                        return mvc.perform(
                                                        post(path)
                                                                .contentType("application/json")
                                                                .content(body))
                                                .andReturn()
                                                .getResponse()
                                                .getStatus();
                                    }));
                start.countDown();
                List<Integer> statuses = new ArrayList<>();
                for (var r : results)
                    statuses.add(r.get(20, java.util.concurrent.TimeUnit.SECONDS));
                assertThat(statuses).containsExactlyInAnyOrder(200, owner ? 409 : 400);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void revocationWhileAcceptanceWaitsCannotCreateAccount() throws Exception {
        String email = UUID.randomUUID() + "@fixture.invalid";
        var issued = invitation(a, email, "MEMBER");
        UUID id = UUID.fromString(issued.get("id").asText());
        var pool = java.util.concurrent.Executors.newSingleThreadExecutor();
        try (var c = datasource.getConnection()) {
            c.setAutoCommit(false);
            try (var lock = c.prepareStatement("select id from gyms where id=? for update")) {
                lock.setObject(1, a.gym().getId());
                lock.execute();
            }
            var accepting =
                    pool.submit(
                            () ->
                                    mvc.perform(
                                                    post("/api/auth/register")
                                                            .contentType("application/json")
                                                            .content(
                                                                    acceptance(
                                                                            email,
                                                                            issued.get(
                                                                                            "invitationToken")
                                                                                    .asText())))
                                            .andReturn()
                                            .getResponse()
                                            .getStatus());
            boolean waiting = false;
            for (int n = 0; n < 100; n++) {
                if (jdbc.queryForObject(
                                "select count(*) from pg_stat_activity where usename=current_user"
                                    + " and wait_event_type='Lock'",
                                Long.class)
                        > 0) {
                    waiting = true;
                    break;
                }
                Thread.sleep(50);
            }
            assertThat(waiting).as("Acceptance reached the held gym lock").isTrue();
            try (var revoke =
                    c.prepareStatement("update gym_invitations set revoked_at=? where id=?")) {
                revoke.setObject(1, java.time.LocalDateTime.of(2000, 1, 1, 0, 0));
                revoke.setObject(2, id);
                revoke.executeUpdate();
            }
            c.commit();
            assertThat(accepting.get(15, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(400);
            assertThat(
                            jdbc.queryForObject(
                                    "select count(*) from users where email=?", Long.class, email))
                    .isZero();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void onboardingRejectsPrivilegeAndTenantOverrides() throws Exception {
        String body =
                ownerBody(UUID.randomUUID() + "@fixture.invalid", "forged-" + UUID.randomUUID());
        for (String extra : List.of("\"role\":\"OWNER\"", "\"gymId\":\"" + a.gym().getId() + "\""))
            mvc.perform(
                            post("/api/auth/register-owner")
                                    .contentType("application/json")
                                    .content(
                                            body.substring(0, body.length() - 1)
                                                    + ","
                                                    + extra
                                                    + "}"))
                    .andExpect(status().isBadRequest());
        mvc.perform(
                        post("/api/gym/invitations")
                                .header("Authorization", token(a.member()))
                                .contentType("application/json")
                                .content(
                                        "{\"email\":\"denied@fixture.invalid\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isForbidden());
        for (String role : List.of("OWNER", "ADMIN"))
            mvc.perform(
                            post("/api/gym/invitations")
                                    .header("Authorization", token(a.owner()))
                                    .contentType("application/json")
                                    .content(
                                            "{\"email\":\"denied@fixture.invalid\",\"role\":\""
                                                    + role
                                                    + "\"}"))
                    .andExpect(status().isBadRequest());
    }
}
