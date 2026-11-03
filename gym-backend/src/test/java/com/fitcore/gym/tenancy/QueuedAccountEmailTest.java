package com.fitcore.gym.tenancy;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fitcore.gym.email.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.*;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;

@EnabledIfEnvironmentVariable(named="FITCORE_TENANCY_TEST_TOKEN",matches="[a-f0-9]{64}")
@SpringBootTest(properties={"fitcore.security-flows.demo-enabled=true","fitcore.email.outbox.enabled=true","fitcore.email.outbox.worker-enabled=true","fitcore.email.outbox.active-key=test","fitcore.email.outbox.keys=test=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@Import(QueuedAccountEmailTest.Config.class)
class QueuedAccountEmailTest {
 static FoundationMigrationTest fixture=new FoundationMigrationTest();
 @DynamicPropertySource static void database(DynamicPropertyRegistry p)throws Exception{
  fixture.isolatedSchema();fixture.migrate("8");
  p.add("spring.datasource.url",()->System.getenv("FITCORE_POSTGRES_TEST_URL"));p.add("spring.datasource.username",()->System.getenv("FITCORE_POSTGRES_TEST_USERNAME"));p.add("spring.datasource.password",()->System.getenv("FITCORE_POSTGRES_TEST_PASSWORD"));p.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");p.add("spring.datasource.hikari.schema",()->fixture.schema);p.add("spring.jpa.properties.hibernate.default_schema",()->fixture.schema);p.add("spring.jpa.properties.hibernate.dialect",()->"org.hibernate.dialect.PostgreSQLDialect");p.add("spring.jpa.hibernate.ddl-auto",()->"validate");p.add("spring.sql.init.mode",()->"never");
 }
 static class Fake implements EmailProvider {Map<UUID,OutboxCipher.Message> accepted=new java.util.concurrent.ConcurrentHashMap<>();public Acceptance accept(UUID id,OutboxCipher.Message m){accepted.putIfAbsent(id,m);return new Acceptance("accepted-only");}}
 @TestConfiguration static class Config {@Bean @Primary Fake testProvider(){return new Fake();}}
 @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired EmailOutbox worker;@Autowired Fake provider;@Autowired com.fitcore.gym.service.AccountSecurityService security;@Autowired com.fitcore.gym.service.OnboardingService onboarding;
 String email(){return UUID.randomUUID()+"@fixture.invalid";}
 String password="Queued-Password!2026";
 String token(String email,String fragment){return provider.accepted.values().stream().filter(m->m.recipient().equals(email)&&m.text().contains(fragment)).reduce((a,b)->b).orElseThrow().text().split(fragment)[1].substring(0,43);}
 void drain(){for(int i=0;i<20&&worker.processOne();i++){} }
 String owner(String address)throws Exception{
  mvc.perform(post("/api/auth/registration/owner").contentType("application/json").content("{\"fullName\":\"Queued Owner\",\"email\":\""+address+"\",\"password\":\""+password+"\",\"gymName\":\"Queued Gym\",\"slug\":\"q-"+UUID.randomUUID()+"\"}")).andExpect(status().isAccepted());
  assertThat(jdbc.queryForObject("select count(*) from users where email=?",Long.class,address)).isZero();drain();
  var result=mvc.perform(post("/api/auth/verify-email").contentType("application/json").content("{\"email\":\""+address+"\",\"token\":\""+token(address,"#token=")+"\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  return (String)tools.jackson.databind.json.JsonMapper.builder().build().readValue(result,Map.class).get("token");
 }
 @Test void queuedVerificationResetAndNotificationAreAtomic()throws Exception{
  String address=email();String session=owner(address);
  mvc.perform(post("/api/auth/forgot-password").contentType("application/json").content("{\"email\":\""+address+"\"}")).andExpect(status().isAccepted());
  assertThat(jdbc.queryForObject("select count(*) from email_outbox where kind='RESET' and status='PENDING'",Long.class)).isEqualTo(1);
  drain();String reset=provider.accepted.values().stream().filter(m->m.recipient().equals(address)&&m.text().contains("/reset-password")).findFirst().orElseThrow().text().split("#token=")[1].substring(0,43);
  mvc.perform(post("/api/auth/reset-password").contentType("application/json").content("{\"email\":\""+address+"\",\"token\":\""+reset+"\",\"password\":\"Changed-Password!2026\"}")).andExpect(status().isNoContent());
  mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+session)).andExpect(status().isUnauthorized());
  assertThat(jdbc.queryForObject("select count(*) from email_outbox where kind='PASSWORD_CHANGED' and status='PENDING'",Long.class)).isEqualTo(1);drain();
  assertThat(jdbc.queryForObject("select count(*) from email_outbox where status='ACCEPTED' and (payload is not null or key_id is not null)",Long.class)).isZero();
 }
 @Test void invitationIntentIsBoundToIssuerGymAndRevocationWipesPayload()throws Exception{
  String address=email(),member=email();String session=owner(address);
  String response=mvc.perform(post("/api/gym/invitations").header("Authorization","Bearer "+session).contentType("application/json").content("{\"email\":\""+member+"\",\"role\":\"TRAINER\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
  var issued=tools.jackson.databind.json.JsonMapper.builder().build().readValue(response,Map.class);UUID id=UUID.fromString((String)issued.get("id"));
  assertThat(jdbc.queryForObject("select count(*) from email_outbox o join gym_invitations i on i.id=o.invitation_id and i.gym_id=o.gym_id where i.id=? and o.kind='TRAINER_INVITE' and o.status='PENDING'",Long.class,id)).isEqualTo(1);
  mvc.perform(post("/api/auth/registration/invitation").contentType("application/json").content("{\"fullName\":\"Pending Trainer\",\"email\":\""+member+"\",\"password\":\""+password+"\",\"invitationToken\":\""+issued.get("invitationToken")+"\"}")).andExpect(status().isAccepted());
  org.springframework.test.util.ReflectionTestUtils.setField(onboarding,"queued",false);
  mvc.perform(delete("/api/gym/invitations/"+id).header("Authorization","Bearer "+session)).andExpect(status().isNoContent());
  assertThat(jdbc.queryForObject("select payload from email_outbox where invitation_id=?",byte[].class,id)).isNull();assertThat(jdbc.queryForObject("select status from email_outbox where invitation_id=?",String.class,id)).isEqualTo("CANCELLED");
  assertThat(jdbc.queryForObject("select count(*) from email_outbox where challenge_id in(select id from account_challenges where email=?) and payload is not null",Long.class,member)).isZero();
  org.springframework.test.util.ReflectionTestUtils.setField(onboarding,"queued",true);
 }
 @Test void completingChallengeScrubsCompetingIntentsEvenWhenIssuanceIsDisabled()throws Exception{
  String address=email();
  for(int i=0;i<2;i++)mvc.perform(post("/api/auth/registration/owner").contentType("application/json").content("{\"fullName\":\"Queued Owner\",\"email\":\""+address+"\",\"password\":\""+password+"\",\"gymName\":\"Queued Gym\",\"slug\":\"q-"+UUID.randomUUID()+"\"}")).andExpect(status().isAccepted());
  while(provider.accepted.values().stream().noneMatch(m->m.recipient().equals(address)))assertThat(worker.processOne()).isTrue();
  org.springframework.test.util.ReflectionTestUtils.setField(security,"queued",false);
  try{mvc.perform(post("/api/auth/verify-email").contentType("application/json").content("{\"email\":\""+address+"\",\"token\":\""+token(address,"#token=")+"\"}")).andExpect(status().isOk());
   assertThat(jdbc.queryForObject("select count(*) from email_outbox where challenge_id in(select id from account_challenges where email=?) and payload is not null",Long.class,address)).isZero();
  }finally{org.springframework.test.util.ReflectionTestUtils.setField(security,"queued",true);}
 }

}
