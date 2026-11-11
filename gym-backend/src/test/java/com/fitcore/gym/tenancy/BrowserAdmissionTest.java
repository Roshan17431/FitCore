package com.fitcore.gym.tenancy;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import java.util.*;
import java.security.*;
import java.time.Instant;
import io.jsonwebtoken.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
@EnabledIfEnvironmentVariable(named="FITCORE_TENANCY_TEST_TOKEN", matches="[a-f0-9]{64}")
@SpringBootTest @AutoConfigureMockMvc
class BrowserAdmissionTest {
    static final String SCHEMA="browser_admission_"+UUID.randomUUID().toString().replace("-","");
    static final String RUNTIME="browser_runtime_"+UUID.randomUUID().toString().replace("-","");
    static final String ORIGIN="https://127.0.0.1:18444";
    static final KeyPair KEY=key();
    static KeyPair key(){try{var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);return generator.generateKeyPair();}catch(Exception e){throw new IllegalStateException(e);}}
    static final String PUBLIC_KEY="-----BEGIN PUBLIC KEY-----\n"+Base64.getMimeEncoder(64,new byte[]{10}).encodeToString(KEY.getPublic().getEncoded())+"\n-----END PUBLIC KEY-----";
    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry p) throws Exception {
        configure(p,SCHEMA,RUNTIME);
    }
    static void configure(DynamicPropertyRegistry p,String SCHEMA,String RUNTIME) throws Exception {
        p.add("fitcore.browser.spike-enabled",()->"true");
        p.add("fitcore.browser.public-origin",()->ORIGIN);
        p.add("fitcore.browser.gateway-public-key",()->PUBLIC_KEY);
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
    static String hash(String s)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
    static String proof(String method,String target,String body)throws Exception{
        Instant now=Instant.now();
        return Jwts.builder().setIssuer("fitcore-local-bff").setAudience("fitcore-browser-spike").setIssuedAt(Date.from(now)).setExpiration(Date.from(now.plusSeconds(20))).setId(UUID.randomUUID().toString()).claim("method",method).claim("target",target).claim("bodySha256",hash(body)).claim("cookieSha256",hash("")).claim("clientIp","127.0.0.1").signWith(KEY.getPrivate(),SignatureAlgorithm.RS256).compact();
    }
    @Test void signedExactOriginBootstrapCreatesOnlySecureHttpOnlyHostCookie()throws Exception{
        var r=mvc.perform(post("/api/browser/bootstrap").secure(true).contentType("application/json").content("{}").header("Origin",ORIGIN).header("Sec-Fetch-Site","same-origin").header("X-FitCore-Bootstrap","1").header("X-FitCore-Gateway",proof("POST","/api/browser/bootstrap","{}"))).andReturn().getResponse();
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        assertThat(r.getHeader("Set-Cookie")).startsWith("__Host-fitcore_preauth=").contains("Secure","HttpOnly","Path=/","SameSite=Lax").doesNotContain("Domain=");
        assertThat(r.getContentAsString()).contains("csrfToken").doesNotContain("jwt","refreshToken","accessToken");
    }
    @Test void missingGatewayCannotUseLegacyBearerOrPublicLogin()throws Exception{
        var r=mvc.perform(post("/api/auth/login").contentType("application/json").content("{}").header("Authorization","Bearer ignored")).andReturn().getResponse();
        assertThat(r.getStatus()).isEqualTo(401);assertThat(r.getHeader("Set-Cookie")).isNull();
    }
}
