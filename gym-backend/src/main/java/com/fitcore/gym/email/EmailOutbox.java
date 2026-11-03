package com.fitcore.gym.email;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import org.springframework.beans.factory.annotation.Value;

@Component
public class EmailOutbox {
 private final JdbcTemplate jdbc;private final TransactionTemplate tx;private final OutboxCipher cipher;private final EmailProvider provider;
 @Value("${fitcore.email.outbox.worker-enabled:false}") private boolean worker;
 public EmailOutbox(JdbcTemplate jdbc,PlatformTransactionManager manager,OutboxCipher cipher,EmailProvider provider){this.jdbc=jdbc;this.tx=new TransactionTemplate(manager);this.tx.setPropagationBehavior(3);this.cipher=cipher;this.provider=provider;}
 public static boolean available(JdbcTemplate jdbc){
  return Boolean.TRUE.equals(jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>)c->{
   if(!"PostgreSQL".equals(c.getMetaData().getDatabaseProductName()))return false;
   try(var statement=c.createStatement();var result=statement.executeQuery("select to_regclass('email_outbox') is not null")){return result.next()&&result.getBoolean(1);}
  }));
 }
 public static void cancelEmail(JdbcTemplate jdbc,String email){if(available(jdbc))jdbc.update("update email_outbox set status='CANCELLED',failure_code='CONSUMED',payload=null,key_id=null,lease_token=null,lease_until=null,completed_at=localtimestamp where challenge_id in(select id from account_challenges where email=?) and status in ('PENDING','PROCESSING')",email);}
 public static void cancelInvitation(JdbcTemplate jdbc,UUID id,UUID gym){if(available(jdbc))jdbc.update("update email_outbox set status='CANCELLED',failure_code='INVITATION_UNAVAILABLE',payload=null,key_id=null,lease_token=null,lease_until=null,completed_at=localtimestamp where (invitation_id=? and gym_id=? or challenge_id in(select c.id from account_challenges c join gym_invitations i on i.token_hash=c.invitation_hash and i.gym_id=c.gym_id where i.id=? and i.gym_id=?)) and status in ('PENDING','PROCESSING')",id,gym,id,gym);}
 static String binding(UUID id,String kind,Object challenge,Object invitation,Object gym){return id+"|"+kind+"|"+challenge+"|"+invitation+"|"+gym;}
 public UUID enqueue(EmailIntent intent){
  if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Email intent requires the application transaction");
  if(intent.recipient()==null||intent.recipient().length()>254||intent.recipient().contains("\r")||intent.recipient().contains("\n")||!intent.recipient().contains("@"))throw new IllegalArgumentException("Invalid recipient");
  UUID id=UUID.randomUUID();var sealed=cipher.seal(binding(id,intent.kind(),intent.challengeId(),intent.invitationId(),intent.gymId()),new OutboxCipher.Message(intent.recipient(),intent.subject(),intent.text(),intent.html()));
  jdbc.update("insert into email_outbox(id,gym_id,challenge_id,invitation_id,kind,key_id,payload,next_attempt_at,expires_at,created_at) values(?,?,?,?,?,?,?,localtimestamp,?,localtimestamp)",id,intent.gymId(),intent.challengeId(),intent.invitationId(),intent.kind(),sealed.keyId(),sealed.bytes(),intent.expiresAt());return id;
 }
 private boolean valid(Map<String,Object> row){
  if(!((Timestamp)row.get("expires_at")).toLocalDateTime().isAfter(jdbc.queryForObject("select localtimestamp",LocalDateTime.class)))return false;
  UUID challenge=(UUID)row.get("challenge_id"),invite=(UUID)row.get("invitation_id"),gym=(UUID)row.get("gym_id");
  if(challenge!=null)return jdbc.queryForObject("select count(*) from account_challenges c where c.id=? and c.gym_id is not distinct from ?::uuid and c.consumed_at is null and c.expires_at>localtimestamp and (c.user_id is null or exists(select from users u join gyms g on g.id=u.gym_id where u.id=c.user_id and u.gym_id=c.gym_id and u.email=c.email and u.authz_version=c.authz_version and coalesce(u.enabled,true) and g.status='ACTIVE')) and (c.invitation_hash is null or exists(select from gym_invitations i join gyms g on g.id=i.gym_id where i.token_hash=c.invitation_hash and i.gym_id=c.gym_id and i.accepted_at is null and i.revoked_at is null and i.expires_at>localtimestamp and g.status='ACTIVE'))",Long.class,challenge,gym)==1;
  if(invite!=null)return jdbc.queryForObject("select count(*) from gym_invitations i join gyms g on g.id=i.gym_id where i.id=? and i.gym_id=? and i.accepted_at is null and i.revoked_at is null and i.expires_at>localtimestamp and g.status='ACTIVE'",Long.class,invite,gym)==1;
  return "PASSWORD_CHANGED".equals(row.get("kind")) && gym!=null && jdbc.queryForObject("select count(*) from gyms where id=? and status='ACTIVE'",Long.class,gym)==1;
 }
 private void terminal(UUID id,String lease,String status,String code){jdbc.update("update email_outbox set status=?,failure_code=?,payload=null,key_id=null,lease_until=null,lease_token=null,completed_at=localtimestamp where id=? and lease_token=?::uuid",status,code,id,lease);}
 /** Explicit polling entry point; no scheduler is enabled by this component. */
 public boolean processOne(){
  if(!worker)return false;
  Map<String,Object> row=tx.execute(status->{
   jdbc.execute("set local lock_timeout='2s'");jdbc.execute("set local statement_timeout='5s'");
   var rows=jdbc.queryForList("select * from email_outbox where (status='PENDING' and next_attempt_at<=localtimestamp) or (status='PROCESSING' and lease_until<=localtimestamp) order by created_at,id for update skip locked limit 1");
   if(rows.isEmpty())return null;var selected=rows.get(0);UUID id=(UUID)selected.get("id"),lease=UUID.randomUUID();
   if(!valid(selected)||((Number)selected.get("attempts")).intValue()>=5){jdbc.update("update email_outbox set status=?,failure_code=?,payload=null,key_id=null,lease_token=null,lease_until=null,completed_at=localtimestamp where id=?",!valid(selected)?"CANCELLED":"DEAD",!valid(selected)?"EXPIRED_OR_REVOKED":"ATTEMPTS_EXHAUSTED",id);return Map.of();}
   jdbc.update("update email_outbox set status='PROCESSING',attempts=attempts+1,lease_token=?,lease_until=localtimestamp+interval '2 minutes' where id=?",lease,id);
   selected.put("lease_token",lease);selected.put("attempts",((Number)selected.get("attempts")).intValue()+1);return selected;
  });
  if(row==null)return false;if(row.isEmpty())return true;
  UUID id=(UUID)row.get("id");String lease=row.get("lease_token").toString();
  try {
   if(!valid(row)){tx.executeWithoutResult(s->terminal(id,lease,"CANCELLED","EXPIRED_OR_REVOKED"));return true;}
   OutboxCipher.Message message;
   try{message=cipher.open((String)row.get("key_id"),(byte[])row.get("payload"),binding(id,(String)row.get("kind"),row.get("challenge_id"),row.get("invitation_id"),row.get("gym_id")));}
   catch(RuntimeException unavailable){tx.executeWithoutResult(s->terminal(id,lease,"DEAD","PAYLOAD_UNAVAILABLE"));return true;}
   var accepted=provider.accept(id,message);if(accepted==null||accepted.reference()==null||accepted.reference().isBlank())throw new IllegalStateException("No acceptance");
   tx.executeWithoutResult(s->jdbc.update("update email_outbox set status='ACCEPTED',accepted_at=localtimestamp,completed_at=localtimestamp,payload=null,key_id=null,lease_until=null,lease_token=null,failure_code=null where id=? and lease_token=?::uuid",id,lease));
  }catch(EmailProvider.Rejected permanent){tx.executeWithoutResult(s->terminal(id,lease,"DEAD","PROVIDER_REJECTED"));}
  catch(RuntimeException retry){tx.executeWithoutResult(s->{
   int attempts=((Number)row.get("attempts")).intValue();if(attempts>=5){terminal(id,lease,"DEAD","ACCEPTANCE_UNKNOWN");return;}
   int seconds=Math.min(1800,30*(1<<(attempts-1)));
   jdbc.update("update email_outbox set status='PENDING',failure_code='RETRYABLE_OR_UNKNOWN',next_attempt_at=localtimestamp+(? * interval '1 second'),lease_until=null,lease_token=null where id=? and lease_token=?::uuid",seconds,id,lease);
  });}return true;
 }
 public int cleanup(){return tx.execute(s->{
  jdbc.update("update email_outbox set status='CANCELLED',failure_code='EXPIRED',payload=null,key_id=null,lease_token=null,lease_until=null,completed_at=localtimestamp where id in(select id from email_outbox where status in ('PENDING','PROCESSING') and expires_at<=localtimestamp order by created_at for update skip locked limit 100)");
  return jdbc.update("delete from email_outbox where id in(select id from email_outbox where completed_at<localtimestamp-interval '7 days' order by completed_at limit 100)");
 });}
}
