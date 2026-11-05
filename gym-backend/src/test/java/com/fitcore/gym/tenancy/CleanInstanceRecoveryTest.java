package com.fitcore.gym.tenancy;

import static org.assertj.core.api.Assertions.*;
import com.fitcore.gym.email.*;
import com.fitcore.gym.domain.Role;
import com.fitcore.gym.entity.User;
import com.fitcore.gym.security.JwtService;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.net.*;
import java.net.http.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

/** Brand-new database containers, not merely empty schemas or legacy mapping fixtures. */
@EnabledIfEnvironmentVariable(named="FITCORE_TENANCY_TEST_TOKEN",matches="[a-f0-9]{64}")
class CleanInstanceRecoveryTest {
    final TenantRecoveryTest source=new TenantRecoveryTest(), restored=new TenantRecoveryTest();
    Process app;
    static final String KEY="clean-recovery", JWT="synthetic-clean-recovery-jwt-key-not-for-production-only-2026";
    @BeforeEach void prepare()throws Exception {source.prepare();restored.prepare();source.freshTarget();}
    @AfterEach void cleanup()throws Exception {
        try {if(app!=null){app.destroy();if(!app.waitFor(10,TimeUnit.SECONDS)){app.destroyForcibly();assertThat(app.waitFor(10,TimeUnit.SECONDS)).isTrue();}}}
        finally {try{source.cleanup();}finally{restored.cleanup();}}
    }
    Flyway migrations(){return Flyway.configure().dataSource(source.targetUrl,TenantRecoveryTest.USER,TenantRecoveryTest.PASSWORD).defaultSchema(source.fixture.schema).schemas(source.fixture.schema).baselineOnMigrate(false).cleanDisabled(true).load();}
    JdbcTemplate jdbc(TenantRecoveryTest db){return new JdbcTemplate(new DriverManagerDataSource(db.targetUrl+"?currentSchema="+db.fixture.schema,TenantRecoveryTest.USER,TenantRecoveryTest.PASSWORD));}
    long count(TenantRecoveryTest db,String sql)throws Exception{try(var c=db.restoredConnection()){return db.count(c,sql);}}
    void history(TenantRecoveryTest db)throws Exception {
        try(var c=db.restoredConnection();var s=c.createStatement();var r=s.executeQuery("select string_agg(version,',' order by installed_rank),bool_and(success) from flyway_schema_history")){
            r.next();assertThat(r.getString(1)).isEqualTo("1,2,4,5,6,7,8,9,10");assertThat(r.getBoolean(2)).isTrue();
        }
    }
    @Test void genuinelyEmptyDatabaseInitializesWithoutV3AndRerunsWithoutSeeds()throws Exception {
        assertThat(count(source,"select count(*) from information_schema.tables where table_schema not in ('pg_catalog','information_schema')")).isZero();
        assertThat(migrations().migrate().migrationsExecuted).isEqualTo(9);history(source);
        assertThat(count(source,"select count(*) from users")).isZero();assertThat(count(source,"select count(*) from gyms")).isZero();
        assertThat(count(source,"select count(*) from membership_plans")).isZero();
        assertThat(migrations().migrate().migrationsExecuted).isZero();history(source);
        assertThat(count(source,"select count(*) from information_schema.tables where table_schema=current_schema() and table_type='BASE TABLE'")).isEqualTo(30);
    }
    void seed()throws Exception {
        migrations().migrate();
        try(var c=source.restoredConnection()){
            c.setAutoCommit(false);source.fixture.owner(c,1,"OWNER",true);source.fixture.owner(c,2,"OWNER",true);
            for(int n=1;n<=2;n++){
                UUID gym=source.fixture.gym(n), member=source.fixture.user(n+2), trainer=source.fixture.user(n+4);
                FoundationMigrationTest.execute(c,"insert into users(id,full_name,email,password_hash,role,enabled,gym_id,created_at,updated_at) values(?,?,?,'synthetic-hash','MEMBER',true,?,now(),now()),(?,?,?,'synthetic-hash','TRAINER',true,?,now(),now())",member,"Member "+n,"member"+n+"@fixture.invalid",gym,trainer,"Trainer "+n,"trainer"+n+"@fixture.invalid",gym);
                FoundationMigrationTest.execute(c,"insert into membership_plans(id,name,price,duration_months,recommended,gym_id) values(?,'Independent Premium',79,3,false,?)",n,gym);
                FoundationMigrationTest.execute(c,"insert into membership_plan_features(plan_id,gym_id,feature) values(?,?,'Synthetic feature')",n,gym);
                FoundationMigrationTest.execute(c,"insert into trainer_profiles(id,user_id,specialization,experience_years,gym_id) values(?,?,'Strength',3,?)",n,trainer,gym);
                FoundationMigrationTest.execute(c,"insert into active_memberships(id,user_id,plan_id,gym_id,start_date,end_date,status,created_at,updated_at) values(?,?,?,?,current_date,current_date+30,'ACTIVE',now(),now())",n,member,n,gym);
                FoundationMigrationTest.execute(c,"insert into trainer_sessions(id,member_id,trainer_id,gym_id,session_date,start_time,end_time,status,created_at,updated_at) values(?,?,?,?,current_date+1,time '10:00',time '11:00','SCHEDULED',now(),now())",n,member,n,gym);
            }
            try(var s=c.createStatement()){for(String table:List.of("membership_plans","trainer_profiles","active_memberships","trainer_sessions"))s.execute("select setval(pg_get_serial_sequence('"+table+"','id'),(select max(id) from "+table+"))");}
            c.commit();
        }
        var j=jdbc(source);var manager=new DataSourceTransactionManager(j.getDataSource());var cipher=new AesGcmOutboxCipher(Map.of(KEY,new byte[32]),KEY);
        var worker=new EmailOutbox(j,manager,cipher,(id,msg)->{throw new AssertionError("Source must never send");});
        new TransactionTemplate(manager).executeWithoutResult(s->worker.enqueue(new EmailIntent(source.fixture.gym(1),null,null,"PASSWORD_CHANGED","owner1@fixture.invalid","Recovery notice","synthetic-encrypted-recovery-body","<p>synthetic</p>",j.queryForObject("select localtimestamp",LocalDateTime.class).plusHours(1))));
    }
    Path dump()throws Exception {
        try(var ignored=source.restoredConnection()){} // validate owned target marker first
        Path path=source.temp.resolve("clean.dump");
        var p=new ProcessBuilder("docker","exec",source.target,"pg_dump","-U",TenantRecoveryTest.USER,"-d","fitcore_phase2","--format=custom","--schema="+source.fixture.schema,"--no-owner","--no-privileges").redirectOutput(path.toFile()).redirectError(source.temp.resolve("dump.err").toFile()).start();
        if(!p.waitFor(60,TimeUnit.SECONDS)){p.destroyForcibly();throw new IllegalStateException("Owned clean backup timed out");}assertThat(p.exitValue()).isZero();assertThat(Files.size(path)).isPositive();return path;
    }
    @Test void cleanTwoGymArchiveRestoresBusinessDataKeysPrivilegesAndHttpIsolation()throws Exception {
        seed();Map<String,String> before;
        try(var c=source.restoredConnection()){before=source.fingerprint(c);}
        Path archive=dump();restored.freshTarget();String unusedSchema=restored.fixture.schema;
        // Restore includes source schema name; retain original helper schema for its cleanup.
        restored.restore(archive);restored.fixture.schema=source.fixture.schema;
        try {
            try(var c=restored.restoredConnection()){assertThat(restored.fingerprint(c)).isEqualTo(before);}
            history(restored);
            var j=jdbc(restored);byte[] encrypted=j.queryForObject("select payload from email_outbox",byte[].class);
            assertThat(new String(encrypted,java.nio.charset.StandardCharsets.UTF_8)).doesNotContain("synthetic-encrypted-recovery-body");
            String id=j.queryForObject("select id::text from email_outbox",String.class);
            String binding=id+"|PASSWORD_CHANGED|null|null|"+source.fixture.gym(1);
            var cipher=new AesGcmOutboxCipher(Map.of(KEY,new byte[32]),KEY);
            var message=cipher.open(KEY,encrypted,binding);
            assertThat(message.text()).isEqualTo("synthetic-encrypted-recovery-body");
            assertThatThrownBy(()->new AesGcmOutboxCipher(Map.of("other",new byte[32]),"other").open(KEY,encrypted,binding)).isInstanceOf(RuntimeException.class);
            try(var c=restored.restoredConnection();var s=c.createStatement()){
                s.execute("create role clean_restore_runtime login password 'synthetic-runtime-only' nosuperuser nocreatedb nocreaterole nobypassrls noinherit");
                s.execute("grant usage on schema "+source.fixture.schema+" to clean_restore_runtime");
                s.execute("grant select,insert,update,delete on all tables in schema "+source.fixture.schema+" to clean_restore_runtime");
                s.execute("revoke all on flyway_schema_history from clean_restore_runtime");s.execute("grant select on flyway_schema_history to clean_restore_runtime");
                s.execute("grant usage,select on all sequences in schema "+source.fixture.schema+" to clean_restore_runtime");
                s.execute("revoke all on function fitcore_scope_catalogs() from public");
                s.execute("set role clean_restore_runtime");assertThat(com.fitcore.gym.operations.SchemaContract.fingerprint(c)).isEqualTo("c14f022fb8ed56bec51719b37b7c2c5e3ea8d194282463638e84d3d42e1af37b");
                assertThat(com.fitcore.gym.operations.ReleaseReadiness.schemaReady(c)).as("Certified full V10 restore must pass restricted runtime readiness").isTrue();
                assertThatThrownBy(()->s.execute("select fitcore_scope_catalogs()" )).isInstanceOf(SQLException.class);
                assertThatThrownBy(()->s.execute("update flyway_schema_history set checksum=0")).isInstanceOf(SQLException.class);
                assertThatThrownBy(()->s.execute("insert into active_memberships(user_id,plan_id,gym_id,start_date,end_date,status,created_at,updated_at) values('"+source.fixture.user(3)+"',2,'"+source.fixture.gym(1)+"',current_date+60,current_date+90,'ACTIVE',now(),now())")).isInstanceOf(SQLException.class);
            }
            try(var c=restored.restoredConnection();var s=c.createStatement();var r=s.executeQuery("insert into membership_plans(name,price,duration_months,recommended,gym_id) values('Next sequence',80,1,false,'"+source.fixture.gym(1)+"') returning id")){r.next();assertThat(r.getLong(1)).isGreaterThan(2);}
            URI base=startRestoredApp();
            assertThat(http(base,"/api/health/ready",token(1)).statusCode()).isEqualTo(200);
            for(int n=1;n<=2;n++){
                String token=token(n);
                assertThat(http(base,"/api/auth/me",token).statusCode()).isEqualTo(200);
                assertThat(http(base,"/api/users/"+source.fixture.user(n==1?2:1),token).statusCode()).isEqualTo(404);
                assertThat(http(base,"/api/admin/users",token).body()).contains("member"+n+"@fixture.invalid").doesNotContain("member"+(n==1?2:1)+"@fixture.invalid");
            }
            var manager=new DataSourceTransactionManager(j.getDataSource());var accepted=new ArrayList<UUID>();
            var worker=new EmailOutbox(j,manager,cipher,(messageId,m)->{assertThat(m.text()).isEqualTo("synthetic-encrypted-recovery-body");accepted.add(messageId);return new EmailProvider.Acceptance("synthetic-only");});
            ReflectionTestUtils.setField(worker,"worker",true);assertThat(worker.processOne()).isTrue();assertThat(j.queryForMap("select status,failure_code,attempts from email_outbox")).containsEntry("status","ACCEPTED");assertThat(accepted).containsExactly(UUID.fromString(id));
            assertThat(j.queryForObject("select payload from email_outbox",byte[].class)).isNull();
        }finally{restored.fixture.schema=unusedSchema;}
    }
    String token(int n){var jwt=new JwtService();ReflectionTestUtils.setField(jwt,"secretKey",JWT);ReflectionTestUtils.setField(jwt,"jwtExpirationMs",60000L);return jwt.generateToken(User.builder().id(source.fixture.user(n)).gymId(source.fixture.gym(n)).role(Role.OWNER).passwordHash("synthetic-hash").enabled(true).build());}
    HttpResponse<String> http(URI base,String path,String token)throws Exception {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build().send(HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(5)).header("Authorization","Bearer "+token).GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    URI startRestoredApp()throws Exception {
        Path log=restored.temp.resolve("app.log");
        var args=new ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),"com.fitcore.gym.GymBackendApplication","--server.address=127.0.0.1","--server.port=0","--spring.config.import=","--spring.datasource.url="+restored.targetUrl,"--spring.datasource.username=clean_restore_runtime","--spring.datasource.password=synthetic-runtime-only","--spring.datasource.hikari.schema="+restored.fixture.schema,"--spring.jpa.properties.hibernate.default_schema="+restored.fixture.schema,"--spring.datasource.driver-class-name=org.postgresql.Driver","--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect","--spring.jpa.defer-datasource-initialization=false","--spring.jpa.hibernate.ddl-auto=validate","--spring.sql.init.mode=never","--spring.flyway.enabled=false","--aws.s3.enabled=false","--jwt.secret="+JWT,"--jwt.expiration-ms=60000","--cors.allowed-origins=http://127.0.0.1:5175"));
        var pb=new ProcessBuilder(args).directory(restored.temp.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());pb.environment().clear();pb.environment().put("FITCORE_TENANCY_TEST_TOKEN",restored.target);app=pb.start();
        for(int i=0;i<450;i++){
            if(!app.isAlive())throw new IllegalStateException("Restored disposable app failed startup; inspect owned temporary log");
            if(Files.exists(log)){
                var match=java.util.regex.Pattern.compile("Tomcat started on port ([0-9]+)").matcher(Files.readString(log));
                if(match.find())return URI.create("http://127.0.0.1:"+match.group(1));
            }
            Thread.sleep(200);
        }
        throw new IllegalStateException("Restored disposable app startup timed out");
    }
}
