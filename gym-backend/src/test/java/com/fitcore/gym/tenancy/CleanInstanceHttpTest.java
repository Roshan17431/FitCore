package com.fitcore.gym.tenancy;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fitcore.gym.email.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/** No seeds, legacy input or direct account inserts: real verified onboarding on empty V8. */
@EnabledIfEnvironmentVariable(named="FITCORE_TENANCY_TEST_TOKEN",matches="[a-f0-9]{64}")
@SpringBootTest(properties={"fitcore.security-flows.demo-enabled=true","fitcore.email.outbox.enabled=true","fitcore.email.outbox.worker-enabled=true","fitcore.email.outbox.active-key=synthetic","fitcore.email.outbox.keys=synthetic=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@Import(CleanInstanceHttpTest.Config.class)
class CleanInstanceHttpTest {
    static final FoundationMigrationTest fixture=new FoundationMigrationTest();
    static final String PASSWORD="Clean-Only!2026", RUNTIME="clean_instance_http";
    static final JsonMapper JSON=JsonMapper.builder().build();
    @DynamicPropertySource static void database(DynamicPropertyRegistry p)throws Exception {
        fixture.isolatedSchema();
        try(var c=fixture.connection();var s=c.createStatement();var r=s.executeQuery("select count(*) from information_schema.tables where table_schema=current_schema()")){r.next();assertThat(r.getInt(1)).isZero();}
        fixture.migrate("9");
        // App runs as a non-owner identity, not the fixture migrator.
        try(var c=fixture.connection();var s=c.createStatement()){
            s.execute("create role "+RUNTIME+" login password 'synthetic-runtime-only' nosuperuser nocreatedb nocreaterole nobypassrls noinherit");
            s.execute("grant usage on schema "+fixture.schema+" to "+RUNTIME);
            s.execute("grant select,insert,update,delete on all tables in schema "+fixture.schema+" to "+RUNTIME);
            s.execute("revoke all on "+fixture.schema+".flyway_schema_history from "+RUNTIME);
            s.execute("grant select on "+fixture.schema+".flyway_schema_history to "+RUNTIME);
            s.execute("grant usage,select on all sequences in schema "+fixture.schema+" to "+RUNTIME);
        }
        p.add("spring.datasource.url",()->System.getenv("FITCORE_POSTGRES_TEST_URL"));
        p.add("spring.datasource.username",()->RUNTIME);p.add("spring.datasource.password",()->"synthetic-runtime-only");
        p.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        p.add("spring.datasource.hikari.schema",()->fixture.schema);p.add("spring.jpa.properties.hibernate.default_schema",()->fixture.schema);
        p.add("spring.jpa.properties.hibernate.dialect",()->"org.hibernate.dialect.PostgreSQLDialect");
        p.add("spring.jpa.hibernate.ddl-auto",()->"validate");p.add("spring.sql.init.mode",()->"never");p.add("spring.flyway.enabled",()->"false");
    }
    static class Fake implements EmailProvider {
        final Map<UUID,OutboxCipher.Message> accepted=new java.util.concurrent.ConcurrentHashMap<>();
        public Acceptance accept(UUID id,OutboxCipher.Message message){accepted.putIfAbsent(id,message);return new Acceptance("synthetic-acceptance-only");}
    }
    @TestConfiguration static class Config {@Bean @Primary Fake fakeProvider(){return new Fake();}}
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired EmailOutbox worker; @Autowired Fake mail;
    record Session(String token,String id,String gym,String email){}
    String email(){return UUID.randomUUID()+"@fixture.invalid";}
    String body(Map<String,?> values){return JSON.writeValueAsString(values);}
    String request(MockHttpServletRequestBuilder req,String token,int expected)throws Exception {
        if(token!=null)req.header("Authorization","Bearer "+token);
        return mvc.perform(req).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }
    MockHttpServletRequestBuilder postJson(String path,Map<String,?> values){return post(path).contentType("application/json").content(body(values));}
    Map<String,Object> object(String value){return JSON.readValue(value,Map.class);}
    Session session(String value){var o=object(value);var u=(Map<?,?>)o.get("user");return new Session((String)o.get("token"),(String)u.get("id"),(String)u.get("gymId"),(String)u.get("email"));}
    Session verify(String address)throws Exception {
        for(int i=0;i<40&&worker.processOne();i++){}
        String token=mail.accepted.values().stream().filter(m->m.recipient().equals(address)&&m.text().contains("#token=")).findFirst().orElseThrow().text().split("#token=")[1].substring(0,43);
        return session(request(postJson("/api/auth/verify-email",Map.of("email",address,"token",token)),null,200));
    }
    Session owner(String name)throws Exception {
        String address=email();request(postJson("/api/auth/registration/owner",Map.of("fullName",name,"email",address,"password",PASSWORD,"gymName",name+" Gym","slug","clean-"+UUID.randomUUID())),null,202);
        assertThat(jdbc.queryForObject("select count(*) from users where email=?",Long.class,address)).isZero();return verify(address);
    }
    Session invite(Session owner,Session foreign,String role)throws Exception {
        String address=email();var invitation=object(request(postJson("/api/gym/invitations",Map.of("email",address,"role",role)),owner.token(),201));
        request(delete("/api/gym/invitations/"+invitation.get("id")),foreign.token(),404);
        request(postJson("/api/auth/registration/invitation",Map.of("fullName","Clean "+role,"email",address,"password",PASSWORD,"invitationToken",invitation.get("invitationToken"))),null,202);
        Session joined=verify(address);assertThat(joined.gym()).isEqualTo(owner.gym());return joined;
    }
    @Test void cleanHistoryVerifiedOwnersInvitationsAndEmptyCatalogsStayIsolated()throws Exception {
        assertThat(jdbc.queryForObject("select string_agg(version,',' order by installed_rank) from flyway_schema_history",String.class)).isEqualTo("1,2,4,5,6,7,8,9");
        assertThat(jdbc.queryForObject("select count(*) from users",Long.class)).isZero();
        // Certify the independently reviewed clean descriptor with restricted credentials.
        try(var c=jdbc.getDataSource().getConnection()){
            assertThat(com.fitcore.gym.operations.SchemaContract.fingerprint(c)).isEqualTo("257f2bf8e720e2ecb27c516ecbd216d4f16b77581abd9312b432821d2da42c7f");
            assertThat(com.fitcore.gym.operations.ReleaseReadiness.schemaReady(c)).as("Certified clean fingerprint must pass restricted runtime readiness").isTrue();
        }
        request(get("/api/health/ready"),null,200);
        Session a=owner("Owner Alpha"),b=owner("Owner Beta");assertThat(a.gym()).isNotEqualTo(b.gym());
        for(Session own:List.of(a,b)){
            Session foreign=own==a?b:a;
            assertThat(object(request(get("/api/auth/me"),own.token(),200)).get("gymId")).isEqualTo(own.gym());
            request(patch("/api/gym").contentType("application/json").content(body(Map.of("name","Configured "+own.id()))),own.token(),200);
            assertThat(request(get("/api/gym"),foreign.token(),200)).doesNotContain("Configured "+own.id());
            for(String path:List.of("/api/memberships/plans","/api/workouts/exercises","/api/workouts/plans","/api/nutrition/plans"))
                assertThat(JSON.readValue(request(get(path),own.token(),200),List.class)).isEmpty();
            var plan=object(request(postJson("/api/admin/membership-plans",Map.of("name","Independent Premium","price",79,"durationMonths",3,"features",List.of("Local feature"))),own.token(),201));
            request(put("/api/admin/membership-plans/"+plan.get("id")).contentType("application/json").content(body(Map.of("name","Foreign change","price",80,"durationMonths",3))),foreign.token(),404);
            Session member=invite(own,foreign,"MEMBER"),trainer=invite(own,foreign,"TRAINER");
            for(Session joined:List.of(member,trainer)){
                request(get("/api/auth/me"),joined.token(),200);request(get("/api/users/"+foreign.id()),joined.token(),404);
                request(get("/api/admin/users"),joined.token(),403);
            }
            assertThat(request(get("/api/admin/users"),own.token(),200)).contains(member.email(),trainer.email()).doesNotContain(foreign.email());
            request(get("/api/users/"+foreign.id()),own.token(),404);
            Session login=session(request(postJson("/api/auth/login",Map.of("email",member.email(),"password",PASSWORD)),null,200));
            request(get("/api/auth/me"),login.token(),200);request(get("/api/auth/me"),null,401);
            request(delete("/api/admin/users/"+member.id()),own.token(),204);
            request(get("/api/auth/me"),login.token(),401);
            request(get("/api/auth/me"),trainer.token(),200);
        }
        assertThat(jdbc.queryForObject("select count(*) from gyms g join users u on u.id=g.primary_owner_user_id and u.gym_id=g.id where u.role='OWNER' and u.enabled and u.email_verified_at is not null",Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from membership_plans where name='Independent Premium'",Long.class)).isEqualTo(2);
        fixture.migrate("9"); // no-op repeat must not seed a default gym or account.
        assertThat(jdbc.queryForObject("select count(*) from gyms",Long.class)).isEqualTo(2);
    }
}
