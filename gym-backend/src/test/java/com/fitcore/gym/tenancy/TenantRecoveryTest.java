package com.fitcore.gym.tenancy;

import static org.assertj.core.api.Assertions.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Actual archives; only this harness's marked source and separately owned targets. */
@EnabledIfEnvironmentVariable(named="FITCORE_TENANCY_TEST_TOKEN",matches="[a-f0-9]{64}")
class TenantRecoveryTest {
    final FoundationMigrationTest fixture=new FoundationMigrationTest();
    String target, targetUrl;
    Path temp;
    static final String USER="fitcore_phase2_test", PASSWORD="fitcore-disposable-phase2-only";

    @BeforeEach void prepare() throws Exception {
        fixture.isolatedSchema(); temp=Files.createTempDirectory("fitcore-synthetic-recovery-");
    }
    @AfterEach void cleanup() throws Exception {
        try { if(target!=null) command("docker","rm","-f",target); }
        finally { try { fixture.cleanup(); } finally {
            if(temp!=null) try(var paths=Files.walk(temp)) { for(Path p:paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); }
        } }
    }
    String command(String...args) throws Exception {
        var process=new ProcessBuilder(args).redirectErrorStream(true).start();
        if(!process.waitFor(60,TimeUnit.SECONDS)){process.destroyForcibly();throw new IllegalStateException("Owned recovery command timed out");}
        String output=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        if(process.exitValue()!=0)throw new IllegalStateException("Owned recovery command failed: "+args[0]);
        return output.trim();
    }
    void freshTarget() throws Exception {
        target=command("docker","run","--detach","--rm","--label","fitcore.purpose=synthetic-recovery","--publish","127.0.0.1::5432","--env","POSTGRES_DB=fitcore_phase2","--env","POSTGRES_USER="+USER,"--env","POSTGRES_PASSWORD="+PASSWORD,System.getenv().getOrDefault("FITCORE_POSTGRES_TEST_IMAGE","postgres:18-alpine"));
        if(!target.matches("[a-f0-9]{64}"))throw new IllegalStateException("Invalid owned container identifier");
        boolean ready=false;
        for(int n=0;n<60;n++){try{command("docker","exec",target,"pg_isready","-h","127.0.0.1","-U",USER,"-d","fitcore_phase2");ready=true;break;}catch(IllegalStateException unavailable){Thread.sleep(200);}}
        if(!ready)throw new IllegalStateException("Owned recovery database not ready");
        String binding=command("docker","port",target,"5432/tcp");
        if(!binding.matches("127\\.0\\.0\\.1:[0-9]+"))throw new IllegalStateException("Recovery binding must be loopback only");
        targetUrl="jdbc:postgresql://"+binding+"/fitcore_phase2";
        try(var c=DriverManager.getConnection(targetUrl,USER,PASSWORD);var s=c.createStatement()){
            s.execute("COMMENT ON DATABASE fitcore_phase2 IS 'fitcore-disposable:"+target+"'");
        }
    }
    Connection restoredConnection() throws Exception {
        var c=DriverManager.getConnection(targetUrl,USER,PASSWORD);
        try(var s=c.createStatement();var r=s.executeQuery("select shobj_description(oid,'pg_database') from pg_database where datname=current_database()")){
            if(!r.next()||!Objects.equals(r.getString(1),"fitcore-disposable:"+target)){c.close();throw new IllegalStateException("Recovery marker mismatch");}
        }
        c.setSchema(fixture.schema);return c;
    }
    Path backup() throws Exception {
        // A live JDBC check verifies source URL/ownership/marker before the container command.
        try(var ignored=fixture.connection()){}
        Path dump=temp.resolve("synthetic.dump"), error=temp.resolve("dump.err");
        var process=new ProcessBuilder("docker","exec",System.getenv("FITCORE_TENANCY_TEST_TOKEN"),"pg_dump","-U",USER,"-d","fitcore_phase2","--format=custom","--schema="+fixture.schema,"--no-owner","--no-privileges")
            .redirectOutput(dump.toFile()).redirectError(error.toFile()).start();
        if(!process.waitFor(60,TimeUnit.SECONDS)){process.destroyForcibly();throw new IllegalStateException("Backup timed out");}
        assertThat(process.exitValue()).isZero();assertThat(Files.size(dump)).isGreaterThan(0);
        return dump;
    }
    void restore(Path dump) throws Exception {
        try(var ignored=restoredConnection()){}
        Path error=temp.resolve("restore.err");
        var process=new ProcessBuilder("docker","exec","-i",target,"pg_restore","-U",USER,"-d","fitcore_phase2","--single-transaction","--exit-on-error","--no-owner","--no-privileges")
            .redirectInput(dump.toFile()).redirectOutput(error.toFile()).redirectErrorStream(true).start();
        if(!process.waitFor(60,TimeUnit.SECONDS)){process.destroyForcibly();throw new IllegalStateException("Restore timed out");}
        if(process.exitValue()!=0)throw new IllegalStateException("Archive restoration rejected");
    }
    Map<String,String> fingerprint(Connection c) throws Exception {
        var result=new TreeMap<String,String>();var tables=new ArrayList<String>();
        try(var p=c.prepareStatement("select table_name from information_schema.tables where table_schema=? and table_type='BASE TABLE' order by table_name")){
            p.setString(1,fixture.schema);try(var r=p.executeQuery()){while(r.next())tables.add(r.getString(1));}
        }
        for(String table:tables)try(var s=c.createStatement();var r=s.executeQuery("select count(*)||':'||md5(coalesce(string_agg(row_to_json(t)::text,'|' order by row_to_json(t)::text),'')) from "+fixture.schema+".\""+table+"\" t")){r.next();result.put(table,r.getString(1));}
        var sequences=new ArrayList<String>();try(var p=c.prepareStatement("select sequencename from pg_sequences where schemaname=?")){p.setString(1,fixture.schema);try(var r=p.executeQuery()){while(r.next())sequences.add(r.getString(1));}}
        for(String sequence:sequences)try(var s=c.createStatement();var r=s.executeQuery("select last_value||':'||is_called from "+fixture.schema+".\""+sequence+"\"")){r.next();result.put("sequence:"+sequence,r.getString(1));}
        return result;
    }
    long count(Connection c,String sql) throws Exception {try(var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getLong(1);}}
    @ParameterizedTest @ValueSource(ints={2,4,5,6})
    void restoredArchiveReconcilesEveryRowAndSequenceAndSupportsTenantCandidate(int stage) throws Exception {
        fixture.legacyRecords();
        if(stage>=4){try(var c=fixture.connection()){c.setAutoCommit(false);fixture.stageExplicitSyntheticMapping(c,false);c.commit();}fixture.migrate(Integer.toString(stage));}
        Map<String,String> original;try(var c=fixture.connection()){original=fingerprint(c);}
        Path archive=backup();freshTarget();restore(archive);
        try(var c=restoredConnection()){
            assertThat(fingerprint(c)).isEqualTo(original);
            if(stage==2){c.setAutoCommit(false);fixture.stageExplicitSyntheticMapping(c,false);c.commit();}
        }
        Flyway.configure().dataSource(targetUrl,USER,PASSWORD).defaultSchema(fixture.schema).schemas(fixture.schema).baselineOnMigrate(false).cleanDisabled(true).load().migrate();
        try(var c=restoredConnection()){
            assertThat(com.fitcore.gym.operations.ReleaseReadiness.schemaReady(c)).isFalse();
            assertThat(count(c,"select count(*) from users where password_hash='preserved-hash' and created_at=timestamp '2025-01-01'")).isEqualTo(4);
            assertThat(count(c,"select count(*) from users where role='ADMIN' and enabled=false")).isEqualTo(1);
            assertThat(count(c,"select count(*) from active_memberships")).isEqualTo(3);
            assertThat(count(c,"select count(*) from active_memberships where user_id='"+fixture.user(1)+"' and status='ACTIVE'")).isEqualTo(2);
            assertThat(count(c,"select count(*) from trainer_sessions where id=1 and trainer_id=1")).isEqualTo(1);
            assertThat(count(c,"select count(*) from membership_plans where name='Legacy Premium' and price=79")).isEqualTo(2);
            assertThat(count(c,"select count(*) from flyway_schema_history where version='6' and success")).isEqualTo(1);
            try(var s=c.createStatement()){
                s.execute("create role recovery_runtime login password 'synthetic-runtime-only' nosuperuser nocreatedb nocreaterole noinherit");
                s.execute("grant usage on schema "+fixture.schema+" to recovery_runtime");
                s.execute("grant select on "+fixture.schema+".membership_plans to recovery_runtime");
                s.execute("grant select on "+fixture.schema+".flyway_schema_history to recovery_runtime");
            }
        }
        // --no-privileges makes role recreation portable but also omits REVOKE.
        // Prove the restricted helper is exposed, then restore the reviewed ACL explicitly.
        try(var c=DriverManager.getConnection(targetUrl,"recovery_runtime","synthetic-runtime-only")){
            c.setSchema(fixture.schema);
            assertThat(count(c,"select has_function_privilege(current_user,'fitcore_scope_catalogs()','EXECUTE')::int")).isEqualTo(1);
            assertThat(com.fitcore.gym.operations.ReleaseReadiness.schemaReady(c)).isFalse();
        }
        try(var c=restoredConnection();var statement=c.createStatement()){
            statement.execute("revoke all on function fitcore_scope_catalogs() from public");
        }
        try(var c=DriverManager.getConnection(targetUrl,"recovery_runtime","synthetic-runtime-only")){
            c.setSchema(fixture.schema);
            assertThat(com.fitcore.gym.operations.ReleaseReadiness.schemaReady(c)).as("Missing business privileges must not report READY").isFalse();
        }
        try(var c=restoredConnection();var statement=c.createStatement()){
            for(String table:List.of("account_challenges","security_rate_limits","gyms","gym_invitations","users","active_memberships","attendance","exercises","food_logs","membership_plans","membership_plan_features","notifications","nutrition_goals","nutrition_plans","nutrition_plan_meals","progress_metrics","trainer_profiles","trainer_sessions","trainer_available_slots","trainer_certifications","workout_logs","workout_exercise_logs","workout_plans","workout_plan_exercises","workout_plan_schedule"))
                statement.execute("grant select,insert,update,delete on "+fixture.schema+"."+table+" to recovery_runtime");
            statement.execute("grant usage,select on all sequences in schema "+fixture.schema+" to recovery_runtime");
        }
        try(var c=DriverManager.getConnection(targetUrl,"recovery_runtime","synthetic-runtime-only")){
            c.setSchema(fixture.schema);
            assertThat(com.fitcore.gym.operations.ReleaseReadiness.schemaReady(c)).isTrue();
            for(int n=1;n<=2;n++)assertThat(count(c,"select count(*) from membership_plans where gym_id='"+fixture.gym(n)+"'")).isEqualTo(1);
            for(String forbidden:List.of("alter table users add column forbidden int","truncate users","create table forbidden(id int)","select fitcore_scope_catalogs()","update flyway_schema_history set checksum=0"))
                assertThatThrownBy(()->{try(var s=c.createStatement()){s.execute(forbidden);}}).isInstanceOf(SQLException.class);
        }
        for(String drift:List.of(
                "alter table active_memberships drop constraint fk_active_memberships_plan_id_gym",
                "alter table users disable trigger immutable_gym",
                "alter table users disable trigger preserve_primary_owner",
                "alter table users alter column gym_id drop not null",
                "revoke usage on all sequences in schema "+fixture.schema+" from recovery_runtime",
                "grant create on schema "+fixture.schema+" to recovery_runtime")) {
            try(var c=restoredConnection();var statement=c.createStatement()) {
                c.setAutoCommit(false);statement.execute(drift);statement.execute("set local role recovery_runtime");
                assertThat(com.fitcore.gym.operations.ReleaseReadiness.schemaReady(c)).as("Reject drift: "+drift).isFalse();
                c.rollback();
            }
        }
    }
    @Test void invalidArchiveCannotCreatePartialRestoredSchema() throws Exception {
        fixture.legacyRecords();freshTarget();Path corrupt=temp.resolve("corrupt.dump");Files.writeString(corrupt,"not-a-postgresql-archive");
        assertThatThrownBy(()->restore(corrupt)).hasMessage("Archive restoration rejected");
        try(var c=restoredConnection()) {assertThat(count(c,"select count(*) from information_schema.tables where table_schema='"+fixture.schema+"'")).isZero();}
    }
}
