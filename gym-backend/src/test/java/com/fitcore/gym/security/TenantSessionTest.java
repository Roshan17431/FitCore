package com.fitcore.gym.security;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fitcore.gym.domain.Role;
import com.fitcore.gym.entity.*;
import com.fitcore.gym.fixture.*;
import com.fitcore.gym.repository.*;
import com.fitcore.gym.tenancy.RegressionGym;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.*;

@org.junit.jupiter.api.extension.ExtendWith(RegressionGym.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TenantSessionTest {
    @Autowired jakarta.persistence.EntityManager em;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired FixtureUserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    User member;

    @BeforeEach
    void fixture() {
        member =
                users.saveAndFlush(
                        User.builder()
                                .gymId(RegressionGym.ID)
                                .fullName("Tenant session")
                                .email("session@fixture.invalid")
                                .passwordHash(encoder.encode("SessionOnly!2026"))
                                .role(Role.MEMBER)
                                .enabled(true)
                                .build());
    }

    String raw(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(
                        Keys.hmacShaKeyFor(
                                "fitcore-isolated-security-tests-only-secret-at-least-32-characters"
                                        .getBytes(StandardCharsets.UTF_8)),
                        SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    void issuedTokenUsesStableUuidAndRequiredClaims() {
        String token = jwt.generateToken(member);
        assertThat(jwt.extractUsername(token)).isEqualTo(member.getId().toString());
        assertThat((Integer) jwt.extractClaim(token, c -> c.get("tokenVersion", Integer.class)))
                .isEqualTo(2);
        assertThat((String) jwt.extractClaim(token, c -> c.get("gymId", String.class)))
                .isEqualTo(RegressionGym.ID.toString());
        assertThat(
                        (Long)
                                jwt.extractClaim(
                                        token, c -> ((Number) c.get("authzVersion")).longValue()))
                .isZero();
    }

    @Test
    void meRequiresValidatedSession() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(
                        get("/api/auth/me")
                                .header("Authorization", "Bearer " + jwt.generateToken(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("id").value(member.getId().toString()))
                .andExpect(jsonPath("gymId").value(RegressionGym.ID.toString()));
    }

    @Test
    void legacyEmailTokenRejected() throws Exception {
        mvc.perform(
                        get("/api/users/" + member.getId())
                                .header(
                                        "Authorization",
                                        "Bearer " + raw(Map.of(), member.getEmail())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void versionChangeRejectsOldSession() throws Exception {
        String old = jwt.generateToken(member);
        jdbc.update("update users set authz_version=authz_version+1 where id=?", member.getId());
        em.clear();
        mvc.perform(get("/api/users/" + member.getId()).header("Authorization", "Bearer " + old))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void suspendedGymCannotUseOrReceiveToken() throws Exception {
        String old = jwt.generateToken(member);
        jdbc.update("update gyms set status='SUSPENDED' where id=?", RegressionGym.ID);
        em.clear();
        mvc.perform(get("/api/users/" + member.getId()).header("Authorization", "Bearer " + old))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/api/auth/login")
                                .contentType("application/json")
                                .content(
                                        "{\"email\":\"session@fixture.invalid\",\"password\":\"SessionOnly!2026\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unassignedCredentialsIssueNoToken() throws Exception {
        jdbc.update("update users set gym_id=null where id=?", member.getId());
        em.clear();
        mvc.perform(
                        post("/api/auth/login")
                                .contentType("application/json")
                                .content(
                                        "{\"email\":\"session@fixture.invalid\",\"password\":\"SessionOnly!2026\"}"))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"gymId", "authzVersion", "tokenVersion", "credentialsVersion"})
    void missingClaimsRejected(String key) throws Exception {
        var claims =
                jwt.extractClaim(jwt.generateToken(member), c -> new HashMap<String, Object>(c));
        claims.remove(key);
        mvc.perform(
                        get("/api/users/" + member.getId())
                                .header(
                                        "Authorization",
                                        "Bearer " + raw(claims, member.getId().toString())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clientGymSelectorIsRejected() throws Exception {
        mvc.perform(
                        get("/api/memberships/plans")
                                .header("Authorization", "Bearer " + jwt.generateToken(member))
                                .header("X-Gym-ID", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clientOwnershipFieldsAreRejected() throws Exception {
        member.setRole(Role.ADMIN);
        users.saveAndFlush(member);
        mvc.perform(
                        post("/api/admin/membership-plans")
                                .header("Authorization", "Bearer " + jwt.generateToken(member))
                                .contentType("application/json")
                                .content(
                                        "{\"name\":\"Scoped\",\"price\":20,\"durationMonths\":1,\"gymId\":\""
                                                + RegressionGym.ID
                                                + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tamperedSignatureIsRejected() throws Exception {
        String signed = jwt.generateToken(member);
        int dot = signed.lastIndexOf('.');
        String invalid =
                signed.substring(0, dot + 1)
                        + (signed.charAt(dot + 1) == 'a' ? 'b' : 'a')
                        + signed.substring(dot + 2);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + invalid))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void foreignGymAndForgedRoleClaimsDoNotGrantAccess() throws Exception {
        var claims =
                jwt.extractClaim(jwt.generateToken(member), c -> new HashMap<String, Object>(c));
        claims.put("gymId", UUID.randomUUID().toString());
        mvc.perform(
                        get("/api/auth/me")
                                .header(
                                        "Authorization",
                                        "Bearer " + raw(claims, member.getId().toString())))
                .andExpect(status().isUnauthorized());
        claims.put("gymId", RegressionGym.ID.toString());
        claims.put("role", "OWNER");
        mvc.perform(
                        get("/api/admin/users")
                                .header(
                                        "Authorization",
                                        "Bearer " + raw(claims, member.getId().toString())))
                .andExpect(status().isForbidden());
    }

    @Test
    void expiredOrMissingExpiryCannotAuthenticate() throws Exception {
        var claims =
                jwt.extractClaim(jwt.generateToken(member), c -> new HashMap<String, Object>(c));
        String token =
                Jwts.builder()
                        .setClaims(claims)
                        .setExpiration(new Date(0))
                        .signWith(
                                Keys.hmacShaKeyFor(
                                        "fitcore-isolated-security-tests-only-secret-at-least-32-characters"
                                                .getBytes(StandardCharsets.UTF_8)),
                                SignatureAlgorithm.HS256)
                        .compact();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        claims.remove("exp");
        token =
                Jwts.builder()
                        .setClaims(claims)
                        .signWith(
                                Keys.hmacShaKeyFor(
                                        "fitcore-isolated-security-tests-only-secret-at-least-32-characters"
                                                .getBytes(StandardCharsets.UTF_8)),
                                SignatureAlgorithm.HS256)
                        .compact();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "empty", "invalid"})
    void malformedSubjectAlwaysReceivesUnauthorized(String shape) throws Exception {
        var claims =
                jwt.extractClaim(jwt.generateToken(member), c -> new HashMap<String, Object>(c));
        String subject =
                switch (shape) {
                    case "missing" -> null;
                    case "empty" -> "";
                    default -> "not-a-uuid";
                };
        var before = jdbc.queryForList("select * from users order by id");
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + raw(claims, subject)))
                .andExpect(status().isUnauthorized());
        assertThat(
                        org.springframework.security.core.context.SecurityContextHolder.getContext()
                                .getAuthentication())
                .isNull();
        assertThat(jdbc.queryForList("select * from users order by id")).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "application/vnd.fitcore+json",
                "application/problem+json",
                "application/json"
            })
    void allSupportedJsonMediaRejectRootAndNestedOwnership(String media) throws Exception {
        member.setRole(Role.ADMIN);
        users.saveAndFlush(member);
        for (String override :
                java.util.List.of(
                        "\"gymId\":\"" + RegressionGym.ID + "\"",
                        "\"metadata\":{\"authzVersion\":0}")) {
            long before = jdbc.queryForObject("select count(*) from membership_plans", Long.class);
            mvc.perform(
                            post("/api/admin/membership-plans")
                                    .header("Authorization", "Bearer " + jwt.generateToken(member))
                                    .contentType(media)
                                    .content(
                                            "{\"name\":\"Vendor"
                                                + " selector\",\"price\":20,\"durationMonths\":1,"
                                                    + override
                                                    + "}"))
                    .andExpect(status().isBadRequest());
            assertThat(jdbc.queryForObject("select count(*) from membership_plans", Long.class))
                    .isEqualTo(before);
        }
    }

    @Test
    void nullAndIncorrectlyTypedSubjectsAreRejectedWithoutStateChanges() throws Exception {
        for (Object subject : java.util.Arrays.asList(null, 7, List.of("not-an-identity"))) {
            var claims =
                    jwt.extractClaim(
                            jwt.generateToken(member), c -> new HashMap<String, Object>(c));
            claims.put("sub", subject);
            var base64 = Base64.getUrlEncoder().withoutPadding();
            String unsigned =
                    base64.encodeToString("{\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8))
                            + "."
                            + base64.encodeToString(
                                    tools.jackson.databind.json.JsonMapper.builder()
                                            .build()
                                            .writeValueAsBytes(claims));
            var mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(
                    new javax.crypto.spec.SecretKeySpec(
                            "fitcore-isolated-security-tests-only-secret-at-least-32-characters"
                                    .getBytes(StandardCharsets.UTF_8),
                            "HmacSHA256"));
            String signed =
                    unsigned
                            + "."
                            + base64.encodeToString(
                                    mac.doFinal(unsigned.getBytes(StandardCharsets.US_ASCII)));
            var before = jdbc.queryForList("select * from users order by id");
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + signed))
                    .andExpect(status().isUnauthorized());
            assertThat(
                            org.springframework.security.core.context.SecurityContextHolder
                                    .getContext()
                                    .getAuthentication())
                    .isNull();
            assertThat(jdbc.queryForList("select * from users order by id")).isEqualTo(before);
        }
    }

    @Test
    void ownerSignupRemainsDisabledInOrdinaryConfiguration() throws Exception {
        mvc.perform(
                        post("/api/auth/register-owner")
                                .contentType("application/json")
                                .content(
                                        "{\"fullName\":\"Demo"
                                            + " Owner\",\"email\":\"owner@fixture.invalid\",\"password\":\"StrongPass!2026\",\"gymName\":\"Demo"
                                            + " Gym\",\"slug\":\"demo-gym\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void publicSignupRateLimitIsEnforcedOverHttp() throws Exception {
        for (int n = 0; n < 51; n++)
            mvc.perform(
                            post("/api/auth/register-owner")
                                    .with(
                                            r -> {
                                                r.setRemoteAddr("198.51.100.99");
                                                return r;
                                            })
                                    .contentType("application/json")
                                    .content("{}"))
                    .andExpect(status().is(n < 50 ? 400 : 429));
    }
    @ParameterizedTest
    @ValueSource(strings={"missing", "future", "after-expiry"})
    void issuedAtIsRequiredAndBounded(String shape) throws Exception {
        var claims = jwt.extractClaim(jwt.generateToken(member), c -> new HashMap<String,Object>(c));
        var builder = Jwts.builder().setClaims(claims);
        if (shape.equals("missing")) builder.setIssuedAt(null);
        else if (shape.equals("future")) builder.setIssuedAt(new Date(System.currentTimeMillis()+120_000));
        else builder.setIssuedAt(new Date(System.currentTimeMillis()+120_000)).setExpiration(new Date(System.currentTimeMillis()+60_000));
        String token = builder.signWith(Keys.hmacShaKeyFor("fitcore-isolated-security-tests-only-secret-at-least-32-characters".getBytes(StandardCharsets.UTF_8)),SignatureAlgorithm.HS256).compact();
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
    }

    @Test void smallIssueClockSkewRemainsAccepted() throws Exception {
        var claims=jwt.extractClaim(jwt.generateToken(member),c->new HashMap<String,Object>(c));
        String token=Jwts.builder().setClaims(claims).setIssuedAt(new Date(System.currentTimeMillis()+10_000)).signWith(Keys.hmacShaKeyFor("fitcore-isolated-security-tests-only-secret-at-least-32-characters".getBytes(StandardCharsets.UTF_8)),SignatureAlgorithm.HS256).compact();
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+token)).andExpect(status().isOk());
    }

    @Test void readinessFailsClosedWithoutExposingDatabaseDetails() throws Exception {
        mvc.perform(get("/api/health/ready")).andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("status").value("NOT_READY"))
            .andExpect(jsonPath("database").doesNotExist());
        mvc.perform(get("/api/health")).andExpect(status().isOk());
        mvc.perform(get("/api/health/ready").param("gymId",RegressionGym.ID.toString())).andExpect(status().isBadRequest());
    }

    @ParameterizedTest @ValueSource(strings={"null","text","fraction","negative"})
    void malformedIssuedAtCannotEstablishSession(String shape) throws Exception {
        var claims=jwt.extractClaim(jwt.generateToken(member),c->new HashMap<String,Object>(c));
        claims.put("iat",switch(shape){case "null"->null;case "text"->"not-a-date";case "fraction"->1.5;default->-1;});
        String token=Jwts.builder().setClaims(claims).signWith(Keys.hmacShaKeyFor("fitcore-isolated-security-tests-only-secret-at-least-32-characters".getBytes(StandardCharsets.UTF_8)),SignatureAlgorithm.HS256).compact();
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
    }

}
