package com.fitcore.gym.security.browser;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Bounded shared-store operations. No scheduler or externally callable cleanup route. */
@Service
@ConditionalOnProperty(name="fitcore.browser.spike-enabled",havingValue="true")
public class BrowserSecurityMaintenance {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    public BrowserSecurityMaintenance(JdbcTemplate jdbc,PlatformTransactionManager manager){
        this.jdbc=jdbc;tx=new TransactionTemplate(manager);tx.setPropagationBehavior(3); // Independent of handler rollback.
    }
    private void deadlines(){jdbc.execute("set local lock_timeout='2s'");jdbc.execute("set local statement_timeout='3s'");}
    public boolean consumeNonce(String hash,Instant expires){
        return Boolean.TRUE.equals(tx.execute(status->{
            deadlines();
            jdbc.update("""
                delete from browser_gateway_nonces where nonce_hash in
                (select nonce_hash from browser_gateway_nonces where expires_at<now()
                 order by expires_at,nonce_hash limit 100 for update skip locked)
                """);
            return jdbc.update("insert into browser_gateway_nonces(nonce_hash,expires_at) values(?,?) on conflict do nothing",hash,Timestamp.from(expires))==1;
        }));
    }
    public int cleanupRetiredSessions(){
        Integer removed=tx.execute(status->{
            deadlines();
            // Lock the same family rows as login/logout/ACK. Never remove current,
            // expired-current or pending proof, even after its user is revoked.
            int count=jdbc.update("""
                with families as (
                  select f.id,f.revision from browser_session_families f
                  where f.pending_hash is null and exists
                    (select 1 from browser_sessions s where s.family_id=f.id and s.used and s.family_revision<f.revision)
                  order by f.id limit 100 for update of f skip locked
                ), retired as (
                  select s.token_hash from browser_sessions s join families f on f.id=s.family_id
                  where s.used and s.family_revision<f.revision
                  order by s.token_hash limit 100 for update of s skip locked
                ) delete from browser_sessions where token_hash in (select token_hash from retired)
                """);
            jdbc.update("""
                with empty as (
                  select f.id from browser_session_families f where f.pending_hash is null
                  and not exists(select 1 from browser_sessions s where s.family_id=f.id)
                  order by f.id limit 100 for update of f skip locked
                ) delete from browser_session_families where id in (select id from empty)
                """);
            return count;
        });
        return removed==null?0:removed;
    }
}
