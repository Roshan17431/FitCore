package com.fitcore.gym.tenancy;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fitcore.gym.domain.*;
import com.fitcore.gym.entity.*;
import com.fitcore.gym.security.JwtService;
import java.sql.Connection;
import java.util.concurrent.*;
import java.util.stream.*;
import javax.sql.DataSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;

/** Signed catalog HTTP contracts against a uniquely scoped, marked disposable PostgreSQL schema. */
@EnabledIfEnvironmentVariable(named = "FITCORE_TENANCY_TEST_TOKEN", matches = "[a-f0-9]{64}")
@SpringBootTest
@AutoConfigureMockMvc
@Timeout(40)
public class CatalogManagementTest {
    static final String SUFFIX = UUID.randomUUID().toString().replace("-", "");
    static final String SCHEMA = "catalog_" + SUFFIX;
    static final String RUNTIME = "catalog_runtime_" + SUFFIX;
    static final String PREFIX = "/api/admin/catalog";
    public static final Set<String> ENDPOINTS = java.util.stream.Stream.of("exercises", "workouts", "nutrition")
            .flatMap(kind -> java.util.stream.Stream.of("GET " + PREFIX + "/" + kind,
                    "POST " + PREFIX + "/" + kind, "GET " + PREFIX + "/" + kind + "/{}",
                    "PUT " + PREFIX + "/" + kind + "/{}", "POST " + PREFIX + "/" + kind + "/{}/archive"))
            .collect(java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toSet(), routes -> {
                routes.add("POST " + PREFIX + "/workouts/{}/publish"); return Set.copyOf(routes);
            }));

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry p) throws Exception {
        FoundationMigrationTest fixture = new FoundationMigrationTest();
        fixture.schema = SCHEMA;
        try (var c = fixture.connection(); var s = c.createStatement()) {
            s.execute("create schema " + SCHEMA);
        }
        fixture.flyway("latest").migrate();
        try (var c = fixture.connection(); var s = c.createStatement()) {
            s.execute("create role " + RUNTIME + " login password 'synthetic-runtime-only' nosuperuser nocreatedb nocreaterole noreplication");
            s.execute("grant usage on schema " + SCHEMA + " to " + RUNTIME);
            s.execute("grant select,insert,update,delete on all tables in schema " + SCHEMA + " to " + RUNTIME);
            s.execute("revoke all on " + SCHEMA + ".flyway_schema_history from " + RUNTIME);
            s.execute("grant select on " + SCHEMA + ".flyway_schema_history to " + RUNTIME);
            s.execute("grant usage,select on all sequences in schema " + SCHEMA + " to " + RUNTIME);
        }
        p.add("spring.datasource.url", () -> System.getenv("FITCORE_POSTGRES_TEST_URL"));
        p.add("spring.datasource.username", () -> RUNTIME);
        p.add("spring.datasource.password", () -> "synthetic-runtime-only");
        p.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        p.add("spring.datasource.hikari.schema", () -> SCHEMA);
        p.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        p.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        p.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        p.add("spring.jpa.defer-datasource-initialization", () -> "false");
        p.add("spring.flyway.enabled", () -> "false");
        p.add("spring.flyway.default-schema", () -> SCHEMA);
    }

    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactions;
    @Autowired RequestMappingHandlerMapping mappings;
    @Autowired com.fitcore.gym.service.CatalogService catalog;
    final JsonMapper json = JsonMapper.builder().build();
    Fixture a, b;
    User owner;

    record Fixture(Gym gym, User owner, User admin, User member, User trainer, Exercise exercise) {}
    record HttpResult(int status, String body, String resolvedException) {}

    <T> T persist(T row) { em.persist(row); return row; }

    User user(Gym gym, Role role) {
        return persist(User.builder().gymId(gym.getId()).fullName("Synthetic " + role)
                .email(UUID.randomUUID() + "@fixture.invalid").passwordHash("synthetic-catalog-hash")
                .role(role).enabled(true).build());
    }

    Fixture fixture(String label) {
        Gym gym = persist(Gym.builder().name("Synthetic Catalog " + label).slug(UUID.randomUUID().toString())
                .status(GymStatus.ACTIVE).build());
        User primary = user(gym, Role.OWNER); gym.setPrimaryOwner(primary);
        User admin = user(gym, Role.ADMIN), member = user(gym, Role.MEMBER), trainer = user(gym, Role.TRAINER);
        Exercise exercise = persist(Exercise.builder().gymId(gym.getId()).name("Fixture Bench")
                .category(label).difficulty(DifficultyLevel.BEGINNER).build());
        return new Fixture(gym, primary, admin, member, trainer, exercise);
    }

    @BeforeEach
    void seed() {
        new TransactionTemplate(transactions).executeWithoutResult(ignored -> {
            a = fixture("GYM_A_SENTINEL"); b = fixture("GYM_B_SENTINEL"); em.flush();
        });
        owner = a.owner();
    }

    HttpResult send(String method, String path, Object body, User actor) throws Exception {
        MockHttpServletRequestBuilder request = request(org.springframework.http.HttpMethod.valueOf(method), path)
                .header("Authorization", "Bearer " + jwt.generateToken(actor));
        if (body != null) request.contentType("application/json").content(json.writeValueAsString(body));
        var outcome = mvc.perform(request).andReturn();
        var response = outcome.getResponse();
        Throwable cause = outcome.getResolvedException();
        while (cause != null && cause.getCause() != null) cause = cause.getCause();
        return new HttpResult(response.getStatus(), response.getContentAsString(), cause == null ? "" : cause.toString());
    }

    JsonNode expect(String method, String path, Object body, User actor, int status) throws Exception {
        HttpResult result = send(method, path, body, actor);
        assertThat(result.status()).as(method + " " + path + " " + result).isEqualTo(status);
        return json.readTree(result.body());
    }

    JsonNode create(String kind, Map<String, Object> body, User actor) throws Exception {
        return expect("POST", PREFIX + "/" + kind, body, actor, 201);
    }

    Map<String, Object> exercise(String name) {
        return new LinkedHashMap<>(Map.of("name", name, "category", "Chest", "difficulty", "BEGINNER"));
    }

    Map<String, Object> workout(String level, List<Long> ids) {
        return new LinkedHashMap<>(Map.of("level", level, "duration", "30 min", "schedule", List.of("Monday", "Friday"), "exerciseIds", ids));
    }

    Map<String, Object> nutrition(String category) {
        return new LinkedHashMap<>(Map.of("category", category, "calories", 2000, "protein", 100,
                "carbs", 250, "fats", 60, "meals", List.of("Breakfast oats", "Dinner rice")));
    }

    Map<String, Object> payload(String kind) {
        return switch (kind) {
            case "exercises" -> exercise("Catalog Bench");
            case "workouts" -> workout("Catalog Routine", List.of(a.exercise().getId()));
            case "nutrition" -> nutrition("Catalog Balanced");
            default -> throw new IllegalArgumentException(kind);
        };
    }

    List<String> strings(JsonNode array) {
        List<String> result = new ArrayList<>(); array.forEach(value -> result.add(value.asText())); return result;
    }

    List<Long> ids(JsonNode array) {
        List<Long> result = new ArrayList<>(); array.forEach(value -> result.add(value.get("id").asLong())); return result;
    }

    @Test
    void ownerCanCreateExerciseInOwnGym() throws Exception {
        JsonNode created = create("exercises", exercise("Catalog Bench"), owner);
        assertThat(created.get("name").asText()).isEqualTo("Catalog Bench");
        assertThat(created.get("archived").asBoolean()).isFalse();
    }

    static Stream<Arguments> managersAndKinds() {
        return Stream.of(Role.OWNER, Role.ADMIN).flatMap(role -> Stream.of("exercises", "workouts", "nutrition")
                .map(kind -> Arguments.of(role, kind)));
    }

    @ParameterizedTest @MethodSource("managersAndKinds")
    void managersCanCreateReadUpdateAndArchiveLocalCatalog(Role role, String kind) throws Exception {
        User actor = role == Role.OWNER ? a.owner() : a.admin();
        Map<String, Object> body = payload(kind);
        JsonNode created = create(kind, body, actor); long id = created.get("id").asLong();
        String path = PREFIX + "/" + kind + "/" + id;
        assertThat(ids(expect("GET", PREFIX + "/" + kind, null, actor, 200))).contains(id);
        assertThat(expect("GET", path, null, actor, 200).get("id").asLong()).isEqualTo(id);
        String field = kind.equals("exercises") ? "description" : kind.equals("workouts") ? "duration" : "calories";
        Object value = kind.equals("exercises") ? "Revised instructions" : kind.equals("workouts") ? "45 min" : 2100;
        body.put(field, value);
        JsonNode changed = expect("PUT", path, body, actor, 200);
        assertThat(changed.get(field).asText()).isEqualTo(value.toString());
        JsonNode archived = expect("POST", path + "/archive", null, actor, 200);
        if (kind.equals("workouts")) assertThat(archived.get("status").asText()).isEqualTo("ARCHIVED");
        else assertThat(archived.get("archived").asBoolean()).isTrue();
        assertThat(expect("GET", path, null, actor, 200).get("id").asLong()).isEqualTo(id);
    }

    static Stream<Arguments> forbiddenRoutes() {
        return Stream.of(Role.MEMBER, Role.TRAINER).flatMap(role -> ENDPOINTS.stream().sorted()
                .map(route -> Arguments.of(role, route)));
    }

    @ParameterizedTest @MethodSource("forbiddenRoutes")
    void membersAndTrainersCannotReachAnyCatalogManagerRoute(Role role, String route) throws Exception {
        String[] parts = route.split(" ", 2);
        String path = parts[1].replace("{}", a.exercise().getId().toString());
        String kind = path.substring((PREFIX + "/").length()).split("/")[0];
        expect(parts[0], path, payload(kind), role == Role.MEMBER ? a.member() : a.trainer(), 403);
    }

    @ParameterizedTest @EnumSource(value = Role.class, names = {"MEMBER", "TRAINER"})
    void serviceBoundaryAlsoDeniesEveryManagerOperation(Role role) {
        var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        User account = role == Role.MEMBER ? a.member() : a.trainer();
        var actor = new com.fitcore.gym.security.TenantActor(account.getId(), account.getGymId(), role, account.getAuthzVersion());
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(actor, null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role))));
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);
        var ex = new com.fitcore.gym.dto.catalog.CatalogDtos.ExerciseInput("Denied", "Chest", DifficultyLevel.BEGINNER, null, null, null, null);
        var routine = new com.fitcore.gym.dto.catalog.CatalogDtos.WorkoutInput("Denied", "30 min", List.of(), List.of(a.exercise().getId()), null);
        var diet = new com.fitcore.gym.dto.catalog.CatalogDtos.NutritionInput("Denied", 2000, 100, 250, 60, List.of("Meal"));
        long id = a.exercise().getId();
        List<Runnable> calls = List.of(() -> catalog.exercises(), () -> catalog.exercise(id),
                () -> catalog.createExercise(ex), () -> catalog.updateExercise(id, ex), () -> catalog.archiveExercise(id),
                () -> catalog.workouts(), () -> catalog.workout(id), () -> catalog.createWorkout(routine),
                () -> catalog.updateWorkout(id, routine), () -> catalog.publishWorkout(id), () -> catalog.archiveWorkout(id),
                () -> catalog.nutrition(), () -> catalog.nutrition(id), () -> catalog.createNutrition(diet),
                () -> catalog.updateNutrition(id, diet), () -> catalog.archiveNutrition(id));
        try {
            calls.forEach(call -> assertThatThrownBy(call::run).isInstanceOf(org.springframework.security.access.AccessDeniedException.class));
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }

    @Test
    void routeMatrixCoversEveryCatalogControllerMapping() {
        Set<String> actual = new TreeSet<>();
        mappings.getHandlerMethods().forEach((mapping, handler) -> {
            for (String path : mapping.getPatternValues()) if (path.startsWith(PREFIX + "/"))
                mapping.getMethodsCondition().getMethods().forEach(method -> actual.add(method + " " + path.replaceAll("\\{[^}]+}", "{}")));
        });
        assertThat(actual).containsExactlyInAnyOrderElementsOf(ENDPOINTS);
        assertThat(ENDPOINTS).hasSize(16);
    }

    @ParameterizedTest @ValueSource(strings = {"exercises", "workouts", "nutrition"})
    void foreignItemsCannotBeReadEditedArchivedOrPublished(String kind) throws Exception {
        Map<String, Object> foreignBody = payload(kind);
        if (kind.equals("workouts")) foreignBody.put("exerciseIds", List.of(b.exercise().getId()));
        long id = create(kind, foreignBody, b.owner()).get("id").asLong();
        String path = PREFIX + "/" + kind + "/" + id;
        expect("GET", path, null, owner, 404);
        expect("PUT", path, payload(kind), owner, 404);
        expect("POST", path + "/archive", null, owner, 404);
        if (kind.equals("workouts")) expect("POST", path + "/publish", null, owner, 404);
        assertThat(ids(expect("GET", PREFIX + "/" + kind, null, owner, 200))).doesNotContain(id);
    }

    @ParameterizedTest @ValueSource(strings = {"exercises", "workouts", "nutrition"})
    void clientOwnershipAndStateFieldsCannotChooseGym(String kind) throws Exception {
        Map<String, Object> body = payload(kind);
        body.put("gymId", b.gym().getId()); body.put("ownerId", b.owner().getId());
        body.put("gym", Map.of("id", b.gym().getId()));
        String table = kind.equals("exercises") ? "exercises" : kind.equals("workouts") ? "workout_plans" : "nutrition_plans";
        long localBefore = jdbc.queryForObject("select count(*) from " + table + " where gym_id=?", Long.class, a.gym().getId());
        long foreignBefore = jdbc.queryForObject("select count(*) from " + table + " where gym_id=?", Long.class, b.gym().getId());
        // Existing global write protection rejects these fields rather than silently assigning them.
        expect("POST", PREFIX + "/" + kind, body, owner, 400);
        assertThat(jdbc.queryForObject("select count(*) from " + table + " where gym_id=?", Long.class, a.gym().getId())).isEqualTo(localBefore);
        assertThat(jdbc.queryForObject("select count(*) from " + table + " where gym_id=?", Long.class, b.gym().getId())).isEqualTo(foreignBefore);
        JsonNode created = create(kind, payload(kind), owner); long id = created.get("id").asLong();
        assertThat(jdbc.queryForObject("select gym_id from " + table + " where id=?", UUID.class, id)).isEqualTo(a.gym().getId());
        expect("GET", PREFIX + "/" + kind + "/" + id, null, b.owner(), 404);
    }

    @ParameterizedTest @ValueSource(strings = {"exercises", "workouts", "nutrition"})
    void duplicatesAreGymScopedAndArchivedNamesCannotBeRecycled(String kind) throws Exception {
        Map<String, Object> local = payload(kind), foreign = payload(kind);
        if (kind.equals("workouts")) foreign.put("exerciseIds", List.of(b.exercise().getId()));
        long id = create(kind, local, owner).get("id").asLong();
        create(kind, foreign, b.owner());
        expect("POST", PREFIX + "/" + kind, local, a.admin(), 409);
        expect("POST", PREFIX + "/" + kind + "/" + id + "/archive", null, owner, 200);
        expect("POST", PREFIX + "/" + kind, local, owner, 409);
        // PostgreSQL uniqueness remains case-sensitive.
        String field = kind.equals("exercises") ? "name" : kind.equals("workouts") ? "level" : "category";
        local.put(field, local.get(field).toString().toLowerCase(Locale.ROOT)); create(kind, local, owner);
    }

    @Test
    void publicLibrariesFollowArchiveAndPublicationWhileManagersKeepAllRecords() throws Exception {
        long exerciseId = create("exercises", exercise("Visible Bench"), owner).get("id").asLong();
        long nutritionId = create("nutrition", nutrition("Visible Estimate"), owner).get("id").asLong();
        long workoutId = create("workouts", workout("Visible Routine", List.of(exerciseId)), owner).get("id").asLong();
        assertThat(ids(expect("GET", "/api/workouts/plans", null, a.member(), 200))).doesNotContain(workoutId);
        expect("POST", PREFIX + "/workouts/" + workoutId + "/publish", null, a.admin(), 200);
        assertThat(ids(expect("GET", "/api/workouts/plans", null, a.member(), 200))).contains(workoutId);
        expect("POST", PREFIX + "/exercises/" + exerciseId + "/archive", null, owner, 409);
        expect("POST", PREFIX + "/workouts/" + workoutId + "/archive", null, owner, 200);
        expect("POST", PREFIX + "/exercises/" + exerciseId + "/archive", null, owner, 200);
        expect("POST", PREFIX + "/nutrition/" + nutritionId + "/archive", null, owner, 200);
        assertThat(ids(expect("GET", "/api/workouts/exercises", null, a.member(), 200))).doesNotContain(exerciseId, b.exercise().getId());
        assertThat(ids(expect("GET", "/api/workouts/plans", null, a.member(), 200))).doesNotContain(workoutId);
        assertThat(ids(expect("GET", "/api/nutrition/plans", null, a.member(), 200))).doesNotContain(nutritionId);
        assertThat(ids(expect("GET", PREFIX + "/exercises", null, owner, 200))).contains(exerciseId);
    }

    @Test
    void emptyDraftCanExistButCannotPublishAndPublishedSelectionsCannotBeCleared() throws Exception {
        long id = create("workouts", workout("Empty Draft", List.of()), owner).get("id").asLong();
        String path = PREFIX + "/workouts/" + id;
        assertThat(expect("GET", path, null, owner, 200).get("status").asText()).isEqualTo("DRAFT");
        expect("POST", path + "/publish", null, owner, 400);
        expect("PUT", path, workout("Empty Draft", List.of(a.exercise().getId())), owner, 200);
        expect("POST", path + "/publish", null, owner, 200);
        expect("PUT", path, workout("Empty Draft", List.of()), owner, 400);
        assertThat(strings(expect("GET", path, null, owner, 200).get("exercises"))).containsExactly("Fixture Bench");
    }

    @Test
    void validExplicitPublicationAndMetadataOnlyLegacyUpdatesPreserveNames() throws Exception {
        Map<String, Object> published = workout("Explicit Published", List.of(a.exercise().getId())); published.put("status", "PUBLISHED");
        assertThat(create("workouts", published, owner).get("status").asText()).isEqualTo("PUBLISHED");
        long id = jdbc.queryForObject("insert into workout_plans(gym_id,level,duration,status) values(?,'Legacy Routine','30 min','PUBLISHED') returning id", Long.class, a.gym().getId());
        jdbc.update("insert into workout_plan_exercises(gym_id,plan_id,exercise_name) values(?,?,?)", a.gym().getId(), id, "Unresolved Legacy Name");
        Map<String, Object> metadata = new LinkedHashMap<>(Map.of("level", "Legacy Routine", "duration", "40 min", "schedule", List.of("Saturday")));
        JsonNode changed = expect("PUT", PREFIX + "/workouts/" + id, metadata, owner, 200);
        assertThat(strings(changed.get("exercises"))).containsExactly("Unresolved Legacy Name");
        assertThat(strings(changed.get("unresolvedExercises"))).containsExactly("Unresolved Legacy Name");
        assertThat(changed.get("status").asText()).isEqualTo("PUBLISHED");
        expect("POST", PREFIX + "/workouts/" + id + "/publish", null, owner, 400);
        metadata.put("status", "DRAFT"); metadata.put("exerciseIds", List.of());
        assertThat(expect("PUT", PREFIX + "/workouts/" + id, metadata, owner, 200).get("exercises").size()).isZero();
    }

    @Test
    void unchangedPublishedStatusOnLegacyMetadataEditPreservesUnresolvedNames() throws Exception {
        long id = jdbc.queryForObject("insert into workout_plans(gym_id,level,duration,status) values(?,'Legacy Explicit Status','30 min','PUBLISHED') returning id", Long.class, a.gym().getId());
        jdbc.update("insert into workout_plan_exercises(gym_id,plan_id,exercise_name) values(?,?,?)", a.gym().getId(), id, "Unresolved Legacy Name");
        Map<String, Object> metadata = Map.of("level", "Legacy Explicit Status", "duration", "45 min",
                "schedule", List.of("Saturday"), "status", "PUBLISHED");
        JsonNode changed = expect("PUT", PREFIX + "/workouts/" + id, metadata, owner, 200);
        assertThat(changed.get("duration").asText()).isEqualTo("45 min");
        assertThat(changed.get("status").asText()).isEqualTo("PUBLISHED");
        assertThat(strings(changed.get("exercises"))).containsExactly("Unresolved Legacy Name");
        assertThat(strings(changed.get("unresolvedExercises"))).containsExactly("Unresolved Legacy Name");
        assertThat(changed.get("exerciseIds").size()).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"create", "update"})
    void explicitEmptySelectionsAreRejectedForArchivedCreatesAndUpdates(String operation) throws Exception {
        Map<String, Object> archived = workout("Rejected Archived Empty", List.of()); archived.put("status", "ARCHIVED");
        if (operation.equals("create")) {
            long before = jdbc.queryForObject("select count(*) from workout_plans where gym_id=?", Long.class, a.gym().getId());
            expect("POST", PREFIX + "/workouts", archived, owner, 400);
            assertThat(jdbc.queryForObject("select count(*) from workout_plans where gym_id=?", Long.class, a.gym().getId())).isEqualTo(before);
        } else {
            long id = create("workouts", workout("Retained Draft Selection", List.of(a.exercise().getId())), owner).get("id").asLong();
            JsonNode before = expect("GET", PREFIX + "/workouts/" + id, null, owner, 200);
            expect("PUT", PREFIX + "/workouts/" + id, archived, owner, 400);
            assertThat(expect("GET", PREFIX + "/workouts/" + id, null, owner, 200)).isEqualTo(before);
        }
    }

    @Test
    void draftMayRetainArchivedReferenceButCannotPublishIt() throws Exception {
        long id = create("workouts", workout("Draft Holds Reference", List.of(a.exercise().getId())), owner).get("id").asLong();
        expect("POST", PREFIX + "/exercises/" + a.exercise().getId() + "/archive", null, owner, 200);
        JsonNode draft = expect("PUT", PREFIX + "/workouts/" + id, Map.of("level", "Draft Holds Reference", "duration", "50 min", "schedule", List.of()), owner, 200);
        assertThat(strings(draft.get("exercises"))).containsExactly("Fixture Bench");
        expect("POST", PREFIX + "/workouts/" + id + "/publish", null, owner, 409);
    }

    @Test
    void foreignNestedIdsRollbackCreateAndExistingCollectionsAtomically() throws Exception {
        long before = jdbc.queryForObject("select count(*) from workout_plans where gym_id=?", Long.class, a.gym().getId());
        expect("POST", PREFIX + "/workouts", workout("Foreign Selection", List.of(a.exercise().getId(), b.exercise().getId())), owner, 404);
        assertThat(jdbc.queryForObject("select count(*) from workout_plans where gym_id=?", Long.class, a.gym().getId())).isEqualTo(before);
        long id = create("workouts", workout("Atomic Original", List.of(a.exercise().getId())), owner).get("id").asLong();
        Map<String, Object> invalid = workout("Changed But Rejected", List.of(a.exercise().getId(), b.exercise().getId()));
        expect("PUT", PREFIX + "/workouts/" + id, invalid, owner, 404);
        JsonNode retained = expect("GET", PREFIX + "/workouts/" + id, null, owner, 200);
        assertThat(retained.get("level").asText()).isEqualTo("Atomic Original");
        assertThat(strings(retained.get("schedule"))).containsExactlyInAnyOrder("Monday", "Friday");
        assertThat(strings(retained.get("exercises"))).containsExactly("Fixture Bench");
    }

    @Test
    void invalidNutritionMealUpdatePreservesTheEntireRecord() throws Exception {
        long id = create("nutrition", nutrition("Atomic Estimate"), owner).get("id").asLong();
        JsonNode before = expect("GET", PREFIX + "/nutrition/" + id, null, owner, 200);
        Map<String, Object> invalid = nutrition("Should Roll Back"); invalid.put("calories", 2400);
        invalid.put("meals", List.of("Valid first meal", "x".repeat(256)));
        expect("PUT", PREFIX + "/nutrition/" + id, invalid, owner, 400);
        assertThat(expect("GET", PREFIX + "/nutrition/" + id, null, owner, 200)).isEqualTo(before);
    }

    @Test
    void archivedLocalSelectionsCannotBeIntroducedIntoNewOrExistingDrafts() throws Exception {
        long id = create("workouts", workout("Stable Draft", List.of()), owner).get("id").asLong();
        expect("POST", PREFIX + "/exercises/" + a.exercise().getId() + "/archive", null, owner, 200);
        expect("POST", PREFIX + "/workouts", workout("Archived Selection", List.of(a.exercise().getId())), owner, 409);
        expect("PUT", PREFIX + "/workouts/" + id, workout("Changed Draft", List.of(a.exercise().getId())), owner, 409);
        JsonNode retained = expect("GET", PREFIX + "/workouts/" + id, null, owner, 200);
        assertThat(retained.get("level").asText()).isEqualTo("Stable Draft");
        assertThat(retained.get("exercises").size()).isZero();
    }

    static Stream<Arguments> invalidPayloads() {
        List<Arguments> cases = new ArrayList<>();
        for (String kind : List.of("exercises", "workouts", "nutrition")) {
            String field = kind.equals("exercises") ? "name" : kind.equals("workouts") ? "level" : "category";
            cases.add(Arguments.of(kind, field, "   ")); cases.add(Arguments.of(kind, field, "x".repeat(101)));
        }
        for (Object value : List.of(0, 101)) cases.add(Arguments.of("exercises", "recommendedSets", value));
        cases.add(Arguments.of("exercises", "difficulty", "EXPERT"));
        cases.add(Arguments.of("exercises", "category", " "));
        cases.add(Arguments.of("exercises", "recommendedReps", "x".repeat(101)));
        cases.add(Arguments.of("exercises", "description", "x".repeat(4001)));
        cases.add(Arguments.of("exercises", "imageUrl", "/images/" + "x".repeat(244) + ".png"));
        cases.add(Arguments.of("workouts", "duration", " "));
        cases.add(Arguments.of("workouts", "duration", "x".repeat(101)));
        cases.add(Arguments.of("workouts", "status", "INVALID"));
        cases.add(Arguments.of("workouts", "exerciseIds", List.of(0)));
        cases.add(Arguments.of("workouts", "exerciseIds", List.of(-1)));
        cases.add(Arguments.of("workouts", "exerciseIds", LongStream.rangeClosed(1, 101).boxed().toList()));
        for (String field : List.of("schedule", "meals")) {
            String kind = field.equals("schedule") ? "workouts" : "nutrition";
            cases.add(Arguments.of(kind, field, List.of(" ")));
            cases.add(Arguments.of(kind, field, List.of("x".repeat(256))));
            cases.add(Arguments.of(kind, field, Collections.nCopies(32, "Valid text")));
        }
        for (String field : List.of("calories", "protein", "carbs", "fats")) {
            cases.add(Arguments.of("nutrition", field, -1));
            cases.add(Arguments.of("nutrition", field, field.equals("calories") ? 20001 : 2001));
        }
        return cases.stream();
    }

    @ParameterizedTest @MethodSource("invalidPayloads")
    void invalidBoundsAndEnumsAreRejectedWithoutPartialRows(String kind, String field, Object invalid) throws Exception {
        Map<String, Object> body = payload(kind); body.put(field, invalid);
        expect("POST", PREFIX + "/" + kind, body, owner, 400);
    }

    @Test
    void duplicateExerciseIdsAndMissingNutritionMacrosAreRejected() throws Exception {
        expect("POST", PREFIX + "/workouts", workout("Duplicate IDs", List.of(a.exercise().getId(), a.exercise().getId())), owner, 400);
        for (String field : List.of("calories", "protein", "carbs", "fats")) {
            Map<String, Object> body = nutrition("Missing " + field); body.remove(field);
            expect("POST", PREFIX + "/nutrition", body, owner, 400);
        }
    }

    @Test
    void trimmedInputsAndMaximumCollectionStringsRoundTripWithoutInventedEnums() throws Exception {
        Map<String, Object> body = exercise("  Trimmed Bench  "); body.put("category", "  Freeform Category  ");
        JsonNode ex = create("exercises", body, owner);
        assertThat(ex.get("name").asText()).isEqualTo("Trimmed Bench");
        assertThat(ex.get("category").asText()).isEqualTo("Freeform Category");
        Map<String, Object> routine = workout("Flexible Routine", List.of(ex.get("id").asLong()));
        routine.put("duration", "Two short blocks, as scheduled"); routine.put("schedule", List.of("x".repeat(255)));
        assertThat(strings(create("workouts", routine, owner).get("schedule"))).containsExactly("x".repeat(255));
        Map<String, Object> diet = nutrition("Flexible Estimate"); diet.put("meals", List.of("x".repeat(255)));
        assertThat(strings(create("nutrition", diet, owner).get("meals"))).containsExactly("x".repeat(255));
    }

    @Test
    void unsafeNewImagesAreRejectedAndUnreviewedLegacyReferencesAreNeverApproved() throws Exception {
        for (String url : List.of("https://untrusted.fixture.invalid/image.png", "http://fixture.invalid/a.png", "javascript:alert(1)", "//fixture.invalid/a.png", "/images/../secret")) {
            Map<String, Object> body = exercise("Unsafe " + UUID.randomUUID()); body.put("imageUrl", url);
            expect("POST", PREFIX + "/exercises", body, owner, 400);
        }
        Map<String, Object> local = exercise("Safe Image"); local.put("imageUrl", "/images/exercises/bench.png");
        assertThat(create("exercises", local, owner).get("imageApproved").asBoolean()).isTrue();
        jdbc.update("update exercises set image_url='https://legacy.fixture.invalid/unchanged.png' where id=?", a.exercise().getId());
        Map<String, Object> metadata = exercise("Fixture Bench"); metadata.put("imageUrl", "https://legacy.fixture.invalid/unchanged.png");
        JsonNode retained = expect("PUT", PREFIX + "/exercises/" + a.exercise().getId(), metadata, owner, 200);
        assertThat(retained.get("imageApproved").asBoolean()).isFalse();
        JsonNode publicList = expect("GET", "/api/workouts/exercises", null, a.member(), 200);
        for (JsonNode item : publicList) if (item.get("id").asLong() == a.exercise().getId())
            assertThat(item.get("imageApproved").asBoolean()).isFalse();
    }

    @Test
    void namesReferencedByDraftArchivedPlanOrHistoryCannotBeChanged() throws Exception {
        long routine = create("workouts", workout("Reference Routine", List.of(a.exercise().getId())), owner).get("id").asLong();
        expect("PUT", PREFIX + "/exercises/" + a.exercise().getId(), exercise("Renamed Bench"), owner, 409);
        expect("POST", PREFIX + "/workouts/" + routine + "/archive", null, owner, 200);
        expect("PUT", PREFIX + "/exercises/" + a.exercise().getId(), exercise("Renamed Bench"), owner, 409);
        long historyExercise = create("exercises", exercise("History Bench"), owner).get("id").asLong();
        expect("POST", "/api/workouts/logs/" + a.member().getId(), logBody(historyExercise), a.member(), 200);
        expect("PUT", PREFIX + "/exercises/" + historyExercise, exercise("Rewritten History"), owner, 409);
        Map<String, Object> metadata = exercise("History Bench"); metadata.put("description", "Safe instruction correction");
        expect("PUT", PREFIX + "/exercises/" + historyExercise, metadata, owner, 200);
    }

    @Test
    void foreignOnlySameNameReferencesDoNotBlockLocalRenameOrArchive() throws Exception {
        Map<String, Object> foreignRoutine = workout("Foreign Published Routine", List.of(b.exercise().getId()));
        foreignRoutine.put("status", "PUBLISHED");
        long planId = create("workouts", foreignRoutine, b.owner()).get("id").asLong();
        Map<String, Object> foreignLog = new LinkedHashMap<>(logBody(b.exercise().getId()));
        foreignLog.put("userId", b.member().getId());
        expect("POST", "/api/workouts/logs/" + b.member().getId(), foreignLog, b.member(), 200);
        JsonNode before = expect("GET", PREFIX + "/workouts/" + planId, null, b.owner(), 200);
        // Both gyms call this exercise Fixture Bench; only GymB has references.
        expect("POST", PREFIX + "/exercises/" + a.exercise().getId() + "/archive", null, owner, 200);
        JsonNode renamed = expect("PUT", PREFIX + "/exercises/" + a.exercise().getId(), exercise("Gym A Unreferenced Rename"), owner, 200);
        assertThat(renamed.get("name").asText()).isEqualTo("Gym A Unreferenced Rename");
        assertThat(renamed.get("archived").asBoolean()).isTrue();
        assertThat(expect("GET", PREFIX + "/workouts/" + planId, null, b.owner(), 200)).isEqualTo(before);
        JsonNode history = expect("GET", "/api/workouts/logs/" + b.member().getId(), null, b.member(), 200);
        assertThat(history.get(0).get("exercises").get(0).get("exercise").get("name").asText()).isEqualTo("Fixture Bench");
    }

    Map<String, Object> logBody(long... exerciseIds) {
        return Map.of("userId", a.member().getId(), "date", "2030-06-03", "durationMinutes", 37, "notes", "Preserve historical values",
                "exercises", Arrays.stream(exerciseIds).mapToObj(id -> Map.of("exercise", Map.of("id", id, "name", "Client Supplied Name", "category", "Chest", "difficulty", "BEGINNER"),
                        "setsCompleted", 4, "repsCompleted", "12,10,8,8", "weightUsed", 42.5)).toList());
    }

    @Test
    void archivalPreservesHistoryIdsAndValuesAndRejectsOnlyNewLogging() throws Exception {
        long exercise = a.exercise().getId();
        JsonNode log = expect("POST", "/api/workouts/logs/" + a.member().getId(), logBody(exercise), a.member(), 200);
        long logId = log.get("id").asLong();
        JsonNode before = expect("GET", "/api/workouts/logs/" + a.member().getId(), null, a.member(), 200);
        expect("POST", PREFIX + "/exercises/" + exercise + "/archive", null, owner, 200);
        JsonNode after = expect("GET", "/api/workouts/logs/" + a.member().getId(), null, a.member(), 200);
        assertThat(after).isEqualTo(before);
        assertThat(after.get(0).get("id").asLong()).isEqualTo(logId);
        assertThat(after.get(0).get("exercises").get(0).get("exercise").get("name").asText()).isEqualTo("Fixture Bench");
        assertThat(after.get(0).get("exercises").get(0).get("weightUsed").asDouble()).isEqualTo(42.5);
        HttpResult rejected = send("POST", "/api/workouts/logs/" + a.member().getId(), logBody(exercise), a.member());
        assertThat(rejected.status()).as(rejected.body()).isEqualTo(409);
        assertThat(jdbc.queryForObject("select count(*) from workout_logs where user_id=?", Long.class, a.member().getId())).isEqualTo(1);
    }
    @Test
    void loggingWaitsForArchiveAndRejectsFreshlyArchivedExerciseWithoutStaleLookup() throws Exception {
        ExecutorService workers = Executors.newSingleThreadExecutor();
        Future<HttpResult> request = null;
        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            int pid = connectionPid(holder);
            lockExercise(holder, a.exercise().getId(), "FOR NO KEY UPDATE");
            request = workers.submit(() -> send("POST", "/api/workouts/logs/" + a.member().getId(),
                    logBody(a.exercise().getId()), a.member()));
            try {
                awaitBlocked(request, pid);
                FoundationMigrationTest.execute(holder, "update exercises set archived=true where id=?", a.exercise().getId());
                holder.commit();
            } finally { holder.rollback(); }
            HttpResult result = request.get(10, TimeUnit.SECONDS);
            assertThat(result.status()).as(result.body()).isEqualTo(409);
            assertThat(jdbc.queryForObject("select count(*) from workout_logs where user_id=?", Long.class, a.member().getId())).isZero();
        } finally { stop(workers, request); }
    }

    @Test
    void loggingWaitsForEditAndUsesFreshCanonicalName() throws Exception {
        ExecutorService workers = Executors.newSingleThreadExecutor();
        Future<HttpResult> request = null;
        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            int pid = connectionPid(holder);
            lockExercise(holder, a.exercise().getId(), "FOR NO KEY UPDATE");
            request = workers.submit(() -> send("POST", "/api/workouts/logs/" + a.member().getId(),
                    logBody(a.exercise().getId()), a.member()));
            try {
                awaitBlocked(request, pid);
                FoundationMigrationTest.execute(holder, "update exercises set name='Committed New Name' where id=?", a.exercise().getId());
                holder.commit();
            } finally { holder.rollback(); }
            HttpResult result = request.get(10, TimeUnit.SECONDS);
            assertThat(result.status()).as(result.body()).isEqualTo(200);
            assertThat(json.readTree(result.body()).get("exercises").get(0).get("exercise").get("name").asText())
                    .isEqualTo("Committed New Name");
        } finally { stop(workers, request); }
    }

    @Test
    void loggingLocksMultipleExercisesInAscendingIdOrder() throws Exception {
        long low = a.exercise().getId(), high = create("exercises", exercise("Higher ID Bench"), owner).get("id").asLong();
        ExecutorService workers = Executors.newSingleThreadExecutor();
        Future<HttpResult> request = null;
        try (Connection lowHolder = dataSource.getConnection(); Connection highProbe = dataSource.getConnection()) {
            lowHolder.setAutoCommit(false); highProbe.setAutoCommit(false);
            lockExercise(lowHolder, low, "FOR NO KEY UPDATE");
            request = workers.submit(() -> send("POST", "/api/workouts/logs/" + a.member().getId(), logBody(high, low), a.member()));
            try {
                awaitBlocked(request, connectionPid(lowHolder));
                FoundationMigrationTest.execute(highProbe, "set local lock_timeout='500ms'");
                // A reversed request must wait on low before taking any lock on high.
                lockExercise(highProbe, high, "FOR NO KEY UPDATE");
                highProbe.rollback(); lowHolder.commit();
            } finally { highProbe.rollback(); lowHolder.rollback(); }
            HttpResult result = request.get(10, TimeUnit.SECONDS);
            assertThat(result.status()).as(result.body()).isEqualTo(200);
            JsonNode logged = json.readTree(result.body()).get("exercises");
            List<Long> referenced = new ArrayList<>(); logged.forEach(item -> referenced.add(item.get("exercise").get("id").asLong()));
            assertThat(referenced).containsExactlyInAnyOrder(low, high);
        } finally { stop(workers, request); }
    }

    @ParameterizedTest @ValueSource(strings = {"rename", "archive"})
    void managerWaitsForActualLogTransactionBeforeCheckingReferences(String operation) throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch logReturned = new CountDownLatch(1), releaseLog = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger logPid = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<HttpResult> logResponse = new java.util.concurrent.atomic.AtomicReference<>();
        Future<HttpResult> logging = null, manager = null;
        try {
            logging = workers.submit(() -> new TransactionTemplate(transactions).execute(ignored -> {
                try {
                    logPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                    logResponse.set(send("POST", "/api/workouts/logs/" + a.member().getId(), logBody(a.exercise().getId()), a.member()));
                    logReturned.countDown();
                    if (!releaseLog.await(15, TimeUnit.SECONDS)) throw new AssertionError("Log release deadline exceeded");
                    return logResponse.get();
                } catch (Exception error) { throw new IllegalStateException(error); }
            }));
            assertThat(logReturned.await(10, TimeUnit.SECONDS)).as("HTTP log reached uncommitted transaction gate").isTrue();
            assertThat(logResponse.get().status()).as(logResponse.get().body()).isEqualTo(200);
            manager = workers.submit(() -> operation.equals("rename")
                    ? send("PUT", PREFIX + "/exercises/" + a.exercise().getId(), exercise("Racing Rename"), owner)
                    : send("POST", PREFIX + "/exercises/" + a.exercise().getId() + "/archive", null, owner));
            awaitBlocked(manager, logPid.get());
            releaseLog.countDown();
            assertThat(logging.get(10, TimeUnit.SECONDS).status()).isEqualTo(200);
            HttpResult result = manager.get(10, TimeUnit.SECONDS);
            assertThat(result.status()).as(result.body()).isEqualTo(operation.equals("rename") ? 409 : 200);
            JsonNode history = expect("GET", "/api/workouts/logs/" + a.member().getId(), null, a.member(), 200);
            assertThat(history.get(0).get("exercises").get(0).get("exercise").get("name").asText()).isEqualTo("Fixture Bench");
            assertThat(history.get(0).get("exercises").get(0).get("setsCompleted").asInt()).isEqualTo(4);
        } finally { releaseLog.countDown(); stop(workers, logging, manager); }
    }

    @Test
    void managerGymSerializationRetainsForeignKeyCompatibleLockMode() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        Future<HttpResult> manager = null, logging = null;
        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            FoundationMigrationTest.execute(holder, "select id from gyms where id=? for no key update", a.gym().getId());
            manager = workers.submit(() -> send("POST", PREFIX + "/nutrition", nutrition("Serialized Estimate"), owner));
            try {
                awaitBlocked(manager, connectionPid(holder));
                logging = workers.submit(() -> send("POST", "/api/workouts/logs/" + a.member().getId(), logBody(a.exercise().getId()), a.member()));
                HttpResult logged = logging.get(5, TimeUnit.SECONDS);
                assertThat(logged.status()).as(logged.body()).isEqualTo(200);
            } finally { holder.rollback(); }
            HttpResult changed = manager.get(10, TimeUnit.SECONDS);
            assertThat(changed.status()).as(changed.body()).isEqualTo(201);
        } finally { stop(workers, manager, logging); }
    }

    int connectionPid(Connection connection) throws Exception {
        try (var s = connection.createStatement(); var result = s.executeQuery("select pg_backend_pid()")) { result.next(); return result.getInt(1); }
    }

    void lockExercise(Connection connection, long id, String mode) throws Exception {
        try (var statement = connection.prepareStatement("select id from exercises where id=? " + mode)) {
            statement.setLong(1, id);
            try (var result = statement.executeQuery()) { assertThat(result.next()).isTrue(); }
        }
    }

    void awaitBlocked(Future<HttpResult> request, int holderPid) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        do {
            if (request.isDone()) throw new AssertionError("HTTP request completed before required row lock: " + request.get());
            Long waiting = jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and usename=current_user and ?=any(pg_blocking_pids(pid))", Long.class, holderPid);
            if (waiting != null && waiting > 0) return;
            Thread.sleep(10);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("HTTP transaction did not contend on expected PostgreSQL holder within8s");
    }

    void stop(ExecutorService workers, Future<?>... requests) throws Exception {
        for (Future<?> request : requests) if (request != null && !request.isDone()) request.cancel(true);
        workers.shutdownNow();
        assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).as("Catalog HTTP workers terminated").isTrue();
    }

}
