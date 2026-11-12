package com.fitcore.gym.tenancy;

import com.fitcore.gym.operations.ReleaseReadiness;
import com.fitcore.gym.operations.SchemaContract;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Test-classpath reference contract. No Spring bean, production trust root or startup path. */
@EnabledIfEnvironmentVariable(named = "FITCORE_TENANCY_TEST_TOKEN", matches = "[a-f0-9]{64}")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class CertifiedStartupContractTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final long NOW = 1_800_000_000L;
    private static final String RUNTIME = "startup_contract_runtime";
    private static final String MIGRATOR = "startup_contract_migrator";
    private static final String RELEASE_DOMAIN = "fitcore:test:startup:v1";
    private static final String RECOVERY_DOMAIN = "fitcore:test:recovery:v1";

    // In production the target, state and observed workload are independent trusted inputs.
    // These records model their verified output, not a wire parser or a provider attestation.
    record Target(String resource, long generation, String host, int port, String database,
                  String schema, String caDigest, String runtimeRole, String migrationRole) {}
    record Workload(String principal, String boot, String artifactDigest, String configDigest) {}
    record Recovery(Target target, long epoch, String drillDigest, long expiresAt) {}
    record Grant(String id, Target target, Workload workload, long epoch, String historyDigest,
                 String schemaDigest, String recoveryDigest, long startsAt, long expiresAt) {}
    record Signed<T>(T claims, byte[] signature) {}
    record State(Target target, long epoch, String activeGrant, boolean hold, long observedAt) {}
    record Migration(int rank, String version, String type, String script, Integer checksum, boolean success) {}
    record Credential(String role, String password) {}

    private final TenantRecoveryTest source = new TenantRecoveryTest();
    private final String runtimePassword = UUID.randomUUID().toString();
    private final String migratorPassword = UUID.randomUUID().toString();
    private KeyPair releaseKey;
    private KeyPair recoveryKey;
    private Path ca;
    private Path wrongCa;
    private Target approved;
    private Workload workload;
    private String expectedHistory;
    private String expectedSchema;
    private int attempts;

    @BeforeAll
    void prepare() throws Exception {
        source.prepare();
        source.freshTarget();
        KeyPairGenerator keys = KeyPairGenerator.getInstance("Ed25519");
        releaseKey = keys.generateKeyPair();
        recoveryKey = keys.generateKeyPair();

        ca = makeCertificate("approved");
        wrongCa = makeCertificate("other");
        enableTls(source);
        String schema = source.fixture.schema;
        sql(source, "create role " + MIGRATOR + " login noinherit password '" + migratorPassword + "'",
                "create schema " + schema + " authorization " + MIGRATOR);
        approved = target(source, ca, 1);
        // Migrate using the separately named fixture role over verified TLS.
        var ds = new org.springframework.jdbc.datasource.DriverManagerDataSource(source.targetUrl);
        ds.setConnectionProperties(tlsProperties(ca, new Credential(MIGRATOR, migratorPassword)));
        var flyway = Flyway.configure().dataSource(ds).defaultSchema(schema).schemas(schema)
                .baselineOnMigrate(false).cleanDisabled(true).load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(9);
        flyway.validate();
        grantRuntime(source);
        workload = new Workload("synthetic-fitcore-backend", UUID.randomUUID().toString(),
                digest("test-artifact".getBytes()), digest("test-config".getBytes()));
        try (var c = runtime(source.targetUrl, ca, credential(), schema)) {
            assertThat(history(c)).extracting(Migration::version).containsExactly("1", "2", "4", "5", "6", "7", "8", "9", "10");
            assertThat(history(c)).allMatch(row -> row.success() && row.checksum() != null && row.type().equals("SQL"));
            expectedHistory = digest(JSON.writeValueAsBytes(history(c)));
            expectedSchema = SchemaContract.fingerprint(c);
        }
    }

    @AfterAll
    void cleanup() throws Exception {
        if (source.fixture.schema != null) source.cleanup();
    }

    @BeforeEach
    void resetCounter() {
        attempts = 0;
    }

    private Path makeCertificate(String label) throws Exception {
        Path cert = source.temp.resolve(label + ".crt");
        source.command("openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "1",
                "-subj", "/CN=fitcore-disposable-startup-" + label,
                "-addext", "subjectAltName=IP:127.0.0.1",
                "-addext", "basicConstraints=critical,CA:TRUE",
                "-keyout", source.temp.resolve(label + ".key").toString(), "-out", cert.toString());
        return cert;
    }

    private void enableTls(TenantRecoveryTest db) throws Exception {
        // Recheck owned loopback target and live marker before copying temporary fixture keys.
        try (var ignored = db.restoredConnection()) {}
        db.command("docker", "cp", ca.toString(), db.target + ":/tmp/fitcore-startup.crt");
        db.command("docker", "cp", source.temp.resolve("approved.key").toString(),
                db.target + ":/tmp/fitcore-startup.key");
        db.command("docker", "exec", db.target, "chown", "postgres:postgres",
                "/tmp/fitcore-startup.crt", "/tmp/fitcore-startup.key");
        db.command("docker", "exec", db.target, "chmod", "600", "/tmp/fitcore-startup.key");
        sql(db, "alter system set ssl_cert_file='/tmp/fitcore-startup.crt'",
                "alter system set ssl_key_file='/tmp/fitcore-startup.key'", "alter system set ssl='on'",
                "select pg_reload_conf()");
        for (int i = 0; i < 30; i++) {
            try (var c = db.restoredConnection(); var st = c.createStatement();
                 var row = st.executeQuery("show ssl")) {
                if (row.next() && "on".equals(row.getString(1))) return;
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("Disposable PostgreSQL TLS failed to load");
    }

    private void grantRuntime(TenantRecoveryTest db) throws Exception {
        String schema = db.fixture.schema;
        sql(db, "create role " + RUNTIME + " login noinherit password '" + runtimePassword + "'",
                "grant usage on schema " + schema + " to " + RUNTIME,
                "grant select,insert,update,delete on all tables in schema " + schema + " to " + RUNTIME,
                "revoke all on flyway_schema_history from " + RUNTIME,
                "grant select on flyway_schema_history to " + RUNTIME,
                "grant usage,select on all sequences in schema " + schema + " to " + RUNTIME,
                "revoke all on function fitcore_scope_catalogs() from public");
    }

    private static void sql(TenantRecoveryTest db, String... statements) throws Exception {
        try (var c = db.restoredConnection(); var s = c.createStatement()) {
            for (String statement : statements) s.execute(statement);
        }
    }

    private static Target target(TenantRecoveryTest db, Path root, long generation) throws Exception {
        URI uri = URI.create(db.targetUrl.substring("jdbc:".length()));
        return new Target(db.target, generation, uri.getHost(), uri.getPort(), "fitcore_phase2",
                db.fixture.schema, digest(Files.readAllBytes(root)), RUNTIME, MIGRATOR);
    }

    private Credential credential() {
        return new Credential(RUNTIME, runtimePassword);
    }

    private static Properties tlsProperties(Path root, Credential credential) {
        var p = new Properties();
        p.setProperty("user", credential.role());
        p.setProperty("password", credential.password());
        p.setProperty("sslmode", "verify-full");
        p.setProperty("sslrootcert", root.toString());
        p.setProperty("sslcert", "");
        p.setProperty("sslkey", "");
        p.setProperty("gssEncMode", "disable");
        p.setProperty("connectTimeout", "3");
        p.setProperty("socketTimeout", "10");
        return p;
    }

    private static Connection runtime(String url, Path root, Credential credential, String schema) throws SQLException {
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/fitcore_phase2")) {
            throw new IllegalArgumentException("Only harness loopback URLs may be dialed");
        }
        var c = DriverManager.getConnection(url, tlsProperties(root, credential));
        try {
            c.setReadOnly(true);
            c.setSchema(schema);
            return c;
        } catch (SQLException e) {
            c.close();
            throw e;
        }
    }

    private static List<Migration> history(Connection c) throws SQLException {
        var result = new ArrayList<Migration>();
        try (var s = c.createStatement()) {
            s.setQueryTimeout(2);
            try (var rows = s.executeQuery("""
                    select installed_rank,version,type,script,checksum,success
                    from flyway_schema_history order by installed_rank
                    """)) {
                while (rows.next()) {
                    result.add(new Migration(rows.getInt(1), rows.getString(2), rows.getString(3),
                            rows.getString(4), rows.getObject(5, Integer.class), rows.getBoolean(6)));
                }
            }
        }
        return result;
    }

    private static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static byte[] payload(String domain, Object claims) throws Exception {
        return JSON.writeValueAsBytes(List.of(domain, claims));
    }

    private static <T> Signed<T> sign(String domain, T claims, KeyPair keys) throws Exception {
        var signer = Signature.getInstance("Ed25519");
        signer.initSign(keys.getPrivate());
        signer.update(payload(domain, claims));
        return new Signed<>(claims, signer.sign());
    }

    private static boolean valid(String domain, Signed<?> signed, PublicKey key) throws Exception {
        var verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(key);
        verifier.update(payload(domain, signed.claims()));
        return verifier.verify(signed.signature());
    }

    private Signed<Recovery> recovery(Target target, long epoch) throws Exception {
        return sign(RECOVERY_DOMAIN, new Recovery(target, epoch, digest("synthetic-drill".getBytes()), NOW + 120), recoveryKey);
    }

    private Signed<Grant> grant(Target target, Signed<Recovery> recovery, long from, long until) throws Exception {
        return sign(RELEASE_DOMAIN, new Grant("test-grant", target, workload, 7, expectedHistory,
                expectedSchema, digest(payload(RECOVERY_DOMAIN, recovery.claims())), from, until), releaseKey);
    }

    private State state(Target target) {
        return new State(target, 7, "test-grant", false, NOW);
    }

    private static final class Rejected extends Exception {
        Rejected(String stage) {
            super(stage);
        }
    }

    private void verify(Signed<Grant> signedGrant, Signed<Recovery> signedRecovery, State live,
                        Target registered, Target requested, Workload observed, Path root, Credential credential) throws Exception {
        Grant grant = signedGrant.claims();
        Recovery recovery = signedRecovery.claims();
        if (!valid(RELEASE_DOMAIN, signedGrant, releaseKey.getPublic())
                || grant.startsAt() > NOW || grant.expiresAt() <= NOW || grant.expiresAt() - grant.startsAt() > 120
                || live == null || live.hold() || live.observedAt() != NOW
                || !grant.id().equals(live.activeGrant()) || grant.epoch() != live.epoch()
                || !requested.equals(registered) || !grant.target().equals(live.target())
                || !grant.target().equals(requested)
                || !grant.workload().equals(observed)) {
            throw new Rejected("admission");
        }
        if (!valid(RECOVERY_DOMAIN, signedRecovery, recoveryKey.getPublic())
                || recovery.expiresAt() <= NOW || !recovery.target().equals(requested)
                || recovery.epoch() != live.epoch()
                || !grant.recoveryDigest().equals(digest(payload(RECOVERY_DOMAIN, recovery)))) {
            throw new Rejected("recovery");
        }
        if (!requested.caDigest().equals(digest(Files.readAllBytes(root)))
                || !credential.role().equals(requested.runtimeRole())
                || requested.runtimeRole().equals(requested.migrationRole())) {
            throw new Rejected("connection policy");
        }

        String url = "jdbc:postgresql://" + requested.host() + ":" + requested.port() + "/" + requested.database();
        attempts++;
        try (var c = runtime(url, root, credential, requested.schema())) {
            try (var s = c.createStatement()) {
                s.setQueryTimeout(2);
                try (var row = s.executeQuery("""
                        select current_database(),current_schema(),session_user,current_user,
                          (select ssl from pg_stat_ssl where pid=pg_backend_pid()),
                          not (rolinherit or rolreplication),
                          not exists(select from pg_auth_members where member=r.oid)
                        from pg_roles r where rolname=current_user
                        """)) {
                    if (!row.next() || !requested.database().equals(row.getString(1))
                            || !requested.schema().equals(row.getString(2))
                            || !requested.runtimeRole().equals(row.getString(3))
                            || !requested.runtimeRole().equals(row.getString(4))
                            || !row.getBoolean(5) || !row.getBoolean(6) || !row.getBoolean(7)) {
                        throw new Rejected("live identity or role");
                    }
                }
            }
            if (!ReleaseReadiness.schemaReady(c) || !grant.schemaDigest().equals(SchemaContract.fingerprint(c))) {
                throw new Rejected("schema or privileges");
            }
            if (!grant.historyDigest().equals(digest(JSON.writeValueAsBytes(history(c))))) {
                throw new Rejected("migration history");
            }
        }
    }

    private void verify(Signed<Grant> grant, Signed<Recovery> recovery, State live, Target target) throws Exception {
        verify(grant, recovery, live, approved, target, workload, ca, credential());
    }

    @Test
    void validSyntheticApprovalPassesOverVerifiedPostgresTls() throws Exception {
        var proof = recovery(approved, 7);
        verify(grant(approved, proof, NOW - 1, NOW + 60), proof, state(approved), approved);
        assertThat(attempts).isOne();
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "legacy", "host", "port", "schema", "database"})
    void rejectsUnknownOrMismatchedTargetBeforeDialing(String field) throws Exception {
        var t = approved;
        var other = new Target(field.equals("unknown") ? "unknown-resource" : field.equals("legacy") ? "legacy-fixture" : t.resource(),
                t.generation(), field.equals("host") ? "other.invalid" : t.host(),
                field.equals("port") ? (t.port() == 1 ? 2 : 1) : t.port(),
                field.equals("database") ? "other_fixture" : t.database(),
                field.equals("schema") ? "other_fixture" : t.schema(), t.caDigest(), RUNTIME, MIGRATOR);
        var proof = recovery(other, 7);
        assertThatThrownBy(() -> verify(grant(other, proof, NOW - 1, NOW + 60), proof, state(other), other))
                .isInstanceOf(Rejected.class).hasMessage("admission");
        assertThat(attempts).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"forged", "expired", "future", "revoked", "unavailable", "stale", "hold", "epoch", "boot", "artifact", "config", "principal", "live-generation"})
    void rejectsInvalidAuthorizationBeforeDialing(String reason) throws Exception {
        var proof = recovery(approved, 7);
        var signed = grant(approved, proof, reason.equals("future") ? NOW + 1 : NOW - 1,
                reason.equals("expired") ? NOW : NOW + 60);
        if (reason.equals("forged")) signed = sign(RELEASE_DOMAIN, signed.claims(), recoveryKey);
        var candidate = signed;
        Target liveTarget = reason.equals("live-generation") ? nextGeneration(approved) : approved;
        var live = reason.equals("unavailable") ? null : new State(liveTarget, reason.equals("epoch") ? 8 : 7,
                reason.equals("revoked") ? "revoked-grant" : "test-grant", reason.equals("hold"),
                reason.equals("stale") ? NOW - 1 : NOW);
        var observed = new Workload(reason.equals("principal") ? "other-workload" : workload.principal(),
                reason.equals("boot") ? "other-boot" : workload.boot(),
                reason.equals("artifact") ? "other-artifact" : workload.artifactDigest(),
                reason.equals("config") ? "other-config" : workload.configDigest());
        assertThatThrownBy(() -> verify(candidate, proof, live, approved, approved, observed, ca, credential()))
                .isInstanceOf(Rejected.class).hasMessage("admission");
        assertThat(attempts).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong-signer", "expired", "wrong-epoch", "different-evidence"})
    void rejectsInvalidIndependentRecoveryProofBeforeDialing(String reason) throws Exception {
        var expected = recovery(approved, 7);
        var claims = new Recovery(approved, reason.equals("wrong-epoch") ? 6 : 7,
                reason.equals("different-evidence") ? digest("other-drill".getBytes()) : expected.claims().drillDigest(),
                reason.equals("expired") ? NOW : NOW + 120);
        var proof = sign(RECOVERY_DOMAIN, claims, reason.equals("wrong-signer") ? releaseKey : recoveryKey);
        var signed = grant(approved, reason.equals("different-evidence") ? expected : proof, NOW - 1, NOW + 60);
        assertThatThrownBy(() -> verify(signed, proof, state(approved), approved))
                .isInstanceOf(Rejected.class).hasMessage("recovery");
        assertThat(attempts).isZero();
    }

    @Test
    void wrongRootAndMigratorCredentialAreRejectedBeforeDialing() throws Exception {
        var proof = recovery(approved, 7);
        var signed = grant(approved, proof, NOW - 1, NOW + 60);
        assertThatThrownBy(() -> verify(signed, proof, state(approved), approved, approved, workload, wrongCa, credential()))
                .isInstanceOf(Rejected.class).hasMessage("connection policy");
        assertThatThrownBy(() -> verify(signed, proof, state(approved), approved, approved, workload, ca,
                new Credential(MIGRATOR, migratorPassword))).isInstanceOf(Rejected.class).hasMessage("connection policy");
        assertThat(attempts).isZero();
    }

    @Test
    void verifiesRealTlsTrustAndPassword() throws Exception {
        var proof = recovery(approved, 7);
        var signed = grant(approved, proof, NOW - 1, NOW + 60);
        assertThatThrownBy(() -> verify(signed, proof, state(approved), approved, approved, workload, ca,
                new Credential(RUNTIME, "synthetic-wrong-password"))).isInstanceOfSatisfying(
                SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("28P01"));

        var mismatchedTrust = target(source, wrongCa, approved.generation());
        var wrongProof = recovery(mismatchedTrust, 7);
        assertThatThrownBy(() -> verify(grant(mismatchedTrust, wrongProof, NOW - 1, NOW + 60), wrongProof,
                state(mismatchedTrust), mismatchedTrust, mismatchedTrust, workload, wrongCa, credential()))
                .isInstanceOf(SQLException.class).hasCauseInstanceOf(javax.net.ssl.SSLHandshakeException.class);
        assertThat(attempts).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"history", "schema", "create", "membership"})
    void rejectsDriftInLivePostgres(String drift) throws Exception {
        String schema = approved.schema();
        String change;
        String undo;
        String expected = "schema or privileges";
        switch (drift) {
            case "history" -> {
                int checksum;
                try (var c = source.restoredConnection()) { checksum = history(c).get(0).checksum(); }
                change = "update flyway_schema_history set checksum=checksum # 1 where version='1'";
                undo = "update flyway_schema_history set checksum=" + checksum + " where version='1'";
                expected = "migration history";
            }
            case "schema" -> {
                change = "alter table users add column unauthorized_fixture text";
                undo = "alter table users drop column unauthorized_fixture";
            }
            case "create" -> {
                change = "grant create on schema " + schema + " to " + RUNTIME;
                undo = "revoke create on schema " + schema + " from " + RUNTIME;
            }
            case "membership" -> {
                change = "grant " + MIGRATOR + " to " + RUNTIME;
                undo = "revoke " + MIGRATOR + " from " + RUNTIME;
                expected = "live identity or role";
            }
            default -> throw new IllegalArgumentException(drift);
        }
        var proof = recovery(approved, 7);
        var signed = grant(approved, proof, NOW - 1, NOW + 60);
        sql(source, change);
        try {
            assertThatThrownBy(() -> verify(signed, proof, state(approved), approved))
                    .isInstanceOf(Rejected.class).hasMessage(expected);
        } finally {
            sql(source, undo);
        }
        verify(signed, proof, state(approved), approved);
        assertThat(attempts).isEqualTo(2);
    }

    private static Target nextGeneration(Target t) {
        return new Target(t.resource(), t.generation() + 1, t.host(), t.port(), t.database(),
                t.schema(), t.caDigest(), t.runtimeRole(), t.migrationRole());
    }

    @Test
    void oldGrantFailsWhenOnlyRegisteredResourceGenerationChanges() throws Exception {
        Target t = approved;
        var next = nextGeneration(t);
        var proof = recovery(t, 7);
        var signed = grant(t, proof, NOW - 1, NOW + 60);
        assertThatThrownBy(() -> verify(signed, proof, state(next), next, next, workload, ca, credential()))
                .isInstanceOf(Rejected.class).hasMessage("admission");
        assertThat(attempts).isZero();
    }

    @Test
    void realArchiveRestoreCanPassSchemaReadinessButCannotReuseReleaseApproval() throws Exception {
        var clone = new TenantRecoveryTest();
        try {
            clone.prepare();
            clone.freshTarget();
            Path archive = source.temp.resolve("certified-startup.dump");
            var dump = new ProcessBuilder("docker", "exec", source.target, "pg_dump", "-U", TenantRecoveryTest.USER,
                    "-d", "fitcore_phase2", "--format=custom", "--schema=" + approved.schema(),
                    "--no-owner", "--no-privileges").redirectOutput(archive.toFile())
                    .redirectError(source.temp.resolve("certified-startup-dump.err").toFile()).start();
            if (!dump.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)) {
                dump.destroyForcibly();
                throw new IllegalStateException("Owned startup fixture archive timed out");
            }
            assertThat(dump.exitValue()).isZero();
            clone.restore(archive);
            String originalSchema = clone.fixture.schema;
            clone.fixture.schema = approved.schema();
            try {
                enableTls(clone);
                grantRuntime(clone);
                Target copied = target(clone, ca, 2);
                try (var c = runtime(clone.targetUrl, ca, credential(), copied.schema())) {
                    assertThat(ReleaseReadiness.schemaReady(c)).isTrue();
                    assertThat(digest(JSON.writeValueAsBytes(history(c)))).isEqualTo(expectedHistory);
                }
                var proof = recovery(approved, 7);
                var signed = grant(approved, proof, NOW - 1, NOW + 60);
                assertThatThrownBy(() -> verify(signed, proof, state(copied), copied, copied, workload, ca, credential()))
                        .isInstanceOf(Rejected.class).hasMessage("admission");
                // A restore under the original name and endpoint still needs a new external epoch.
                assertThatThrownBy(() -> verify(signed, proof, new State(approved, 8, "test-grant", false, NOW), approved))
                        .isInstanceOf(Rejected.class).hasMessage("admission");
                assertThat(attempts).isZero();
            } finally {
                clone.fixture.schema = originalSchema;
            }
        } finally {
            if (clone.fixture.schema != null) clone.cleanup();
        }
    }
}
