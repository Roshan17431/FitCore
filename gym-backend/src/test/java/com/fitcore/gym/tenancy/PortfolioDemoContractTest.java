package com.fitcore.gym.tenancy;

import com.fitcore.gym.config.FoundationSafetyConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.fitcore.gym.tenancy.FoundationMigrationTest.execute;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Test-only model of already verified host facts. It supplies no application startup mode. */
class PortfolioDemoContractTest {
    private static final long NOW = 1_800_000_000L;
    private static final Cell APPROVED = new Cell("portfolio-host", "demo-image", "demo-compose",
            "private-pg", "fixture-v1", "generation-1");
    private static final Fence SEALED = new Fence(true, true, true, true);

    record Cell(String host, String artifact, String config, String database, String fixture, String generation) {}
    record Permit(String scope, Cell cell, long expiresAt) {}
    record Fence(boolean disposableStore, boolean privatePorts, boolean noExternalMail, boolean noUserDataIngress) {}

    private static boolean admits(Permit permit, Cell registered, Cell observed, Fence fence) {
        return permit != null && "portfolio-only".equals(permit.scope()) && permit.expiresAt() > NOW
                && permit.expiresAt() - NOW <= 3600 && registered.equals(permit.cell())
                && registered.equals(observed) && SEALED.equals(fence);
    }

    @Test
    void approvedSyntheticCellPassesThePolicyModel() {
        assertThat(admits(new Permit("portfolio-only", APPROVED, NOW + 120), APPROVED, APPROVED, SEALED)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "commercial-scope", "expired", "unbounded", "wrong-host", "wrong-artifact",
            "override-db", "changed-config", "changed-fixture", "old-generation", "durable", "public-ports",
            "external-mail", "user-data-ingress"})
    void rejectsUnsafePortfolioAdmission(String reason) {
        Permit permit = switch (reason) {
            case "missing" -> null;
            case "commercial-scope" -> new Permit("commercial", APPROVED, NOW + 120);
            case "expired" -> new Permit("portfolio-only", APPROVED, NOW);
            case "unbounded" -> new Permit("portfolio-only", APPROVED, NOW + 3601);
            default -> new Permit("portfolio-only", APPROVED, NOW + 120);
        };
        Cell observed = new Cell(
                reason.equals("wrong-host") ? "other-host" : APPROVED.host(),
                reason.equals("wrong-artifact") ? "other-image" : APPROVED.artifact(),
                reason.equals("changed-config") ? "other-compose" : APPROVED.config(),
                reason.equals("override-db") ? "legacy-fixture" : APPROVED.database(),
                reason.equals("changed-fixture") ? "other-fixture" : APPROVED.fixture(),
                reason.equals("old-generation") ? "generation-0" : APPROVED.generation());
        Fence fence = new Fence(!reason.equals("durable"), !reason.equals("public-ports"),
                !reason.equals("external-mail"), !reason.equals("user-data-ingress"));
        assertThat(admits(permit, APPROVED, observed, fence)).isFalse();
    }

    @Test
    void portfolioMetadataDoesNotAuthorizeTheExistingRuntime() {
        new ApplicationContextRunner().withUserConfiguration(FoundationSafetyConfiguration.class)
                .withPropertyValues("fitcore.portfolio.enabled=true",
                        "spring.datasource.url=jdbc:postgresql://portfolio.invalid/fitcore_demo")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasStackTraceContaining("Foundation build requires an owned disposable database"));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "FITCORE_TENANCY_TEST_TOKEN", matches = "[a-f0-9]{64}")
    void newDisposableSchemaReproducesTwoGymsAndRejectsCrossGymBookings() throws Exception {
        assertThat(rehearseFixture()).isEqualTo(rehearseFixture())
                .containsExactly("Atlas Strength|3|1|1|1", "Harbor Movement|3|1|1|1");
    }

    private static List<String> rehearseFixture() throws Exception {
        var gyms = JsonMapper.builder().build().readTree(Files.readAllBytes(
                Path.of("..", "gym-frontend", "portfolio", "fixtures.json")));
        assertThat(gyms.size()).isEqualTo(2);
        var f = new FoundationMigrationTest();
        try {
            f.isolatedSchema();
            f.migrate("latest");
            assertThat(f.number("select count(*) from users")).isZero();
            var encoder = new BCryptPasswordEncoder();
            try (var connection = f.connection()) {
                connection.setAutoCommit(false);
                for (int index = 0; index < gyms.size(); index++) {
                    var gym = gyms.get(index);
                    int n = index + 1;
                    UUID owner = f.user(n), trainer = f.user(n + 10), member = f.user(n + 20);
                    UUID gymId = f.gym(n);
                    String slug = gym.get("id").asText();
                    execute(connection, """
                            insert into gyms(id,name,slug,status,created_at,updated_at)
                            values(?,?,?,'ACTIVE',now(),now())
                            """, gymId, gym.get("name").asText(), slug);
                    UUID[] users = {owner, trainer, member};
                    String[] roles = {"OWNER", "TRAINER", "MEMBER"};
                    String[] fields = {"owner", "trainer", "member"};
                    for (int i = 0; i < roles.length; i++) {
                        execute(connection, """
                                insert into users(id,full_name,email,password_hash,role,enabled,email_verified_at,gym_id,created_at,updated_at)
                                values(?,?,?,?,?,true,now(),?,now(),now())
                                """, users[i], gym.get(fields[i]).asText(), fields[i] + "@" + slug + ".fixture.invalid",
                                encoder.encode(UUID.randomUUID().toString()), roles[i], gymId);
                    }
                    execute(connection, "update gyms set primary_owner_user_id=? where id=?", owner, gymId);
                    int months = gym.get("months").asInt();
                    execute(connection, """
                            insert into membership_plans(id,name,price,duration_months,recommended,gym_id)
                            values(?,?,?,?,false,?)
                            """, n, gym.get("plan").asText(), gym.get("price").asInt(), months, gymId);
                    execute(connection, """
                            insert into active_memberships(user_id,plan_id,gym_id,start_date,end_date,status,created_at,updated_at)
                            values(?,?,?,current_date,(current_date + make_interval(months => ?))::date,'ACTIVE',now(),now())
                            """, member, n, gymId, months);
                    execute(connection, """
                            insert into trainer_profiles(id,user_id,specialization,experience_years,gym_id)
                            values(?,?,?,3,?)
                            """, n, trainer, gym.get("specialization").asText(), gymId);
                    LocalTime start = LocalTime.parse(gym.get("time").asText());
                    execute(connection, """
                            insert into trainer_sessions(member_id,trainer_id,gym_id,session_date,start_time,end_time,status,created_at,updated_at)
                            values(?,?,?,current_date+1,?,?,'SCHEDULED',now(),now())
                            """, member, n, gymId, start, start.plusHours(1));
                    execute(connection, """
                            insert into exercises(name,category,difficulty,gym_id)
                            values(?,?,'BEGINNER',?)
                            """, gym.get("exercise").asText(), gym.get("category").asText(), gymId);
                }
                execute(connection, "select setval(pg_get_serial_sequence('membership_plans','id'),2)");
                execute(connection, "select setval(pg_get_serial_sequence('trainer_profiles','id'),2)");
                connection.commit();
            }
            assertThatThrownBy(() -> f.sql("update trainer_sessions set trainer_id=2 where gym_id=?", f.gym(1)))
                    .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23503"));
            var rows = new ArrayList<String>();
            try (var c = f.connection(); var s = c.createStatement(); var r = s.executeQuery("""
                    select concat_ws('|',g.name,
                       (select count(*) from users u where u.gym_id=g.id),
                       (select count(*) from active_memberships m where m.gym_id=g.id),
                       (select count(*) from trainer_sessions b where b.gym_id=g.id),
                       (select count(*) from exercises x where x.gym_id=g.id))
                    from gyms g order by g.slug
                    """)) {
                while (r.next()) rows.add(r.getString(1));
            }
            return rows;
        } finally {
            if (f.schema != null) f.cleanup();
        }
    }
}
