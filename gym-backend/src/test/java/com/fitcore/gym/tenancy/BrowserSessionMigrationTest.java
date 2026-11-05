package com.fitcore.gym.tenancy;

import static org.assertj.core.api.Assertions.*;
import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Only harness-owned disposable PostgreSQL: additive V10 and fail-closed ownership contract. */
@EnabledIfEnvironmentVariable(named="FITCORE_TENANCY_TEST_TOKEN",matches="[a-f0-9]{64}")
class BrowserSessionMigrationTest {
    final FoundationMigrationTest f=new FoundationMigrationTest();
    @BeforeEach void setup()throws Exception{f.isolatedSchema();}
    @AfterEach void cleanup()throws Exception{f.cleanup();}
    @Test void freshSchemaHasDigestOnlySessionStateAndEmptyTables()throws Exception{
        f.migrate("latest");
        assertThat(f.number("select count(*) from browser_sessions")).isZero();
        assertThat(f.number("select count(*) from browser_session_families")).isZero();
        assertThat(f.number("select count(*) from browser_gateway_nonces")).isZero();
        assertThat(f.value("select version from flyway_schema_history order by installed_rank desc limit 1")).isEqualTo("10");
        assertThat(f.number("select count(*) from information_schema.columns where table_schema=current_schema() and table_name in ('browser_sessions','browser_session_families','browser_gateway_nonces') and column_name in ('token','handle','jwt','refresh_token','password')")).isZero();
    }
    @Test void upgradePreservesAllLegacyBusinessRowsAndNeverSeedsSessions()throws Exception{
        f.twoGyms();f.migrate("9");
        String before=f.value("select jsonb_agg(to_jsonb(t) order by id)::text from active_memberships t");
        String bookings=f.value("select jsonb_agg(to_jsonb(t) order by id)::text from trainer_sessions t");
        f.migrate("latest");
        assertThat(f.value("select jsonb_agg(to_jsonb(t) order by id)::text from active_memberships t")).isEqualTo(before);
        assertThat(f.value("select jsonb_agg(to_jsonb(t) order by id)::text from trainer_sessions t")).isEqualTo(bookings);
        assertThat(f.number("select count(*) from browser_sessions")).isZero();
    }
    @Test void crossGymSessionAndMalformedDigestsAreRejectedAtomically()throws Exception{
        f.twoGyms();f.migrate("latest");UUID family=UUID.randomUUID();
        f.sql("insert into browser_session_families(id,created_at,expires_at) values(?,now(),now()+interval '12 hours')",family);
        assertThatThrownBy(()->f.sql("insert into browser_sessions(token_hash,family_id,kind,family_revision,user_id,gym_id,authz_version,credentials_version,csrf_token,created_at,expires_at,absolute_expires_at) values(?,?,'AUTH',0,?,?,0,'version','csrf',now(),now()+interval '30 minutes',now()+interval '12 hours')","a".repeat(64),family,f.user(1),f.gym(2))).isInstanceOf(SQLException.class);
        assertThatThrownBy(()->f.sql("insert into browser_gateway_nonces(nonce_hash,expires_at) values('plaintext',now())")).isInstanceOf(SQLException.class);
        assertThat(f.number("select count(*) from browser_sessions")).isZero();
    }
    @Test void pendingStateCannotBePartialAndSessionCannotChangeGym()throws Exception{
        f.twoGyms();f.migrate("latest");UUID family=UUID.randomUUID();
        f.sql("insert into browser_session_families(id,created_at,expires_at) values(?,now(),now()+interval '12 hours')",family);
        assertThatThrownBy(()->f.sql("update browser_session_families set pending_kind='LOGIN' where id=?",family)).isInstanceOf(SQLException.class);
        f.sql("insert into browser_sessions(token_hash,family_id,kind,family_revision,user_id,gym_id,authz_version,credentials_version,csrf_token,created_at,expires_at,absolute_expires_at) values(?,?,'AUTH',0,?,?,0,'version','csrf',now(),now()+interval '30 minutes',now()+interval '12 hours')","b".repeat(64),family,f.user(1),f.gym(1));
        assertThatThrownBy(()->f.sql("update browser_sessions set gym_id=?,user_id=? where token_hash=?",f.gym(2),f.user(2),"b".repeat(64))).isInstanceOf(SQLException.class);
    }
}
