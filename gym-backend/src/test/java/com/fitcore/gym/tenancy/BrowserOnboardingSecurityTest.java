package com.fitcore.gym.tenancy;

import static org.assertj.core.api.Assertions.*;
import com.fitcore.gym.email.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;

/** Actual PostgreSQL + encrypted delivery, restricted runtime, signed gateway and Spring CSRF. */
@EnabledIfEnvironmentVariable(named="FITCORE_TENANCY_TEST_TOKEN",matches="[a-f0-9]{64}")
@SpringBootTest(properties={"fitcore.security-flows.demo-enabled=true","fitcore.email.outbox.enabled=true","fitcore.email.outbox.worker-enabled=true","fitcore.email.outbox.active-key=test","fitcore.email.outbox.keys=test=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@Import(BrowserOnboardingSecurityTest.Config.class)
class BrowserOnboardingSecurityTest {
    static final String SCHEMA="browser_onboarding_"+UUID.randomUUID().toString().replace("-","");
    static final String RUNTIME="onboarding_runtime_"+UUID.randomUUID().toString().replace("-","");
    @DynamicPropertySource static void database(DynamicPropertyRegistry p)throws Exception{BrowserAdmissionTest.configure(p,SCHEMA,RUNTIME);}
    static class Mail implements EmailProvider {
        final Map<UUID,OutboxCipher.Message> accepted=new ConcurrentHashMap<>();
        public Acceptance accept(UUID key,OutboxCipher.Message message){assertThat(message.recipient()).endsWith("@fixture.invalid");accepted.putIfAbsent(key,message);return new Acceptance("synthetic-acceptance-only");}
    }
    @TestConfiguration static class Config {@Bean @Primary Mail syntheticProvider(){return new Mail();}}
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean com.fitcore.gym.security.JwtService jwt;
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired EmailOutbox outbox; @Autowired Mail mail;
    final BrowserSessionSecurityTest http=new BrowserSessionSecurityTest();
    @BeforeEach void setup(){http.mvc=mvc;http.jdbc=jdbc;org.mockito.Mockito.clearInvocations(jwt);jdbc.update("delete from security_rate_limits");}
    String email(){return UUID.randomUUID()+"@fixture.invalid";}
    Map<String,Object> registration(String email){return Map.of("fullName","Synthetic Owner","email",email,"password",BrowserSessionSecurityTest.PASSWORD,"gymName","Onboard Gym","slug","onboard-"+UUID.randomUUID());}
    void receipt(BrowserSessionSecurityTest.Jar jar,String route,Object body,int status)throws Exception{
        var r=http.send(jar,"POST","/api/browser/"+route,body,true);
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(status);
        assertThat(r.getHeaders("Set-Cookie")).isEmpty();assertThat(r.getContentAsString()).isEmpty();
    }
    String token(String email,String fragment){for(int n=0;n<100&&outbox.processOne();n++){}return mail.accepted.values().stream().filter(m->m.recipient().equals(email)&&m.text().contains(fragment)).findFirst().orElseThrow().text().split(fragment)[1].substring(0,43);}
    BrowserSessionSecurityTest.Account owner(String address)throws Exception{
        var pre=http.bootstrap();receipt(pre,"registration/owner",registration(address),202);
        assertThat(jdbc.queryForObject("select count(*) from users where email=?",Integer.class,address)).isZero();
        String token=token(address,"#token=");receipt(pre,"verify-email",Map.of("email",address,"token",token),204);
        var row=jdbc.queryForMap("select id,gym_id from users where email=? and email_verified_at is not null",address);
        assertThat(http.send(pre,"GET","/api/auth/me",null,false).getStatus()).isEqualTo(401);
        return new BrowserSessionSecurityTest.Account((UUID)row.get("id"),(UUID)row.get("gym_id"),address,"OWNER");
    }
    @Test void ownerVerificationIsTokenlessAndRequiresExplicitCookieLogin()throws Exception{
        var owner=owner(email());org.mockito.Mockito.verify(jwt,org.mockito.Mockito.never()).generateToken(org.mockito.ArgumentMatchers.any());var auth=http.authenticated(owner);
        assertThat(http.send(auth,"GET","/api/auth/me",null,false).getContentAsString()).contains(owner.id().toString());
        assertThat(jdbc.queryForObject("select count(*) from email_outbox where status='ACCEPTED' and payload is not null",Integer.class)).isZero();
    }
    @Test void receiptAdmissionRequiresCsrfLiveAnonymousContextAndExactRoute()throws Exception{
        var pre=http.bootstrap();var body=registration(email());
        assertThat(http.send(pre,"POST","/api/browser/registration/owner",body,false).getStatus()).isEqualTo(403);
        assertThat(http.send(new BrowserSessionSecurityTest.Jar(),"POST","/api/browser/registration/owner",body,true).getStatus()).isIn(401,403);
        assertThat(http.send(pre,"GET","/api/browser/registration/owner",null,false).getStatus()).isEqualTo(405);
        var account=owner(email());var auth=http.authenticated(account);
        assertThat(http.send(auth,"POST","/api/browser/registration/owner",body,true).getStatus()).isEqualTo(409);
        var old=pre.copy();http.accept(pre,http.login(pre,account));
        assertThat(http.send(old,"POST","/api/browser/registration/owner",body,true).getStatus()).isEqualTo(423);
        assertThat(http.send(pre,"POST","/api/auth/verify-email",Map.of("email",account.email(),"token","x".repeat(43)),true).getStatus()).isIn(403,423);
    }
    @Test void verificationRejectsWrongEmailExpiryAndReplayWithoutCreatingWrongAccount()throws Exception{
        var pre=http.bootstrap();String address=email();receipt(pre,"registration/owner",registration(address),202);String token=token(address,"#token=");
        assertThat(http.send(pre,"POST","/api/browser/verify-email",Map.of("email",email(),"token",token),true).getStatus()).isEqualTo(400);
        assertThat(jdbc.queryForObject("select count(*) from users where email=?",Integer.class,address)).isZero();
        receipt(pre,"verify-email",Map.of("email",address,"token",token),204);
        assertThat(http.send(pre,"POST","/api/browser/verify-email",Map.of("email",address,"token",token),true).getStatus()).isEqualTo(400);
        String expired=email();receipt(pre,"registration/owner",registration(expired),202);String secret=token(expired,"#token=");
        jdbc.update("update account_challenges set created_at=now()-interval '2 days',expires_at=now()-interval '1 day' where email=?",expired);
        assertThat(http.send(pre,"POST","/api/browser/verify-email",Map.of("email",expired,"token",secret),true).getStatus()).isEqualTo(400);
    }
    @Test void simultaneousVerificationConsumesChallengeExactlyOnce()throws Exception{
        var pre=http.bootstrap();String address=email();receipt(pre,"registration/owner",registration(address),202);String token=token(address,"#token=");
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{Callable<Integer> request=()->{start.await();return http.send(pre,"POST","/api/browser/verify-email",Map.of("email",address,"token",token),true).getStatus();};
            var one=pool.submit(request);var two=pool.submit(request);start.countDown();
            assertThat(List.of(one.get(20,TimeUnit.SECONDS),two.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(204,400);
            assertThat(jdbc.queryForObject("select count(*) from users where email=?",Integer.class,address)).isEqualTo(1);
        }finally{pool.shutdownNow();}
    }
    @Test void resetIsTokenlessSingleUseAndRevokesExistingCookieSessions()throws Exception{
        var account=owner(email());var auth=http.authenticated(account);var pre=http.bootstrap();
        receipt(pre,"forgot-password",Map.of("email",account.email()),202);String token=token(account.email(),"/reset-password#token=");
        receipt(pre,"reset-password",Map.of("email",account.email(),"token",token,"password","Replacement-Secret!2026"),204);
        assertThat(http.send(auth,"GET","/api/auth/me",null,false).getStatus()).isEqualTo(401);
        assertThat(http.send(pre,"POST","/api/browser/reset-password",Map.of("email",account.email(),"token",token,"password","Replacement-Secret!2026"),true).getStatus()).isEqualTo(400);
        assertThat(http.login(pre,account).getStatus()).isEqualTo(401);
        var changed=http.send(pre,"POST","/api/browser/login",Map.of("email",account.email(),"password","Replacement-Secret!2026"),true);assertThat(changed.getStatus()).isEqualTo(200);
    }
    @Test void memberAndTrainerInvitationsRemainGymBoundAndDoNotExposeSecretOnCreation()throws Exception{
        var a=owner(email());var b=owner(email());var authA=http.authenticated(a);var authB=http.authenticated(b);
        for(String role:List.of("MEMBER","TRAINER")){
            String recipient=email();var issued=http.send(authA,"POST","/api/gym/invitations",Map.of("email",recipient,"role",role),true);
            assertThat(issued.getStatus()).as(issued.getContentAsString()).isEqualTo(201);assertThat(issued.getContentAsString()).contains("QUEUED").doesNotContain("invitationToken");
            var id=http.json.readTree(issued.getContentAsString()).get("id").asText();
            assertThat(http.send(authB,"DELETE","/api/gym/invitations/"+id,null,true).getStatus()).isEqualTo(404);
            String invite=token(recipient,"#invitationToken=");var pre=http.bootstrap();
            receipt(pre,"registration/invitation",Map.of("fullName","Synthetic Invitee","email",recipient,"password",BrowserSessionSecurityTest.PASSWORD,"invitationToken",invite),202);
            receipt(pre,"verify-email",Map.of("email",recipient,"token",token(recipient,"#token=")),204);
            assertThat(jdbc.queryForMap("select gym_id,role from users where email=?",recipient)).containsEntry("gym_id",a.gym()).containsEntry("role",role);
        }
    }
    @Test void cookieReceiptsStayDisabledWithoutBothExplicitLocalDeliveryGates()throws Exception{
        var controller=mvc.getDispatcherServlet().getWebApplicationContext().getBean(com.fitcore.gym.security.browser.BrowserAccountController.class);
        var pre=http.bootstrap();long before=jdbc.queryForObject("select count(*) from account_challenges",Long.class);
        for(String flag:List.of("enabled","queued")){
            org.springframework.test.util.ReflectionTestUtils.setField(controller,flag,false);
            try{var r=http.send(pre,"POST","/api/browser/registration/owner",registration(email()),true);
                assertThat(r.getStatus()).isEqualTo(403);assertThat(r.getHeaders("Set-Cookie")).isEmpty();
                assertThat(jdbc.queryForObject("select count(*) from account_challenges",Long.class)).isEqualTo(before);
            }finally{org.springframework.test.util.ReflectionTestUtils.setField(controller,flag,true);}
        }
    }
    @Test void genericRequestsAndSharedAbuseLimitsDoNotExposeAccountExistence()throws Exception{
        var pre=http.bootstrap();String unknown=email();receipt(pre,"forgot-password",Map.of("email",unknown),202);
        for(int n=1;n<50;n++)receipt(pre,"forgot-password",Map.of("email",unknown),202);
        var rejected=http.send(pre,"POST","/api/browser/forgot-password",Map.of("email",unknown),true);
        assertThat(rejected.getStatus()).isEqualTo(429);assertThat(rejected.getHeaders("Set-Cookie")).isEmpty();
    }
}
