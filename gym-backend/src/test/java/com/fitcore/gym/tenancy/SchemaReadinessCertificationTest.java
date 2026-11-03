package com.fitcore.gym.tenancy;

import static org.assertj.core.api.Assertions.*;
import com.fitcore.gym.operations.*;
import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

/** Certification remains restricted to actual independently owned disposable containers. */
@EnabledIfEnvironmentVariable(named="FITCORE_TENANCY_TEST_TOKEN",matches="[a-f0-9]{64}")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SchemaReadinessCertificationTest {
    final CleanInstanceRecoveryTest clean=new CleanInstanceRecoveryTest();
    final TenantRecoveryTest reference=new TenantRecoveryTest();
    String restoreHelperSchema;
    static final String RUNTIME="certification_runtime";
    static final JsonMapper JSON=JsonMapper.builder().build();
    @BeforeAll void prepare()throws Exception {
        clean.prepare();reference.prepare();clean.seed();clean.restored.freshTarget();
        clean.restored.restore(clean.dump());restoreHelperSchema=clean.restored.fixture.schema;
        clean.restored.fixture.schema=clean.source.fixture.schema;
        reference.restoredArchiveReconcilesEveryRowAndSequenceAndSupportsTenantCandidate(6);
        for(var db:List.of(clean.source,clean.restored,reference))restrictedRole(db);
    }
    @AfterAll void cleanup()throws Exception {
        try{reference.cleanup();}finally{
            if(restoreHelperSchema!=null)clean.restored.fixture.schema=restoreHelperSchema;
            clean.cleanup();
        }
    }
    void restrictedRole(TenantRecoveryTest db)throws Exception {
        try(var c=db.restoredConnection();var s=c.createStatement()){
            s.execute("create role "+RUNTIME+" login password 'synthetic-runtime-only' nosuperuser nocreatedb nocreaterole nobypassrls noinherit");
            s.execute("grant usage on schema "+db.fixture.schema+" to "+RUNTIME);
            s.execute("grant select,insert,update,delete on all tables in schema "+db.fixture.schema+" to "+RUNTIME);
            s.execute("revoke all on flyway_schema_history from "+RUNTIME);s.execute("grant select on flyway_schema_history to "+RUNTIME);
            s.execute("grant usage,select on all sequences in schema "+db.fixture.schema+" to "+RUNTIME);
            s.execute("revoke all on function fitcore_scope_catalogs() from public");
        }
    }
    Connection runtime(TenantRecoveryTest db)throws Exception {
        // Verify the owned endpoint and marker as administrator before runtime connection.
        try(var ignored=db.restoredConnection()){}
        var c=DriverManager.getConnection(db.targetUrl,RUNTIME,"synthetic-runtime-only");c.setSchema(db.fixture.schema);return c;
    }
    Map<String,String> snapshot(TenantRecoveryTest db)throws Exception {try(var c=db.restoredConnection()){return SchemaDescriptorSnapshot.collect(c);}}
    Map<String,Object> differences(Map<String,String> a,Map<String,String> b){
        var diff=new TreeMap<String,Object>();var keys=new TreeSet<>(a.keySet());keys.addAll(b.keySet());
        for(String k:keys)if(!Objects.equals(a.get(k),b.get(k)))diff.put(k,Map.of("left",Objects.toString(a.get(k)),"right",Objects.toString(b.get(k))));return diff;
    }
    static final java.util.regex.Pattern ARRAY_CAST=java.util.regex.Pattern.compile("\\(ARRAY\\[((?:'[A-Z_]+'::character varying)(?:, '[A-Z_]+'::character varying)*)\\]\\)::text\\[\\]");
    static final Set<String> ENUM_CHECKS=Set.of(
        "constraint:gyms:gyms_status_check","constraint:users:users_role_check","constraint:gym_invitations:gym_invitations_role_check",
        "constraint:account_challenges:account_challenges_check","constraint:account_challenges:account_challenges_check1","constraint:account_challenges:account_challenges_purpose_check",
        "constraint:email_outbox:email_outbox_check1","constraint:email_outbox:email_outbox_kind_check","constraint:email_outbox:email_outbox_status_check");
    static String elementCasts(String values){return values.replaceAll("('[A-Z_]+'::character varying)","($1)::text");}
    static String restoredRendering(String sql){
        var m=ARRAY_CAST.matcher(sql);var out=new StringBuffer();
        while(m.find())m.appendReplacement(out,java.util.regex.Matcher.quoteReplacement("ARRAY["+elementCasts(m.group(1))+"]"));
        m.appendTail(out);return out.toString();
    }
    String equivalentRendering(String key,String encoded){
        Map<String,Object> row=JSON.readValue(encoded,Map.class);
        if(ENUM_CHECKS.contains(key)||key.equals("index:email_outbox:ix_outbox_due"))row.put("definition",restoredRendering((String)row.get("definition")));
        if(Set.of("function:fitcore_assert_owner:gid uuid","function:fitcore_check_owner:","function:fitcore_scope_catalogs:").contains(key)){
            row.put("proconfig",((List<String>)row.get("proconfig")).stream().map(v->v.replace("\"fitcore_schema\"","fitcore_schema").replace(", ",",")).toList());
        }
        return JSON.writeValueAsString(row);
    }
    @Test void completeDescriptorsHaveOnlyReviewedEnumCastRenderings()throws Exception {
        var fresh=snapshot(clean.source);var restored=snapshot(clean.restored);var accepted=snapshot(reference);
        for(var inventory:List.of(fresh,restored,accepted)){
            assertThat(inventory.keySet().stream().filter(k->k.startsWith("sequence_owner:")).count()).isEqualTo(14);
            assertThat(inventory.keySet().stream().filter(k->k.startsWith("policy:")).count()).isZero();
        }
        var changes=new LinkedHashMap<String,Object>();changes.put("freshVsRestored",differences(fresh,restored));changes.put("freshVsReference",differences(fresh,accepted));changes.put("restoredVsReference",differences(restored,accepted));
        var evidence=new LinkedHashMap<String,Object>();evidence.put("fresh",fresh);evidence.put("restored",restored);evidence.put("acceptedReference",accepted);evidence.put("differences",changes);
        java.nio.file.Path evidenceFile=java.nio.file.Path.of("target","schema-certification-evidence.json");
        java.nio.file.Files.createDirectories(evidenceFile.getParent());java.nio.file.Files.writeString(evidenceFile,JSON.writeValueAsString(evidence));
        System.out.println("Schema certification: "+fresh.size()+" structural records per path; evidence in target/schema-certification-evidence.json");
        var expected=new TreeSet<>(ENUM_CHECKS);expected.addAll(List.of("index:email_outbox:ix_outbox_due","function:fitcore_assert_owner:gid uuid","function:fitcore_check_owner:","function:fitcore_scope_catalogs:"));
        assertThat(differences(fresh,restored).keySet()).containsExactlyInAnyOrderElementsOf(expected);
        for(var pair:List.of(List.of(fresh,restored),List.of(fresh,accepted),List.of(restored,accepted))){
            var left=pair.get(0);var right=pair.get(1);assertThat(left.keySet()).isEqualTo(right.keySet());
            for(String key:left.keySet())assertThat(equivalentRendering(key,left.get(key))).as("No unexplained metadata difference: %s",key).isEqualTo(equivalentRendering(key,right.get(key)));
        }
        for(var db:List.of(clean.source,clean.restored,reference)){
            try(var c=db.restoredConnection()) {assertThat(c.getMetaData().getDatabaseMajorVersion()).isEqualTo(18);}
            Flyway.configure().dataSource(db.targetUrl,TenantRecoveryTest.USER,TenantRecoveryTest.PASSWORD).defaultSchema(db.fixture.schema).schemas(db.fixture.schema).baselineOnMigrate(false).cleanDisabled(true).load().validate();
        }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void certifiedSchemaPassesWithRestrictedLogin(boolean restored)throws Exception {
        try(var c=runtime(restored?clean.restored:clean.source)){
            try(var statement=c.createStatement()){
                try(var result=statement.executeQuery("select not (rolsuper or rolcreatedb or rolcreaterole or rolbypassrls or rolinherit) from pg_roles where rolname=current_user")){result.next();assertThat(result.getBoolean(1)).isTrue();}
                try(var result=statement.executeQuery("select count(*) from pg_class t join pg_namespace n on n.oid=t.relnamespace where n.nspname=current_schema() and pg_has_role(current_user,t.relowner,'MEMBER')")){result.next();assertThat(result.getInt(1)).isZero();}
                try(var result=statement.executeQuery("select count(*),bool_and(has_table_privilege(current_user,table_schema||'.'||table_name,'SELECT') and has_table_privilege(current_user,table_schema||'.'||table_name,'INSERT') and has_table_privilege(current_user,table_schema||'.'||table_name,'UPDATE') and has_table_privilege(current_user,table_schema||'.'||table_name,'DELETE') and not has_table_privilege(current_user,table_schema||'.'||table_name,'TRUNCATE,REFERENCES,TRIGGER')) from information_schema.tables where table_schema=current_schema() and table_name<>'flyway_schema_history'")){result.next();assertThat(result.getInt(1)).isEqualTo(26);assertThat(result.getBoolean(2)).isTrue();}
                try(var result=statement.executeQuery("select not has_schema_privilege(current_user,current_schema(),'CREATE') and not has_function_privilege(current_user,'fitcore_scope_catalogs()','EXECUTE') and has_table_privilege(current_user,'flyway_schema_history','SELECT') and not has_table_privilege(current_user,'flyway_schema_history','INSERT,UPDATE,DELETE,TRUNCATE')")){result.next();assertThat(result.getBoolean(1)).isTrue();}
                try(var result=statement.executeQuery("select bool_and(has_sequence_privilege(current_user,schemaname||'.'||sequencename,'USAGE') and has_sequence_privilege(current_user,schemaname||'.'||sequencename,'SELECT') and not has_sequence_privilege(current_user,schemaname||'.'||sequencename,'UPDATE')) from pg_sequences where schemaname=current_schema()")){result.next();assertThat(result.getBoolean(1)).isTrue();}
            }
            assertThat(ReleaseReadiness.schemaReady(c)).as("Restricted fresh/restored readiness; hash=%s",SchemaContract.fingerprint(c)).isTrue();
        }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void schemaAndPrivilegeDriftRemainsRejected(boolean restored)throws Exception {
        var db=restored?clean.restored:clean.source;
        try(var c=runtime(db)){assertThat(ReleaseReadiness.schemaReady(c)).isTrue();}
        var drifts=List.of(
            "create table unexpected(id int)",
            "alter table users add column unexpected int",
            "alter table active_memberships drop constraint fk_active_memberships_plan_id_gym",
            "alter table users drop constraint users_role_check; alter table users add constraint users_role_check check(role in ('MEMBER','TRAINER','ADMIN','OWNER','PLATFORM'))",
            "drop index ix_outbox_due; create index ix_outbox_due on email_outbox(next_attempt_at,created_at) where status='PENDING'",
            "alter table email_outbox drop constraint email_outbox_kind_check; alter table email_outbox add constraint email_outbox_kind_check check(kind in ('OWNER','VERIFY','MEMBER_INVITE','TRAINER_INVITE','RESET','PASSWORD_CHANGED','OTHER'))",
            "alter table users disable trigger immutable_gym",
            "alter table users disable trigger preserve_primary_owner",
            "alter table gyms disable trigger valid_primary_owner",
            "alter table users alter column gym_id drop not null",
            "update flyway_schema_history set version='99' where version='8'",
            "update flyway_schema_history set success=false where version='8'",
            "grant create on schema "+db.fixture.schema+" to "+RUNTIME,
            "grant update on flyway_schema_history to "+RUNTIME,
            "grant execute on function fitcore_scope_catalogs() to "+RUNTIME,
            "revoke usage on all sequences in schema "+db.fixture.schema+" from "+RUNTIME,
            "alter role "+RUNTIME+" superuser",
            "grant "+TenantRecoveryTest.USER+" to "+RUNTIME);
        for(String drift:drifts)try(var c=db.restoredConnection();var s=c.createStatement()){
            c.setAutoCommit(false);s.execute(drift);s.execute("set local role "+RUNTIME);
            assertThat(ReleaseReadiness.schemaReady(c)).as("Reject %s on restored=%s",drift,restored).isFalse();c.rollback();
        }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void enumAndForeignReferencesAreActuallyEnforced(boolean restored)throws Exception {
        var db=restored?clean.restored:clean.source;
        for(String sql:List.of(
            "update gyms set status='UNKNOWN' where id='"+clean.source.fixture.gym(1)+"'",
            "update users set role='PLATFORM' where id='"+clean.source.fixture.user(3)+"'",
            "insert into gym_invitations(id,gym_id,email,token_hash,role,created_by_user_id,expires_at,created_at) values('30000000-0000-0000-0000-000000000001','"+clean.source.fixture.gym(1)+"','invalid@fixture.invalid','synthetic-invalid-hash','OWNER','"+clean.source.fixture.user(1)+"',now()+interval '1 day',now())",
            "update email_outbox set kind='OTHER'",
            "update email_outbox set status='UNKNOWN'",
            "update active_memberships set plan_id=2 where id=1")){
            try(var c=runtime(db);var s=c.createStatement()){
                c.setAutoCommit(false);assertThatThrownBy(()->s.execute(sql)).isInstanceOf(SQLException.class).satisfies(e->assertThat(((SQLException)e).getSQLState()).startsWith("23"));c.rollback();
            }
        }
        try(var c=runtime(db)){assertThat(ReleaseReadiness.schemaReady(c)).isTrue();}
    }
    @Test void castRenderingsHaveIdenticalThreeValuedSemanticsAndSearchPaths()throws Exception {
        Set<String> arrays=new TreeSet<>();
        for(String row:snapshot(clean.source).values()){
            Map<String,Object> value=JSON.readValue(row,Map.class);Object def=value.get("definition");if(!(def instanceof String))continue;
            var m=ARRAY_CAST.matcher((String)def);while(m.find())arrays.add(m.group(1));
        }
        assertThat(arrays).hasSizeGreaterThanOrEqualTo(9);
        try(var c=clean.source.restoredConnection()){
            String[] inputs={null,"","UNKNOWN","owner","OWNER","INVITE","VERIFY","RESET","ACTIVE","SUSPENDED","MEMBER","TRAINER","ADMIN","PENDING","PROCESSING","ACCEPTED","DEAD","CANCELLED","MEMBER_INVITE","TRAINER_INVITE","PASSWORD_CHANGED","é"};
            for(String values:arrays){
                String original="(ARRAY["+values+"])::text[]",restored="ARRAY["+elementCasts(values)+"]";
                String sql="select bool_and(((v=any("+original+")) is not distinct from (v=any("+restored+"))) and ((v<>all("+original+")) is not distinct from (v<>all("+restored+")))) from unnest(?::text[]) v";
                try(var p=c.prepareStatement(sql)){p.setArray(1,c.createArrayOf("text",inputs));try(var result=p.executeQuery()){result.next();assertThat(result.getBoolean(1)).as("Exact array cast truth table").isTrue();}}
            }
        }
        for(String key:List.of("function:fitcore_assert_owner:gid uuid","function:fitcore_check_owner:","function:fitcore_scope_catalogs:")){
            String expected=null;
            for(var db:List.of(clean.source,clean.restored,reference))try(var c=db.restoredConnection()){
                c.setAutoCommit(false);Map<String,Object> row=JSON.readValue(snapshot(db).get(key),Map.class);
                String setting=((String)((List<?>)row.get("proconfig")).get(0)).substring("search_path=".length()).replace("fitcore_schema",db.fixture.schema);
                try(var p=c.prepareStatement("select set_config('search_path',?,true)")){p.setString(1,setting);p.execute();}
                try(var statement=c.createStatement();var result=statement.executeQuery("select current_schemas(true)::text")){
                    result.next();String resolved=result.getString(1).replace(db.fixture.schema,"fitcore_schema");if(expected==null)expected=resolved;assertThat(resolved).isEqualTo(expected);
                }c.rollback();
            }
        }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void unknownSchemaFailsClosedAtPublicReadinessBoundary(boolean restored)throws Exception {
        var db=restored?clean.restored:clean.source;
        try(var c=db.restoredConnection();var s=c.createStatement()){
            c.setAutoCommit(false);s.execute("create schema certification_unknown");s.execute("grant usage on schema certification_unknown to "+RUNTIME);s.execute("set local role "+RUNTIME);c.setSchema("certification_unknown");
            var env=new org.springframework.mock.env.MockEnvironment().withProperty("spring.jpa.hibernate.ddl-auto","validate");
            var jdbc=new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(c,true));
            assertThat(new ReleaseReadiness(jdbc,env).ready()).isFalse();c.rollback();
        }
    }

}
