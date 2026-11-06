package com.fitcore.gym.tenancy;

import static org.assertj.core.api.Assertions.*;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import com.fitcore.gym.service.AccountSecurityService;

/** Shared admission and bounded cleanup on marked, synthetic PostgreSQL only. */
class BrowserIngressMaintenanceTest extends BrowserSessionSecurityTest {
    @Autowired ApplicationContext application;
    @Autowired org.springframework.transaction.PlatformTransactionManager manager;
    @BeforeEach void isolateRateBuckets(){ jdbc.update("delete from security_rate_limits"); }
    void fillPeer(String path){
        jdbc.update("insert into security_rate_limits(scope_hash,bucket,attempts,expires_at) values(?,floor(extract(epoch from current_timestamp)/300)::bigint,50,now()+interval '10 minutes')",AccountSecurityService.digest("peer:"+path+":127.0.0.1"));
    }
    @Test void bootstrapUsesSharedPeerLimitAndWritesNoCookieOnRejection()throws Exception{
        fillPeer("/api/browser/bootstrap");long rows=familyRows();
        var response=send(new Jar(),"POST","/api/browser/bootstrap",Map.of(),false);
        assertThat(response.getStatus()).isEqualTo(429);assertThat(response.getContentAsString()).contains("RATE_LIMITED");
        assertThat(response.getHeaders("Set-Cookie")).isEmpty();assertThat(familyRows()).isEqualTo(rows);
    }
    @Test void wrongPasswordsConsumeIngressBudgetDespiteHandlerRollback()throws Exception{
        Jar jar=bootstrap();
        jdbc.update("insert into security_rate_limits(scope_hash,bucket,attempts,expires_at) values(?,floor(extract(epoch from current_timestamp)/300)::bigint,49,now()+interval '10 minutes')",AccountSecurityService.digest("peer:/api/browser/login:127.0.0.1"));
        assertThat(send(jar,"POST","/api/browser/login",Map.of("email",a.email(),"password","wrong"),true).getStatus()).isEqualTo(401);
        var denied=login(jar,a);assertThat(denied.getStatus()).isEqualTo(429);assertThat(denied.getHeaders("Set-Cookie")).isEmpty();
        assertThat(send(jar,"GET","/api/browser/state",null,false).getContentAsString()).contains("IDLE");
    }
    @Test void sharedStoreFailureFailsClosedWithoutCookieOrFamily()throws Exception{
        long before=familyRows();f.sql("alter table security_rate_limits rename to security_rate_limits_fixture_unavailable");
        try{
            var r=send(new Jar(),"POST","/api/browser/bootstrap",Map.of(),false);
            assertThat(r.getStatus()).isEqualTo(503);assertThat(r.getHeaders("Set-Cookie")).isEmpty();assertThat(familyRows()).isEqualTo(before);
        }finally{f.sql("alter table security_rate_limits_fixture_unavailable rename to security_rate_limits");}
    }
    @Test void nonceCleanupIsBoundedAndNeverRemovesLiveEvidence()throws Exception{
        jdbc.update("delete from browser_gateway_nonces");
        jdbc.update("insert into browser_gateway_nonces(nonce_hash,expires_at) select md5('expired-'||i)||md5('nonce-'||i),now()-interval '1 minute' from generate_series(1,250) i");
        String live=hash("live-nonce");jdbc.update("insert into browser_gateway_nonces values(?,now()+interval '2 minutes')",live);
        assertThat(send(new Jar(),"GET","/api/browser/state",null,false).getStatus()).isEqualTo(401);
        assertThat(jdbc.queryForObject("select count(*) from browser_gateway_nonces where expires_at<now()",Integer.class)).isEqualTo(150);
        assertThat(jdbc.queryForObject("select count(*) from browser_gateway_nonces where nonce_hash=?",Integer.class,live)).isEqualTo(1);
    }
    @Test void lockedExpiredNonceDoesNotBlockOtherIngress()throws Exception{
        String locked=hash(UUID.randomUUID().toString());jdbc.update("insert into browser_gateway_nonces values(?,now()-interval '1 minute')",locked);
        try(Connection c=f.connection()){
            c.setAutoCommit(false);FoundationMigrationTest.execute(c,"select nonce_hash from browser_gateway_nonces where nonce_hash=? for update",locked);
            var pool=Executors.newSingleThreadExecutor();
            try{ assertThat(pool.submit(()->send(new Jar(),"GET","/api/browser/state",null,false).getStatus()).get(5,TimeUnit.SECONDS)).isEqualTo(401); }
            finally{ c.rollback();pool.shutdownNow(); }
        }
        assertThat(jdbc.queryForObject("select count(*) from browser_gateway_nonces where nonce_hash=?",Integer.class,locked)).isEqualTo(1);
    }
    @Test void nonceEvidenceSurvivesAnEnclosingTransactionRollback()throws Exception{
        Class<?> type=Class.forName("com.fitcore.gym.security.browser.BrowserSecurityMaintenance");Object bean=application.getBean(type);String hash=hash(UUID.randomUUID().toString());
        new org.springframework.transaction.support.TransactionTemplate(manager).execute(status->{
            try{assertThat(type.getMethod("consumeNonce",String.class,java.time.Instant.class).invoke(bean,hash,java.time.Instant.now().plusSeconds(120))).isEqualTo(true);}
            catch(ReflectiveOperationException e){throw new RuntimeException(e);}status.setRollbackOnly();return null;
        });
        assertThat(jdbc.queryForObject("select count(*) from browser_gateway_nonces where nonce_hash=?",Integer.class,hash)).isEqualTo(1);
    }
    @Test void nonceStoreOutageFailsClosedWithoutAHandlerOrCookie()throws Exception{
        long before=familyRows();f.sql("alter table browser_gateway_nonces rename to browser_gateway_nonces_fixture_unavailable");
        try{var r=send(new Jar(),"POST","/api/browser/bootstrap",Map.of(),false);assertThat(r.getStatus()).isEqualTo(503);assertThat(r.getHeaders("Set-Cookie")).isEmpty();assertThat(familyRows()).isEqualTo(before);}
        finally{f.sql("alter table browser_gateway_nonces_fixture_unavailable rename to browser_gateway_nonces");}
    }
    int clean()throws Exception{
        Class<?> type=Class.forName("com.fitcore.gym.security.browser.BrowserSecurityMaintenance");
        return (Integer)type.getMethod("cleanupRetiredSessions").invoke(application.getBean(type));
    }
    @Test void retiredCleanupPreservesPendingCurrentAndExpiredControlProof()throws Exception{
        Jar idle=authenticated(a),pending=bootstrap();login(pending,b);
        String current=hash(idle.values.get("__Host-fitcore_session"));
        jdbc.update("update browser_sessions set created_at=now()-interval '2 hours',expires_at=now()-interval '1 hour' where token_hash=?",current);
        var protectedRows=jdbc.queryForList("select s.token_hash from browser_sessions s join browser_session_families f on f.id=s.family_id where f.pending_hash is not null or s.family_revision=f.revision");
        int removed=clean();assertThat(removed).isPositive().isLessThanOrEqualTo(100);
        for(var row:protectedRows)assertThat(jdbc.queryForObject("select count(*) from browser_sessions where token_hash=?",Integer.class,row.get("token_hash"))).isEqualTo(1);
        assertThat(send(idle,"POST","/api/browser/logout",Map.of(),true).getStatus()).isEqualTo(200);
        assertThat(send(pending,"POST","/api/browser/logout",Map.of(),true).getStatus()).isEqualTo(423);
    }
    @Test void cleanupSkipsFamilyHeldByAnotherTransaction()throws Exception{
        Jar jar=authenticated(a);UUID family=jdbc.queryForObject("select family_id from browser_sessions where token_hash=?",UUID.class,hash(jar.values.get("__Host-fitcore_session")));
        long before=jdbc.queryForObject("select count(*) from browser_sessions where family_id=?",Long.class,family);
        try(Connection c=f.connection()){
            c.setAutoCommit(false);FoundationMigrationTest.execute(c,"select id from browser_session_families where id=? for update",family);
            clean();assertThat(jdbc.queryForObject("select count(*) from browser_sessions where family_id=?",Long.class,family)).isEqualTo(before);c.rollback();
        }
        clean();assertThat(jdbc.queryForObject("select count(*) from browser_sessions where family_id=?",Long.class,family)).isEqualTo(1);
    }
}
