package com.fitcore.gym.security;

import com.fitcore.gym.domain.Role;
import com.fitcore.gym.entity.*;
import com.fitcore.gym.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.junit.jupiter.api.extension.ExtendWith(com.fitcore.gym.tenancy.RegressionGym.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityRegressionTest {
    private static final String PRIVATE_PASSWORD = "Private security fixture password 2026!";
    private static final String PRIVATE_HASH = new BCryptPasswordEncoder().encode(PRIVATE_PASSWORD);
    private static final String PUBLIC_DEMO_PASSWORD = "Fitcore@123";

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder encoder;
    @Autowired UserRepository users;
    @Autowired AttendanceRepository attendance;
    @Autowired MembershipPlanRepository plans;
    @Autowired TrainerProfileRepository trainers;
    @Autowired NutritionGoalRepository goals;
    @Autowired JdbcTemplate jdbc;
    @Autowired Environment environment;
    @Autowired com.fitcore.gym.service.AdminService adminService;

    private User viewer;
    private User other;
    private User admin;
    private TrainerProfile trainer;
    private MembershipPlan plan;
    private Attendance viewerVisit;
    private Attendance otherVisit;

    @Test
    void ordinaryServiceUpdatesCannotAssignOwner() {
        var request = new com.fitcore.gym.dto.admin.UserUpdateRequest();
        request.setFullName(viewer.getFullName());
        request.setEmail(viewer.getEmail());
        request.setRole(Role.OWNER);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> adminService.updateUser(viewer.getId(), request))
                .isInstanceOf(com.fitcore.gym.exception.BadRequestException.class);
        assertThat(users.findById(viewer.getId()).orElseThrow().getRole()).isEqualTo(Role.MEMBER);
    }

    enum Endpoint {
        PROFILE("GET", "/api/users/{user}"),
        DASHBOARD("GET", "/api/dashboard/{user}"),
        MEMBERSHIPS("GET", "/api/memberships/user/{user}"),
        ACTIVE_MEMBERSHIP("GET", "/api/memberships/user/{user}/active"),
        WORKOUT_HISTORY("GET", "/api/workouts/logs/{user}"),
        NUTRITION_DASHBOARD("GET", "/api/nutrition/dashboard/{user}"),
        NUTRITION_GOAL("GET", "/api/nutrition/goal/{user}"),
        FOOD_HISTORY("GET", "/api/nutrition/logs/{user}"),
        PROGRESS("GET", "/api/tracking/progress/{user}"),
        ATTENDANCE("GET", "/api/tracking/attendance/{user}"),
        SESSIONS("GET", "/api/trainers/sessions/{user}"),
        NOTIFICATIONS("GET", "/api/notifications/{user}"),
        SUBSCRIBE("POST", "/api/memberships/subscribe/{user}/{plan}"),
        LOG_WORKOUT("POST", "/api/workouts/logs/{user}"),
        SET_GOAL("POST", "/api/nutrition/goal/{user}"),
        LOG_FOOD("POST", "/api/nutrition/log/{user}"),
        LOG_PROGRESS("POST", "/api/tracking/progress/{user}"),
        CHECK_IN("POST", "/api/tracking/attendance/check-in/{user}"),
        CHECK_OUT("PUT", "/api/tracking/attendance/check-out/{visit}"),
        BOOK_TRAINER("POST", "/api/trainers/book/{user}/{trainer}");

        final String method;
        final String path;
        Endpoint(String method, String path) { this.method = method; this.path = path; }
    }

    @BeforeEach
    void fixtures() {
        viewer = user("viewer", Role.MEMBER);
        other = user("other", Role.MEMBER);
        admin = user("private-admin", Role.ADMIN);
        trainer = trainers.saveAndFlush(TrainerProfile.builder().gymId(com.fitcore.gym.tenancy.RegressionGym.ID).user(user("coach", Role.TRAINER))
                .specialization("Strength").experienceYears(3)
                .certifications(List.of()).availableSlots(List.of()).build());
        plan = plans.saveAndFlush(MembershipPlan.builder().gymId(com.fitcore.gym.tenancy.RegressionGym.ID).name("Security fixture")
                .price(new BigDecimal("29.00")).durationMonths(1).recommended(false)
                .features(List.of("Gym access")).build());
        viewerVisit = visit(viewer);
        otherVisit = visit(other);
        goals.saveAndFlush(NutritionGoal.builder().gymId(com.fitcore.gym.tenancy.RegressionGym.ID).user(other).targetCalories(2000)
                .targetProtein(120).targetCarbs(220).targetFat(60).build());
    }

    static Stream<Arguments> nonAdminEndpoints() {
        return Stream.of(Role.MEMBER, Role.TRAINER)
                .flatMap(role -> Arrays.stream(Endpoint.values()).map(endpoint -> Arguments.of(endpoint, role)));
    }

    @ParameterizedTest(name = "{1} cannot access another user's {0}")
    @MethodSource("nonAdminEndpoints")
    void nonAdminsCannotReadOrWriteAnotherUsersRecords(Endpoint endpoint, Role role) throws Exception {
        viewer.setRole(role);
        users.saveAndFlush(viewer);
        var before = snapshot();
        request(endpoint, other, jwt.generateToken(viewer)).andExpect(status().isForbidden());
        assertThat(snapshot()).as("Denied requests must not mutate personal records").isEqualTo(before);
    }

    @ParameterizedTest(name = "{1} can use own {0}")
    @MethodSource("nonAdminEndpoints")
    void ownersCanStillReadAndWriteTheirRecords(Endpoint endpoint, Role role) throws Exception {
        viewer.setRole(role);
        users.saveAndFlush(viewer);
        request(endpoint, viewer, jwt.generateToken(viewer)).andExpect(status().is2xxSuccessful());
    }

    @ParameterizedTest
    @EnumSource(Endpoint.class)
    void privateAdministratorsCanManageMemberRecords(Endpoint endpoint) throws Exception {
        request(endpoint, other, jwt.generateToken(admin)).andExpect(status().is2xxSuccessful());
    }

    @ParameterizedTest
    @EnumSource(Endpoint.class)
    void personalEndpointsRequireAuthentication(Endpoint endpoint) throws Exception {
        var before = snapshot();
        request(endpoint, other, null).andExpect(status().isUnauthorized());
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @EnumSource(value = Endpoint.class, names = {"LOG_PROGRESS", "LOG_FOOD", "LOG_WORKOUT", "SET_GOAL", "BOOK_TRAINER"})
    void bodyIdsCannotOverrideTheAuthorizedOwner(Endpoint endpoint) throws Exception {
        var request = builder(endpoint, viewer);
        request.content(body(endpoint, other));
        mvc.perform(request.header("Authorization", "Bearer " + jwt.generateToken(viewer)))
                .andExpect(status().isOk());
        String table = switch (endpoint) {
            case LOG_PROGRESS -> "progress_metrics";
            case LOG_FOOD -> "food_logs";
            case LOG_WORKOUT -> "workout_logs";
            case SET_GOAL -> "nutrition_goals";
            case BOOK_TRAINER -> "trainer_sessions";
            default -> throw new IllegalArgumentException();
        };
        String ownerColumn = endpoint == Endpoint.BOOK_TRAINER ? "member_id" : "user_id";
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + ownerColumn + " = ?",
                Long.class, viewer.getId())).isEqualTo(1L);
        if (endpoint == Endpoint.SET_GOAL) {
            assertThat(goals.findByUser(other).orElseThrow().getTargetCalories()).isEqualTo(2000);
        } else {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + ownerColumn + " = ?",
                    Long.class, other.getId())).isZero();
        }
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void disabledAccountsCannotUsePreviouslyIssuedTokensForReadsOrWrites(Role role) throws Exception {
        viewer.setRole(role);
        users.saveAndFlush(viewer);
        String existingToken = jwt.generateToken(viewer);
        viewer.setEnabled(false);
        users.saveAndFlush(viewer);
        var before = snapshot();
        request(Endpoint.PROFILE, viewer, existingToken).andExpect(status().isUnauthorized());
        request(Endpoint.LOG_PROGRESS, viewer, existingToken).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + existingToken))
                .andExpect(status().isUnauthorized());
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void disabledAdministratorTokenCannotModifyAnotherAccount() throws Exception {
        String token = jwt.generateToken(admin);
        admin.setEnabled(false);
        users.saveAndFlush(admin);
        mvc.perform(delete("/api/admin/users/" + other.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        assertThat(users.findById(other.getId()).orElseThrow().isEnabled()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void disabledAccountsCannotLogIn(Role role) throws Exception {
        viewer.setRole(role);
        viewer.setEnabled(false);
        users.saveAndFlush(viewer);
        login(viewer.getEmail(), PRIVATE_PASSWORD).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void publishedAdministratorCredentialsAreRejectedForExistingRows(boolean originalSeedHash) throws Exception {
        admin.setEmail("maya.admin@fitcore.test");
        admin.setPasswordHash(originalSeedHash
                ? "$2y$10$NaDwZvIufqUN0Thv1R/iqeQDZRLSAxZ9cFITenn8WFkNwa424QDgW"
                : encoder.encode(PUBLIC_DEMO_PASSWORD));
        users.saveAndFlush(admin);
        login(admin.getEmail(), PUBLIC_DEMO_PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void oldAdministratorTokenIsRejectedWhenAccountUsesPublishedPassword() throws Exception {
        String token = jwt.generateToken(admin);
        admin.setPasswordHash(encoder.encode(PUBLIC_DEMO_PASSWORD));
        users.saveAndFlush(admin);
        request(Endpoint.PROFILE, admin, token).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/admin/users/" + other.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        assertThat(users.findById(other.getId()).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void rotatingPublishedAdministratorPasswordRestoresPrivateLogin() throws Exception {
        admin.setEmail("maya.admin@fitcore.test");
        admin.setPasswordHash(encoder.encode(PUBLIC_DEMO_PASSWORD));
        users.saveAndFlush(admin);
        login(admin.getEmail(), PUBLIC_DEMO_PASSWORD).andExpect(status().isUnauthorized());
        admin.setPasswordHash(PRIVATE_HASH);
        users.saveAndFlush(admin);
        login(admin.getEmail(), PRIVATE_PASSWORD).andExpect(status().isOk()).andExpect(jsonPath("$.token").isString());
    }

    @Test
    void tokensIssuedWithPublicAdministratorPasswordStayInvalidAfterRotation() throws Exception {
        admin.setPasswordHash(encoder.encode(PUBLIC_DEMO_PASSWORD));
        users.saveAndFlush(admin);
        String compromisedToken = jwt.generateToken(admin);
        admin.setPasswordHash(PRIVATE_HASH);
        users.saveAndFlush(admin);
        var before = snapshot();
        request(Endpoint.PROFILE, admin, compromisedToken).andExpect(status().isUnauthorized());
        request(Endpoint.LOG_PROGRESS, admin, compromisedToken).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/admin/users/" + other.getId()).header("Authorization", "Bearer " + compromisedToken))
                .andExpect(status().isUnauthorized());
        assertThat(snapshot()).isEqualTo(before);
        request(Endpoint.PROFILE, admin, jwt.generateToken(admin)).andExpect(status().isOk());
    }

    @Test
    void legacyTokensWithoutCredentialBindingCannotReadOrWrite() throws Exception {
        String legacyToken = io.jsonwebtoken.Jwts.builder().setSubject(admin.getEmail())
                .setIssuedAt(new Date()).setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        environment.getRequiredProperty("jwt.secret").getBytes(StandardCharsets.UTF_8)),
                        io.jsonwebtoken.SignatureAlgorithm.HS256).compact();
        request(Endpoint.PROFILE, admin, legacyToken).andExpect(status().isUnauthorized());
        request(Endpoint.LOG_PROGRESS, admin, legacyToken).andExpect(status().isUnauthorized());
    }

    @Test
    void currentDatabaseRoleControlsAdministrativeAccess() throws Exception {
        String token = jwt.generateToken(admin);
        admin.setRole(Role.MEMBER);
        users.saveAndFlush(admin);
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/users/" + other.getId()).header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
    }

    static Stream<Arguments> adminEndpoints() {
        return Stream.of("GET /api/admin/dashboard", "GET /api/admin/users", "GET /api/admin/attendance",
                "PUT /api/admin/users/{user}", "DELETE /api/admin/users/{user}",
                "POST /api/admin/trainers", "PUT /api/admin/trainers/{trainer}", "DELETE /api/admin/trainers/{trainer}",
                "POST /api/admin/membership-plans", "PUT /api/admin/membership-plans/{plan}", "DELETE /api/admin/membership-plans/{plan}")
                .flatMap(operation -> Stream.of(Role.MEMBER, Role.TRAINER).map(role -> {
                    String[] parts = operation.split(" ", 2);
                    return Arguments.of(parts[0], parts[1], role);
                }));
    }

    @ParameterizedTest(name = "{2} denied {0} {1}")
    @MethodSource("adminEndpoints")
    void administrativeReadAndWriteEndpointsRejectNonAdmins(String method, String template, Role role) throws Exception {
        viewer.setRole(role);
        users.saveAndFlush(viewer);
        String path = template.replace("{user}", other.getId().toString())
                .replace("{trainer}", trainer.getId().toString()).replace("{plan}", plan.getId().toString());
        var before = snapshot();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(HttpMethod.valueOf(method), path)
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header("Authorization", "Bearer " + jwt.generateToken(viewer)))
                .andExpect(status().isForbidden());
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void privateAdministratorsCanStillReadAndUpdateUsers() throws Exception {
        String token = jwt.generateToken(admin);
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(put("/api/admin/users/" + other.getId()).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Security other","email":"other@security.test",
                                 "role":"MEMBER","fitnessGoal":"Verified admin update","enabled":true}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fitnessGoal").value("Verified admin update"));
        assertThat(users.findById(other.getId()).orElseThrow().getFitnessGoal()).isEqualTo("Verified admin update");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/memberships/plans", "/api/workouts/exercises", "/api/workouts/plans", "/api/nutrition/plans", "/api/trainers"})
    void sharedCatalogReadsRemainAvailableToMembers(String path) throws Exception {
        mvc.perform(get(path).header("Authorization", "Bearer " + jwt.generateToken(viewer)))
                .andExpect(status().isOk());
    }

    @Test
    void invalidJwtCannotReadOrWritePersonalRecords() throws Exception {
        request(Endpoint.PROFILE, viewer, "invalid-token").andExpect(status().isUnauthorized());
        request(Endpoint.LOG_PROGRESS, viewer, "invalid-token").andExpect(status().isUnauthorized());
    }

    private User user(String name, Role role) {
        return users.saveAndFlush(User.builder().gymId(com.fitcore.gym.tenancy.RegressionGym.ID).fullName("Security " + name)
                .email(name + "@security.test").passwordHash(PRIVATE_HASH).role(role).enabled(true).build());
    }

    private Attendance visit(User user) {
        return attendance.saveAndFlush(Attendance.builder().gymId(com.fitcore.gym.tenancy.RegressionGym.ID).user(user).checkInDate(LocalDate.now())
                .checkInTime(LocalDateTime.now().minusMinutes(10)).build());
    }

    private MockHttpServletRequestBuilder builder(Endpoint endpoint, User target) {
        long visit = target.getId().equals(viewer.getId()) ? viewerVisit.getId() : otherVisit.getId();
        String path = endpoint.path.replace("{user}", target.getId().toString())
                .replace("{plan}", plan.getId().toString()).replace("{trainer}", trainer.getId().toString())
                .replace("{visit}", Long.toString(visit));
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(HttpMethod.valueOf(endpoint.method), path)
                .contentType(MediaType.APPLICATION_JSON).content(body(endpoint, target));
    }

    private ResultActions request(Endpoint endpoint, User target, String token) throws Exception {
        var request = builder(endpoint, target);
        if (token != null) request.header("Authorization", "Bearer " + token);
        return mvc.perform(request);
    }

    private String body(Endpoint endpoint, User target) {
        String id = target.getId().toString();
        return switch (endpoint) {
            case LOG_PROGRESS -> "{\"userId\":\"" + id + "\",\"weight\":79.4}";
            case LOG_WORKOUT -> "{\"userId\":\"" + id + "\",\"durationMinutes\":30,\"exercises\":[]}";
            case LOG_FOOD -> "{\"userId\":\"" + id + "\",\"mealName\":\"Audit meal\",\"mealType\":\"LUNCH\",\"calories\":480}";
            case SET_GOAL -> "{\"userId\":\"" + id + "\",\"targetCalories\":2400,\"targetProtein\":150,\"targetCarbs\":280,\"targetFat\":70}";
            case BOOK_TRAINER -> """
                    {"member":{"id":"%s","fullName":"Security member","email":"member@security.test","role":"MEMBER"},
                     "trainer":{"id":%d,"specialization":"Strength"},
                     "sessionDate":"%s","startTime":"10:00:00","endTime":"11:00:00"}
                    """.formatted(id, trainer.getId(), LocalDate.now().plusDays(1));
            default -> "";
        };
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private Map<String, List<Map<String, Object>>> snapshot() {
        Map<String, List<Map<String, Object>>> data = new LinkedHashMap<>();
        for (String table : List.of("users", "active_memberships", "workout_logs", "workout_exercise_logs",
                "nutrition_goals", "food_logs", "progress_metrics", "attendance", "trainer_sessions")) {
            data.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"));
        }
        return data;
    }
    @Test void ordinaryRoleUpdateCannotAssignOwner() throws Exception {
        mvc.perform(put("/api/admin/users/"+other.getId()).header("Authorization","Bearer "+jwt.generateToken(admin))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"fullName\":\"Other\",\"email\":\"owner-injection@fixture.invalid\",\"role\":\"OWNER\",\"enabled\":true}"))
            .andExpect(status().isBadRequest());
        assertThat(users.findById(other.getId()).orElseThrow().getRole()).isEqualTo(Role.MEMBER);
    }
    @Test void ownerCannotAuthenticateWithPublishedAdministratorPassword() throws Exception {
        admin.setRole(Role.OWNER);admin.setPasswordHash(encoder.encode(PUBLIC_DEMO_PASSWORD));users.saveAndFlush(admin);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\""+admin.getEmail()+"\",\"password\":\""+PUBLIC_DEMO_PASSWORD+"\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test void newManagedPlanInheritsAuthenticatedGym() throws Exception {
        mvc.perform(post("/api/admin/membership-plans").header("Authorization","Bearer "+jwt.generateToken(admin))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Tenant plan fixture\",\"price\":29,\"durationMonths\":1,\"features\":[\"Local\"],\"recommended\":false}"))
            .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select gym_id from membership_plans where name='Tenant plan fixture'",UUID.class)).isEqualTo(com.fitcore.gym.tenancy.RegressionGym.ID);
    }
    @Test void newManagedTrainerAndProfileInheritAuthenticatedGym() throws Exception {
        mvc.perform(post("/api/admin/trainers").header("Authorization","Bearer "+jwt.generateToken(admin))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"fullName\":\"New trainer\",\"email\":\"new-trainer@fixture.invalid\",\"password\":\"Synthetic@Password26\",\"specialization\":\"Strength\",\"experienceYears\":3}"))
            .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select t.gym_id from trainer_profiles t join users u on u.id=t.user_id where u.email='new-trainer@fixture.invalid'",UUID.class)).isEqualTo(com.fitcore.gym.tenancy.RegressionGym.ID);
    }

}
