package com.fitcore.gym.tenancy;
import static org.assertj.core.api.Assertions.*;
import com.fitcore.gym.email.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.util.concurrent.*;
import java.time.*;

@EnabledIfEnvironmentVariable(named="FITCORE_TENANCY_TEST_TOKEN",matches="[a-f0-9]{64}")
class EmailOutboxTest {
 FoundationMigrationTest fixture=new FoundationMigrationTest();JdbcTemplate jdbc;DataSourceTransactionManager manager;TransactionTemplate tx;AesGcmOutboxCipher cipher;Fake provider;EmailOutbox box;
 static class Fake implements EmailProvider {
  Set<UUID> accepted=ConcurrentHashMap.newKeySet();int failures;boolean permanent,crash;Runnable afterAccept;java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
  public Acceptance accept(UUID id,OutboxCipher.Message message){calls.incrementAndGet();if(permanent)throw new Rejected();if(failures-->0)throw new IllegalStateException("secret-provider-payload");accepted.add(id);if(afterAccept!=null)afterAccept.run();if(crash){crash=false;throw new AssertionError("simulated process death");}return new Acceptance("receipt");}
 }
 @BeforeEach void setup()throws Exception {
  fixture.isolatedSchema();fixture.migrate("8");
  var ds=new DriverManagerDataSource(System.getenv("FITCORE_POSTGRES_TEST_URL")+"?currentSchema="+fixture.schema,System.getenv("FITCORE_POSTGRES_TEST_USERNAME"),System.getenv("FITCORE_POSTGRES_TEST_PASSWORD"));jdbc=new JdbcTemplate(ds);manager=new DataSourceTransactionManager(ds);tx=new TransactionTemplate(manager);
  cipher=new AesGcmOutboxCipher(Map.of("test",new byte[32]),"test");provider=new Fake();box=worker(provider);
 }
 @AfterEach void cleanup()throws Exception{fixture.cleanup();}
 EmailOutbox worker(EmailProvider p){var b=new EmailOutbox(jdbc,manager,cipher,p);ReflectionTestUtils.setField(b,"worker",true);return b;}
 UUID intent(){return tx.execute(s->{UUID challenge=UUID.randomUUID();jdbc.update("insert into account_challenges(id,token_hash,purpose,email,full_name,password_hash,gym_name,slug,expires_at,created_at) values(?,?,'OWNER','secret@fixture.invalid','Owner','hashed','Gym',?,localtimestamp+interval '1 day',localtimestamp)",challenge,challenge.toString(),"gym-"+challenge);return box.enqueue(new EmailIntent(null,challenge,null,"OWNER","secret@fixture.invalid","Subject","token-secret","<p>token-secret</p>",LocalDateTime.now(ZoneOffset.UTC).plusHours(24)));});}
 String status(UUID id){return jdbc.queryForObject("select status from email_outbox where id=?",String.class,id);}
 void due(){jdbc.update("update email_outbox set next_attempt_at=localtimestamp-interval '1 second',lease_until=case when status='PROCESSING' then localtimestamp-interval '1 second' else null end where status in ('PENDING','PROCESSING')");}
 @Test void atomicIntentAndRollback(){assertThatThrownBy(()->tx.execute(s->{intent();throw new IllegalStateException();})).isInstanceOf(IllegalStateException.class);assertThat(jdbc.queryForObject("select count(*) from email_outbox",Long.class)).isZero();assertThat(jdbc.queryForObject("select count(*) from account_challenges",Long.class)).isZero();UUID id=intent();assertThat(status(id)).isEqualTo("PENDING");assertThat(new String(jdbc.queryForObject("select payload from email_outbox where id=?",byte[].class,id),java.nio.charset.StandardCharsets.UTF_8)).doesNotContain("token-secret","secret@fixture");}
 UUID intentInCurrent(){return box.enqueue(new EmailIntent(null,null,null,"PASSWORD_CHANGED","a@fixture.invalid","Subject","Notice","<p>Notice</p>",LocalDateTime.now(ZoneOffset.UTC).plusHours(1)));}
 @Test void enqueueRequiresTransaction(){assertThatThrownBy(this::intentInCurrent).hasMessageContaining("transaction");}
 @Test void acceptedIsNotMailboxDeliveredAndSecretIsDeleted(){UUID id=intent();assertThat(box.processOne()).isTrue();assertThat(status(id)).isEqualTo("ACCEPTED");assertThat(jdbc.queryForObject("select payload from email_outbox where id=?",byte[].class,id)).isNull();assertThat(provider.accepted).containsExactly(id);assertThat(box.processOne()).isFalse();}
 @Test void simultaneousWorkersDoNotClaimTheSameMessage()throws Exception {UUID id=intent();var second=worker(provider);var pool=Executors.newFixedThreadPool(2);try{var results=pool.invokeAll(List.of(()->box.processOne(),()->second.processOne()));for(var result:results)result.get();assertThat(provider.calls.get()).isEqualTo(1);assertThat(status(id)).isEqualTo("ACCEPTED");}finally{pool.shutdownNow();}}
 @Test void outageRetriesWithSameIdAndBackoff(){UUID id=intent();provider.failures=1;box.processOne();assertThat(status(id)).isEqualTo("PENDING");assertThat(jdbc.queryForObject("select next_attempt_at>localtimestamp from email_outbox where id=?",Boolean.class,id)).isTrue();assertThat(jdbc.queryForObject("select failure_code from email_outbox where id=?",String.class,id)).isEqualTo("RETRYABLE_OR_UNKNOWN");due();box.processOne();assertThat(status(id)).isEqualTo("ACCEPTED");assertThat(provider.accepted).containsExactly(id);}
 @Test void boundedRetryDeadLettersAndWipesSecret(){UUID id=intent();provider.failures=10;for(int i=0;i<5;i++){due();box.processOne();}assertThat(status(id)).isEqualTo("DEAD");assertThat(jdbc.queryForObject("select attempts from email_outbox where id=?",Integer.class,id)).isEqualTo(5);assertThat(jdbc.queryForObject("select payload from email_outbox where id=?",byte[].class,id)).isNull();}
 @Test void permanentRejectDoesNotRetry(){UUID id=intent();provider.permanent=true;box.processOne();assertThat(status(id)).isEqualTo("DEAD");assertThat(box.processOne()).isFalse();}
 @Test void crashAfterAcceptanceRetriesIdempotently(){UUID id=intent();provider.crash=true;assertThatThrownBy(()->box.processOne()).isInstanceOf(AssertionError.class);assertThat(status(id)).isEqualTo("PROCESSING");due();box.processOne();assertThat(provider.calls.get()).isEqualTo(2);assertThat(provider.accepted).containsExactly(id);assertThat(status(id)).isEqualTo("ACCEPTED");}
 @Test void expiredAndConsumedChallengesAreNeverSent(){for(boolean consumed:List.of(false,true)){UUID id=intent();jdbc.update(consumed?"update account_challenges set consumed_at=localtimestamp":"update account_challenges set expires_at=localtimestamp-interval '1 day'");box.processOne();assertThat(status(id)).isEqualTo("CANCELLED");}assertThat(provider.calls.get()).isZero();}
 @Test void wrongOrLostKeyDeadLettersWithoutLeaking(){UUID id=intent();jdbc.update("update email_outbox set key_id='missing' where id=?",id);box.processOne();assertThat(status(id)).isEqualTo("DEAD");assertThat(jdbc.queryForObject("select failure_code from email_outbox where id=?",String.class,id)).isEqualTo("PAYLOAD_UNAVAILABLE");}
 @Test void metadataRetentionAndExpiryCleanup(){UUID id=intent();jdbc.update("update email_outbox set expires_at=localtimestamp-interval '1 day' where id=?",id);box.cleanup();assertThat(status(id)).isEqualTo("CANCELLED");jdbc.update("update email_outbox set completed_at=localtimestamp-interval '8 days' where id=?",id);assertThat(box.cleanup()).isEqualTo(1);}
 @Test void workerDisabledByDefault(){var disabled=new EmailOutbox(jdbc,manager,cipher,provider);UUID id=intent();assertThat(disabled.processOne()).isFalse();assertThat(status(id)).isEqualTo("PENDING");}
 @Test void revokedInvitationAndForeignGymFailClosed()throws Exception {
  try(var c=fixture.connection()){c.setAutoCommit(false);fixture.owner(c,1,"OWNER",true);fixture.owner(c,2,"OWNER",true);c.commit();}
  UUID invitation=UUID.randomUUID();jdbc.update("insert into gym_invitations(id,gym_id,email,token_hash,role,created_by_user_id,expires_at,created_at) values(?,?,?,'hash','MEMBER',?,localtimestamp+interval '2 days',localtimestamp)",invitation,fixture.gym(1),"invite@fixture.invalid",fixture.user(1));
  assertThatThrownBy(()->tx.execute(s->box.enqueue(new EmailIntent(fixture.gym(2),null,invitation,"MEMBER_INVITE","invite@fixture.invalid","subject","secret","html",LocalDateTime.now().plusDays(2))))).isInstanceOf(RuntimeException.class);
  UUID id=tx.execute(s->box.enqueue(new EmailIntent(fixture.gym(1),null,invitation,"MEMBER_INVITE","invite@fixture.invalid","subject","secret","html",LocalDateTime.now().plusDays(2))));jdbc.update("update gym_invitations set revoked_at=localtimestamp where id=?",invitation);box.processOne();assertThat(status(id)).isEqualTo("CANCELLED");assertThat(provider.calls.get()).isZero();
 }
 @Test void staleLeaseCannotAcknowledgeANewerClaim(){UUID id=intent();provider.afterAccept=()->jdbc.update("update email_outbox set lease_token=? where id=?",UUID.randomUUID(),id);box.processOne();assertThat(status(id)).isEqualTo("PROCESSING");provider.afterAccept=null;due();box.processOne();assertThat(status(id)).isEqualTo("ACCEPTED");assertThat(provider.accepted).containsExactly(id);}
 @Test void swappingEncryptedRowsCannotSendAnotherRecipientPayload(){UUID a=intent(),b=intent();byte[] ciphertext=jdbc.queryForObject("select payload from email_outbox where id=?",byte[].class,a);jdbc.update("update email_outbox set payload=? where id=?",ciphertext,b);box.processOne();box.processOne();assertThat(status(b)).isEqualTo("DEAD");assertThat(provider.accepted).containsExactly(a);}

}
