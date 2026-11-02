package com.fitcore.gym.tenancy;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fitcore.gym.email.TransactionalEmailSender;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

@EnabledIfEnvironmentVariable(named="FITCORE_TENANCY_TEST_TOKEN",matches="[a-f0-9]{64}")
@SpringBootTest(properties={"fitcore.onboarding.demo-enabled=false","fitcore.security-flows.demo-enabled=true"})
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@Import(AccountSecurityTest.MailConfig.class)
class AccountSecurityTest {
    static final FoundationMigrationTest fixture=new FoundationMigrationTest();
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) throws Exception {
        fixture.isolatedSchema();fixture.migrate("7");
        p.add("spring.datasource.url",()->System.getenv("FITCORE_POSTGRES_TEST_URL"));
        p.add("spring.datasource.username",()->System.getenv("FITCORE_POSTGRES_TEST_USERNAME"));
        p.add("spring.datasource.password",()->System.getenv("FITCORE_POSTGRES_TEST_PASSWORD"));
        p.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        p.add("spring.datasource.hikari.schema",()->fixture.schema);
        p.add("spring.jpa.properties.hibernate.default_schema",()->fixture.schema);
        p.add("spring.jpa.properties.hibernate.dialect",()->"org.hibernate.dialect.PostgreSQLDialect");
        p.add("spring.jpa.hibernate.ddl-auto",()->"validate");p.add("spring.sql.init.mode",()->"never");
    }
    public static class Mail implements TransactionalEmailSender {
        final Map<String,String> messages=new java.util.concurrent.ConcurrentHashMap<>();
        public void send(String to,String subject,String text){if(to.startsWith("undeliverable-"))throw new IllegalStateException("test-only failure");messages.put(to,text);}
        String token(String email){return messages.get(email).split("#token=")[1];}
    }
    @TestConfiguration static class MailConfig {@Bean @Primary Mail localTestMail(){return new Mail();}}
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired Mail mail;
    static final String PASSWORD="Verified-Password!2026";
    String email(){return UUID.randomUUID()+"@fixture.invalid";}
    String ownerBody(String email){return "{\"fullName\":\"Verified Owner\",\"email\":\""+email+"\",\"password\":\""+PASSWORD+"\",\"gymName\":\"Verified Gym\",\"slug\":\"gym-"+UUID.randomUUID()+"\"}";}
    void pending(String email)throws Exception {mvc.perform(post("/api/auth/registration/owner").contentType("application/json").content(ownerBody(email))).andExpect(status().isAccepted());}
    String verify(String email,String token)throws Exception {return mvc.perform(post("/api/auth/verify-email").contentType("application/json").content("{\"email\":\""+email+"\",\"token\":\""+token+"\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();}
    @Test void ownerExistsOnlyAfterSingleUseVerification() throws Exception {
        String email=email();pending(email);
        assertThat(jdbc.queryForObject("select count(*) from users where email=?",Long.class,email)).isZero();
        String token=mail.token(email);assertThat(token).hasSize(43);
        verify(email,token);
        assertThat(jdbc.queryForObject("select count(*) from users where email=? and email_verified_at is not null and role='OWNER'",Long.class,email)).isEqualTo(1);
        mvc.perform(post("/api/auth/verify-email").contentType("application/json").content("{\"email\":\""+email+"\",\"token\":\""+token+"\"}")).andExpect(status().isBadRequest());
    }
    String jwt(String json) throws Exception {return (String)tools.jackson.databind.json.JsonMapper.builder().build().readValue(json,Map.class).get("token");}
    String registered(String address) throws Exception {pending(address);return jwt(verify(address,mail.token(address)));}
    void requestToken(String address,boolean reset)throws Exception {
        mvc.perform(post(reset?"/api/auth/forgot-password":"/api/auth/verification/request").contentType("application/json").content("{\"email\":\""+address+"\"}")).andExpect(status().isAccepted());
    }
    String body(String address,String token){return "{\"email\":\""+address+"\",\"token\":\""+token+"\"}";}
    @Test void verificationBindsEmailAndPersistsOnlyDigest() throws Exception {
        String a=email(),b=email();pending(a);pending(b);String token=mail.token(a);
        assertThat(jdbc.queryForObject("select token_hash from account_challenges where email=?",String.class,a)).isEqualTo(com.fitcore.gym.service.AccountSecurityService.digest(token)).doesNotContain(token);
        mvc.perform(post("/api/auth/verify-email").contentType("application/json").content(body(b,token))).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from users where email in (?,?)",Long.class,a,b)).isZero();
        verify(a,token);verify(b,mail.token(b));
        assertThat(jdbc.queryForObject("select count(distinct gym_id) from users where email in (?,?)",Long.class,a,b)).isEqualTo(2);
    }
    @Test void expiredVerificationCannotCreateAccount()throws Exception {
        String address=email();pending(address);jdbc.update("update account_challenges set expires_at=current_timestamp-interval '1 day' where email=?",address);
        mvc.perform(post("/api/auth/verify-email").contentType("application/json").content(body(address,mail.token(address)))).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from users where email=?",Long.class,address)).isZero();
    }
    @Test void resetInvalidatesJwtAndCannotBeReplayedOrUsedAsVerification()throws Exception {
        String address=email(),other=email();String old=registered(address);String otherJwt=registered(other);
        requestToken(address,true);String token=mail.token(address);
        mvc.perform(post("/api/auth/verify-email").contentType("application/json").content(body(address,token))).andExpect(status().isBadRequest());
        String reset=body(address,token).replace("}",",\"password\":\"Changed-Password!2026\"}");
        mvc.perform(post("/api/auth/reset-password").contentType("application/json").content(reset)).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+old)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+otherJwt)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/reset-password").contentType("application/json").content(reset)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"email\":\""+address+"\",\"password\":\"Changed-Password!2026\"}")).andExpect(status().isOk());
    }
    @Test void unknownRecoveryHasSamePublicResponseAndNoMail()throws Exception {
        String missing=email();requestToken(missing,true);assertThat(mail.messages).doesNotContainKey(missing);
    }
    @Test void expiredResetAndWrongAccountDoNotChangeCredentials()throws Exception {
        String a=email(),b=email();String before=registered(a);registered(b);requestToken(a,true);String token=mail.token(a);
        for(String address:List.of(b,a)){
            if(address.equals(a))jdbc.update("update account_challenges set expires_at=current_timestamp-interval '1 day' where email=? and purpose='RESET'",a);
            mvc.perform(post("/api/auth/reset-password").contentType("application/json").content(body(address,token).replace("}",",\"password\":\"Changed-Password!2026\"}"))).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+before)).andExpect(status().isOk());
    }
    @Test void concurrentVerificationHasOneWinner()throws Exception {
        String address=email();pending(address);String token=mail.token(address);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {var jobs=new ArrayList<java.util.concurrent.Callable<Integer>>();for(int i=0;i<2;i++)jobs.add(()->mvc.perform(post("/api/auth/verify-email").contentType("application/json").content(body(address,token))).andReturn().getResponse().getStatus());
            var results=pool.invokeAll(jobs);assertThat(List.of(results.get(0).get(),results.get(1).get())).containsExactlyInAnyOrder(200,400);
        }finally{pool.shutdownNow();}
    }
    @Test void concurrentResetHasOneWinner()throws Exception {
        String address=email();registered(address);requestToken(address,true);String token=mail.token(address);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {var jobs=new ArrayList<java.util.concurrent.Callable<Integer>>();for(int i=0;i<2;i++)jobs.add(()->mvc.perform(post("/api/auth/reset-password").contentType("application/json").content(body(address,token).replace("}",",\"password\":\"Changed-Password!2026\"}"))).andReturn().getResponse().getStatus());
            var results=pool.invokeAll(jobs);assertThat(List.of(results.get(0).get(),results.get(1).get())).containsExactlyInAnyOrder(204,400);
        }finally{pool.shutdownNow();}
    }
    @Test void invitationsRemainBoundToGymAndEmailUntilVerified()throws Exception {
        String ownerA=email(),ownerB=email();String a=registered(ownerA),b=registered(ownerB);
        String member=email();String json=mvc.perform(post("/api/gym/invitations").header("Authorization","Bearer "+a).contentType("application/json").content("{\"email\":\""+member+"\",\"role\":\"TRAINER\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var invitation=tools.jackson.databind.json.JsonMapper.builder().build().readValue(json,Map.class);
        String input="{\"email\":\""+member+"\",\"fullName\":\"Verified Trainer\",\"password\":\""+PASSWORD+"\",\"invitationToken\":\""+invitation.get("invitationToken")+"\"}";
        mvc.perform(post("/api/auth/registration/invitation").contentType("application/json").content(input)).andExpect(status().isAccepted());
        assertThat(jdbc.queryForObject("select count(*) from users where email=?",Long.class,member)).isZero();
        mvc.perform(delete("/api/gym/invitations/"+invitation.get("id")).header("Authorization","Bearer "+b)).andExpect(status().isNotFound());
        String trainer=jwt(verify(member,mail.token(member)));
        String gid=jdbc.queryForObject("select gym_id::text from users where email=?",String.class,ownerA);
        assertThat(jdbc.queryForObject("select count(*) from trainer_profiles t join users u on u.id=t.user_id where u.email=? and t.gym_id=?::uuid",Long.class,member,gid)).isEqualTo(1);
        mvc.perform(get("/api/users/"+jdbc.queryForObject("select id from users where email=?",UUID.class,ownerB)).header("Authorization","Bearer "+trainer)).andExpect(status().isNotFound());
    }
    @Test void sharedLimitsCountAcrossSeparateInstances()throws Exception {
        var first=new com.fitcore.gym.security.SharedAbuseLimiter(jdbc,new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        var ds=new org.springframework.jdbc.datasource.DriverManagerDataSource(System.getenv("FITCORE_POSTGRES_TEST_URL")+"?currentSchema="+fixture.schema,System.getenv("FITCORE_POSTGRES_TEST_USERNAME"),System.getenv("FITCORE_POSTGRES_TEST_PASSWORD"));
        var second=new com.fitcore.gym.security.SharedAbuseLimiter(new JdbcTemplate(ds),new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds));
        var pool=java.util.concurrent.Executors.newFixedThreadPool(8);String action="parallel-"+UUID.randomUUID();
        try {var work=new ArrayList<java.util.concurrent.Callable<Boolean>>();for(int n=0;n<100;n++){var limiter=n%2==0?first:second;work.add(()->limiter.allow(action,"same-peer"));}
            int accepted=0;for(var result:pool.invokeAll(work))if(result.get())accepted++;assertThat(accepted).isEqualTo(50);
        }finally{pool.shutdownNow();}
    }
    @Test void spoofedForwardingHeadersDoNotBypassLimits()throws Exception {
        for(int i=0;i<51;i++){final int n=i;mvc.perform(post("/api/auth/verification/request").with(r->{r.setRemoteAddr("198.51.100.31");return r;}).header("X-Forwarded-For","203.0.113."+n).header("Forwarded","for=203.0.113."+n).contentType("application/json").content("{\"email\":\"nobody@fixture.invalid\"}")).andExpect(status().is(i<50?202:429));}
    }

    @Test void secureModeCannotUseLegacyUnverifiedSignup()throws Exception {
        mvc.perform(post("/api/auth/register-owner").contentType("application/json").content(ownerBody(email()))).andExpect(status().isForbidden());
    }
    @Test void revokedInvitationCannotActivatePendingRegistrant()throws Exception {
        String owner=email(),member=email();String session=registered(owner);
        var output=mvc.perform(post("/api/gym/invitations").header("Authorization","Bearer "+session).contentType("application/json").content("{\"email\":\""+member+"\",\"role\":\"MEMBER\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var invitation=tools.jackson.databind.json.JsonMapper.builder().build().readValue(output,Map.class);
        mvc.perform(post("/api/auth/registration/invitation").contentType("application/json").content("{\"email\":\""+member+"\",\"fullName\":\"Pending Member\",\"password\":\""+PASSWORD+"\",\"invitationToken\":\""+invitation.get("invitationToken")+"\"}")).andExpect(status().isAccepted());
        mvc.perform(delete("/api/gym/invitations/"+invitation.get("id")).header("Authorization","Bearer "+session)).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/verify-email").contentType("application/json").content(body(member,mail.token(member)))).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from users where email=?",Long.class,member)).isZero();
    }
    @Test void legacyEmailStartsUnverifiedAndCanBeVerifiedWithoutWrongAccountChange()throws Exception {
        String owner=email();registered(owner);String member=email();
        UUID gym=jdbc.queryForObject("select gym_id from users where email=?",UUID.class,owner);
        jdbc.update("insert into users(id,gym_id,full_name,email,password_hash,role,enabled,created_at,updated_at) values(?,?,'Legacy Member',?,'preserved-legacy-hash','MEMBER',true,current_timestamp,current_timestamp)",UUID.randomUUID(),gym,member);
        assertThat(jdbc.queryForObject("select email_verified_at from users where email=?",Object.class,member)).isNull();
        requestToken(member,false);verify(member,mail.token(member));
        assertThat(jdbc.queryForObject("select count(*) from users where email=? and email_verified_at is not null",Long.class,member)).isEqualTo(1);
    }
    @Test void separateJvmInstancesShareRateBudget()throws Exception {
        String action="jvm-"+UUID.randomUUID();String javaBinary=java.nio.file.Path.of(System.getProperty("java.home"),"bin","java").toString();
        var args=List.of(javaBinary,"-cp",System.getProperty("java.class.path"),"com.fitcore.gym.tenancy.LimiterProcessProbe",fixture.schema,action);
        var a=new ProcessBuilder(args).start();var b=new ProcessBuilder(args).start();
        try {assertThat(a.waitFor(30,java.util.concurrent.TimeUnit.SECONDS)).isTrue();assertThat(b.waitFor(30,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(a.exitValue()).isZero();assertThat(b.exitValue()).isZero();
            assertThat(Integer.parseInt(new String(a.getInputStream().readAllBytes()).trim())+Integer.parseInt(new String(b.getInputStream().readAllBytes()).trim())).isEqualTo(50);
        }finally{a.destroyForcibly();b.destroyForcibly();}
    }

    @Test void emailChangeRevokesVerificationAndOldSession()throws Exception {
        String before=email(),after=email();String token=registered(before);
        jdbc.update("update users set email=? where email=?",after,before);
        assertThat(jdbc.queryForObject("select email_verified_at from users where email=?",Object.class,after)).isNull();
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        requestToken(after,false);verify(after,mail.token(after));
    }
    @Test void rejectedPeerDoesNotSpendOthersGlobalBudget()throws Exception {
        var limit=new com.fitcore.gym.security.SharedAbuseLimiter(jdbc,new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        String action="drain-"+UUID.randomUUID();for(int n=0;n<500;n++)assertThat(limit.allow(action,"one-peer")).isEqualTo(n<50);
        assertThat(limit.allow(action,"fresh-peer")).isTrue();
    }

    @Test void senderFailureRollsBackPendingSecretAndCreatesNoAccount()throws Exception {
        String address="undeliverable-"+email();pending(address);
        assertThat(jdbc.queryForObject("select count(*) from account_challenges where email=?",Long.class,address)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from users where email=?",Long.class,address)).isZero();assertThat(mail.messages).doesNotContainKey(address);
    }

}
