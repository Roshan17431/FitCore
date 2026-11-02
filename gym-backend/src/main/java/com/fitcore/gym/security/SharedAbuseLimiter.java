package com.fitcore.gym.security;

import com.fitcore.gym.service.AccountSecurityService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL is the shared store. There is no process fallback on PostgreSQL failure. */
@Component
public class SharedAbuseLimiter {
    private final JdbcTemplate jdbc;private final TransactionTemplate tx;
    public SharedAbuseLimiter(JdbcTemplate jdbc,PlatformTransactionManager manager){this.jdbc=jdbc;tx=new TransactionTemplate(manager);tx.setPropagationBehavior(3);}
    public boolean postgres(){return Boolean.TRUE.equals(jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>)c->"PostgreSQL".equals(c.getMetaData().getDatabaseProductName())));}
    public boolean allow(String action,String peer){return Boolean.TRUE.equals(tx.execute(status->{
        jdbc.execute("set local lock_timeout='2s'");jdbc.execute("set local statement_timeout='3s'");
        jdbc.update("delete from security_rate_limits where scope_hash in (select scope_hash from security_rate_limits where expires_at<current_timestamp limit 100)");
        if(jdbc.queryForObject("select count(*) from security_rate_limits",Long.class)>10000)return false;
        boolean allowed=increment("global:"+action,500) && increment("peer:"+action+":"+peer,50);
        if(!allowed)status.setRollbackOnly(); // Rejected peers cannot drain the accepted-work budget.
        return allowed;
    }));}
    private boolean increment(String scope,int max){
        var counts=jdbc.queryForList("insert into security_rate_limits(scope_hash,bucket,attempts,expires_at) values(?,floor(extract(epoch from current_timestamp)/300)::bigint,1,current_timestamp+interval '10 minutes') on conflict(scope_hash) do update set attempts=case when security_rate_limits.bucket=excluded.bucket then security_rate_limits.attempts+1 else 1 end,bucket=excluded.bucket,expires_at=excluded.expires_at where security_rate_limits.bucket<>excluded.bucket or security_rate_limits.attempts<? returning attempts",AccountSecurityService.digest(scope),max);
        return !counts.isEmpty();
    }
}
