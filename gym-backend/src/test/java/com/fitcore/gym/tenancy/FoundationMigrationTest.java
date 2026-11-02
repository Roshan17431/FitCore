package com.fitcore.gym.tenancy;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.sql.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Every connection is restricted to a harness-owned, marked disposable database. */
@EnabledIfEnvironmentVariable(named="FITCORE_TENANCY_TEST_TOKEN", matches="[a-f0-9]{64}")
class FoundationMigrationTest {
    String schema;
    Connection connection() throws SQLException {
        String url=System.getenv("FITCORE_POSTGRES_TEST_URL");
        if (url==null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/fitcore_phase2"))
            throw new IllegalStateException("Only the disposable tenancy runner may connect");
        Connection c=DriverManager.getConnection(url,System.getenv("FITCORE_POSTGRES_TEST_USERNAME"),System.getenv("FITCORE_POSTGRES_TEST_PASSWORD"));
        try (var s=c.createStatement();var r=s.executeQuery("select shobj_description(oid,'pg_database') from pg_database where datname=current_database()")) {
            r.next(); if (!Objects.equals(r.getString(1),"fitcore-disposable:"+System.getenv("FITCORE_TENANCY_TEST_TOKEN"))) {c.close();throw new IllegalStateException("Disposable marker mismatch");}
        }
        if(schema!=null)c.setSchema(schema);
        return c;
    }
    @BeforeEach void isolatedSchema() throws Exception {
        schema="tenant_"+UUID.randomUUID().toString().replace("-","");
        try(var c=connection();var s=c.createStatement()){s.execute("create schema "+schema);}
    }
    @AfterEach void cleanup() throws Exception {try(var c=connection();var s=c.createStatement()){s.execute("drop schema "+schema+" cascade");}}
    Flyway flyway(String target) {
        // connection() separately verifies the live marker before each invocation.
        try(var ignored=connection()){}catch(SQLException e){throw new IllegalStateException(e);}
        return Flyway.configure().dataSource(System.getenv("FITCORE_POSTGRES_TEST_URL"),System.getenv("FITCORE_POSTGRES_TEST_USERNAME"),System.getenv("FITCORE_POSTGRES_TEST_PASSWORD"))
            .defaultSchema(schema).schemas(schema).target(target).baselineOnMigrate(false).cleanDisabled(true).load();
    }
    void migrate(String target){flyway(target).migrate();}
    void sql(String query,Object...args)throws Exception{try(var c=connection()){execute(c,query,args);}}
    static void execute(Connection c,String query,Object...args)throws SQLException{try(var p=c.prepareStatement(query)){for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);p.execute();}}
    long number(String q)throws Exception{try(var c=connection();var s=c.createStatement();var r=s.executeQuery(q)){r.next();return r.getLong(1);}}
    String value(String q)throws Exception{try(var c=connection();var s=c.createStatement();var r=s.executeQuery(q)){r.next();return r.getString(1);}}
    UUID gym(int n){return UUID.fromString("10000000-0000-0000-0000-"+String.format("%012d",n));}
    UUID user(int n){return UUID.fromString("20000000-0000-0000-0000-"+String.format("%012d",n));}
    void owner(Connection c,int n,String role,Boolean enabled)throws Exception {
        execute(c,"insert into gyms(id,name,slug,status,created_at,updated_at) values(?,?,?,'ACTIVE',timestamp '2030-01-01',timestamp '2030-01-01')",gym(n),"Synthetic Gym "+n,"synthetic-"+n);
        execute(c,"insert into users(id,full_name,email,password_hash,role,enabled,created_at,updated_at,gym_id) values(?,?,?,'synthetic-hash',?,?,timestamp '2030-01-01',timestamp '2030-01-01',?)",user(n),"Synthetic Owner "+n,"owner"+n+"@fixture.invalid",role,enabled,gym(n));
        execute(c,"update gyms set primary_owner_user_id=? where id=?",user(n),gym(n));
    }
    void twoGyms()throws Exception{
        migrate("4");
        try(var c=connection()){c.setAutoCommit(false);owner(c,1,"OWNER",true);owner(c,2,"OWNER",true);c.commit();}
        for(int n=1;n<=2;n++){
            long id=n*100L;
            sql("insert into membership_plans(id,name,price,duration_months,recommended,gym_id) values(?,'Premium',79,3,false,?)",id,gym(n));
            sql("insert into exercises(id,name,category,difficulty,gym_id) values(?,'Bench','Chest','BEGINNER',?)",id,gym(n));
            sql("insert into workout_plans(id,level,duration,gym_id) values(?,'Beginner','30 min',?)",id,gym(n));
            sql("insert into nutrition_plans(id,category,calories,protein,carbs,fats,gym_id) values(?,'Balanced',2000,100,250,60,?)",id,gym(n));
            sql("insert into trainer_profiles(id,user_id,specialization,experience_years,gym_id) values(?,?,'Strength',3,?)",id,user(n),gym(n));
            sql("insert into workout_logs(id,user_id,date,created_at,gym_id) values(?,?,date '2030-01-01',timestamp '2030-01-01',?)",id,user(n),gym(n));
            sql("insert into active_memberships(id,user_id,plan_id,start_date,end_date,status,created_at,updated_at,gym_id) values(?,?,?,date '2030-01-01',date '2030-04-01','ACTIVE',timestamp '2030-01-01',timestamp '2030-01-01',?)",id,user(n),id,gym(n));
            sql("insert into trainer_sessions(id,member_id,trainer_id,session_date,start_time,end_time,status,created_at,updated_at,gym_id) values(?,?,?,date '2030-01-03',time '10:00',time '11:00','SCHEDULED',timestamp '2030-01-01',timestamp '2030-01-01',?)",id,user(n),id,gym(n));
            sql("insert into attendance(id,user_id,gym_id,check_in_date,check_in_time) values(?,?,?,date '2030-01-01',timestamp '2030-01-01')",id,user(n),gym(n));
            sql("insert into progress_metrics(id,user_id,gym_id,log_date,created_at) values(?,?,?,date '2030-01-01',timestamp '2030-01-01')",id,user(n),gym(n));
            sql("insert into food_logs(id,user_id,gym_id,calories,meal_name,meal_type,log_date,log_time,created_at) values(?,?,?,100,'Synthetic','LUNCH',date '2030-01-01',time '12:00',timestamp '2030-01-01')",id,user(n),gym(n));
            sql("insert into notifications(id,user_id,gym_id,title,type,read_flag,created_at) values(?,?,?,'Synthetic','INFO',false,timestamp '2030-01-01')",id,user(n),gym(n));
            sql("insert into nutrition_goals(id,user_id,gym_id,updated_at) values(?,?,?,timestamp '2030-01-01')",id,user(n),gym(n));
            sql("insert into workout_exercise_logs(id,workout_log_id,exercise_id,gym_id) values(?,?,?,?)",id,id,id,gym(n));
        }
        // Unlinked foreign user avoids unique-user constraints masking the gym FK.
        sql("insert into users(id,full_name,email,password_hash,role,enabled,created_at,updated_at,gym_id) values(?,'Foreign member','foreign@fixture.invalid','synthetic','MEMBER',true,now(),now(),?)",user(3),gym(2));
    }
    @Test void emptyDatabaseInitializesVersionedBaseline()throws Exception{
        migrate("1");assertThat(number("select count(*) from information_schema.tables where table_schema='"+schema+"' and table_type='BASE TABLE'")).isEqualTo(22);
        assertThat(number("select count(*) from users")).isZero();assertThat(number("select count(*) from active_memberships")).isZero();
    }
    @Test void populatedSchemaRequiresExplicitBaseline()throws Exception{
        sql("create table unexpected(id int)");assertThatThrownBy(()->migrate("2")).hasMessageContaining("non-empty schema");
        assertThat(number("select count(*) from unexpected")).isZero();
    }
    @Test void driftAfterExplicitBaselineIsRejectedBeforeExpansion()throws Exception{
        migrate("1");sql("alter table users add column unreviewed text");assertThatThrownBy(()->migrate("2")).hasStackTraceContaining("Baseline schema mismatch");
        assertThat(number("select count(*) from information_schema.tables where table_schema='"+schema+"' and table_name='gyms'")).isZero();
    }
    @Test void flywayRejectsChecksumDriftAndDisallowsClean()throws Exception{
        migrate("1");sql("update flyway_schema_history set checksum=42 where version='1'");assertThatThrownBy(()->flyway("2").validate()).isInstanceOf(Exception.class);
        assertThatThrownBy(()->flyway("2").clean()).hasMessageContaining("cleanDisabled");
    }
    @Test void finalSchemaAllowsIndependentCatalogsAndRejectsLocalDuplicate()throws Exception{
        twoGyms();assertThat(number("select count(*) from membership_plans where name='Premium'")).isEqualTo(2);
        assertThatThrownBy(()->sql("insert into membership_plans(name,price,duration_months,recommended,gym_id) values('Premium',29,1,false,?)",gym(1))).isInstanceOf(SQLException.class).satisfies(error -> assertThat(((SQLException)error).getSQLState()).startsWith("23"));
    }
    @Test void ownerlessGymCannotCommitAndTransactionRollsBack()throws Exception{
        migrate("4");try(var c=connection()){c.setAutoCommit(false);execute(c,"insert into gyms(id,name,slug,status,created_at,updated_at) values(?,'Empty','empty','ACTIVE',now(),now())",gym(1));assertThatThrownBy(c::commit).isInstanceOf(SQLException.class).satisfies(error -> assertThat(((SQLException)error).getSQLState()).startsWith("23"));c.rollback();}
        assertThat(number("select count(*) from gyms")).isZero();
    }
    @ParameterizedTest @CsvSource({"ADMIN,true","MEMBER,true","OWNER,false"})
    void primaryOwnerMustBeEnabledOwner(String role,boolean enabled)throws Exception{
        migrate("4");try(var c=connection()){c.setAutoCommit(false);owner(c,1,role,enabled);assertThatThrownBy(c::commit).isInstanceOf(SQLException.class).satisfies(error -> assertThat(((SQLException)error).getSQLState()).startsWith("23"));c.rollback();}
        assertThat(number("select count(*) from users")).isZero();
    }
    @Test void legacyNullEnabledSemanticsArePreserved()throws Exception{
        migrate("4");try(var c=connection()){c.setAutoCommit(false);owner(c,1,"OWNER",null);c.commit();}assertThat(number("select count(*) from gyms")).isEqualTo(1);
    }
    @ParameterizedTest @CsvSource({"enabled,false","role,'MEMBER'"})
    void primaryOwnerCannotBeInvalidated(String field,String replacement)throws Exception{
        twoGyms();assertThatThrownBy(()->sql("update users set "+field+"=? where id=?",field.equals("enabled")?Boolean.valueOf(replacement):replacement,user(1))).isInstanceOf(SQLException.class).satisfies(error -> assertThat(((SQLException)error).getSQLState()).startsWith("23"));
        assertThat(value("select role from users where email='owner1@fixture.invalid'")).isEqualTo("OWNER");
    }
    @Test void ownerCannotBeDeletedOrReplacedOrMoved()throws Exception{
        twoGyms();assertThatThrownBy(()->sql("delete from users where id=?",user(1))).isInstanceOf(SQLException.class).satisfies(error -> assertThat(((SQLException)error).getSQLState()).startsWith("23"));
        assertThatThrownBy(()->sql("update gyms set primary_owner_user_id=? where id=?",user(2),gym(1))).isInstanceOf(SQLException.class).satisfies(error -> assertThat(((SQLException)error).getSQLState()).startsWith("23"));
        assertThatThrownBy(()->sql("update users set gym_id=? where id=?",gym(2),user(1))).isInstanceOf(SQLException.class).satisfies(error -> assertThat(((SQLException)error).getSQLState()).startsWith("23"));
    }
    @ParameterizedTest @CsvSource({"active_memberships,plan_id,200","trainer_sessions,trainer_id,200","workout_exercise_logs,workout_log_id,200","workout_exercise_logs,exercise_id,200"})
    void foreignNestedReferencesAreRejected(String table,String column,long foreignId)throws Exception{
        twoGyms();assertThatThrownBy(()->sql("update "+table+" set "+column+"=? where id=100",foreignId)).isInstanceOf(SQLException.class).satisfies(error -> assertThat(((SQLException)error).getSQLState()).startsWith("23"));
        assertThat(number("select "+column+" from "+table+" where id=100")).isEqualTo(100);
    }
    @ParameterizedTest @CsvSource({"active_memberships,user_id","attendance,user_id","food_logs,user_id","notifications,user_id","nutrition_goals,user_id","progress_metrics,user_id","trainer_profiles,user_id","trainer_sessions,member_id","workout_logs,user_id"})
    void existingRowCannotBeRepointedToForeignUser(String table,String column)throws Exception {
        twoGyms();
        assertThatThrownBy(()->sql("update "+table+" set "+column+"=? where id=100",user(3)))
            .isInstanceOf(SQLException.class).satisfies(error -> assertThat(((SQLException)error).getSQLState()).isEqualTo("23503"));
        assertThat(value("select "+column+"::text from "+table+" where id=100")).isEqualTo(user(1).toString());
    }
    @Test void primaryOwnerCannotReferenceAnotherGym()throws Exception {
        migrate("4");
        try(var c=connection()) {
            c.setAutoCommit(false);owner(c,1,"OWNER",true);
            execute(c,"insert into gyms(id,name,slug,status,primary_owner_user_id,created_at,updated_at) values(?,'Wrong owner','wrong-owner','ACTIVE',?,now(),now())",gym(2),user(1));
            assertThatThrownBy(c::commit).isInstanceOf(SQLException.class).satisfies(error -> assertThat(((SQLException)error).getSQLState()).startsWith("23"));c.rollback();
        }
        assertThat(number("select count(*) from gyms")).isZero();
    }
    @Test void concurrentInvalidationsCannotRemoveRequiredOwner()throws Exception {
        twoGyms();
        var start=new java.util.concurrent.CountDownLatch(1);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            List<java.util.concurrent.Future<String>> results=new ArrayList<>();
            for(String change:List.of("enabled=false","role='MEMBER'")) results.add(pool.submit(()->{
                start.await();
                try(var c=connection()) {
                    c.setAutoCommit(false);execute(c,"set local lock_timeout='5s'");
                    execute(c,"select id from gyms where id=? for update",gym(1));
                    execute(c,"update users set "+change+" where id=?",user(1));
                    try { c.commit();return "unexpected commit"; }
                    catch(SQLException invalid) { c.rollback();return invalid.getSQLState(); }
                }
            }));
            start.countDown();
            for(var result:results) assertThat(result.get(15,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo("23514");
        } finally {
            pool.shutdownNow();
        }
        assertThat(value("select role||':'||enabled from users where id='"+user(1)+"'")).isEqualTo("OWNER:true");
    }
    @ParameterizedTest @CsvSource({"attendance,check_in_date,check_in_time,date '2030-01-01',timestamp '2030-01-01'","progress_metrics,log_date,created_at,date '2030-01-01',timestamp '2030-01-01'"})
    void personalRowsCannotReferenceForeignUsers(String table,String dateColumn,String timeColumn,String date,String time)throws Exception{
        twoGyms();assertThatThrownBy(()->sql("insert into "+table+"(user_id,gym_id,"+dateColumn+","+timeColumn+") values(?,?,"+date+","+time+")",user(2),gym(1))).isInstanceOf(SQLException.class).satisfies(error -> assertThat(((SQLException)error).getSQLState()).startsWith("23"));
    }
    @ParameterizedTest @CsvSource({"membership_plan_features,plan_id,feature","trainer_certifications,trainer_id,certification","trainer_available_slots,trainer_id,slot","workout_plan_schedule,plan_id,schedule_item","workout_plan_exercises,plan_id,exercise_name","nutrition_plan_meals,plan_id,meal"})
    void collectionCannotAttachToForeignParent(String table,String parent,String value)throws Exception{
        twoGyms();assertThatThrownBy(()->sql("insert into "+table+"(gym_id,"+parent+","+value+") values(?,100,'synthetic')",gym(2))).isInstanceOf(SQLException.class).satisfies(error -> assertThat(((SQLException)error).getSQLState()).startsWith("23"));
    }
    @Test void rejectedBatchRollsBackEarlierValidWrites()throws Exception{
        twoGyms();try(var c=connection()){c.setAutoCommit(false);execute(c,"insert into notifications(user_id,gym_id,title,type,read_flag,created_at) values(?,?,'local','INFO',false,now())",user(1),gym(1));assertThatThrownBy(()->execute(c,"insert into workout_exercise_logs(workout_log_id,exercise_id,gym_id) values(100,200,?)",gym(1))).isInstanceOf(SQLException.class).satisfies(error -> assertThat(((SQLException)error).getSQLState()).startsWith("23"));c.rollback();}
        assertThat(number("select count(*) from notifications where title='local'")).isZero();
    }
    @Test void invitationIsMemberOnlyAndCreatorMustBeLocal()throws Exception{
        twoGyms();assertThatThrownBy(()->sql("insert into gym_invitations(id,gym_id,email,token_hash,role,created_by_user_id,expires_at,created_at) values(?,?,?,'digest','OWNER',?,now()+interval '2 days',now())",UUID.randomUUID(),gym(1),"new@fixture.invalid",user(1))).isInstanceOf(SQLException.class).satisfies(error -> assertThat(((SQLException)error).getSQLState()).startsWith("23"));
        assertThatThrownBy(()->sql("insert into gym_invitations(id,gym_id,email,token_hash,role,created_by_user_id,expires_at,created_at) values(?,?,?,'digest','MEMBER',?,now()+interval '2 days',now())",UUID.randomUUID(),gym(1),"new@fixture.invalid",user(2))).isInstanceOf(SQLException.class).satisfies(error -> assertThat(((SQLException)error).getSQLState()).startsWith("23"));
    }
    @Test void missingLegacyOwnershipIsNotInferred()throws Exception{
        migrate("2");sql("insert into users(id,full_name,email,password_hash,role,enabled,created_at,updated_at) values(?,'Unassigned','unassigned@fixture.invalid','unchanged','ADMIN',false,now(),now())",user(1));
        assertThatThrownBy(()->migrate("4")).hasStackTraceContaining("Explicit ownership mapping required");
        assertThat(number("select count(*) from gyms")).isZero();assertThat(value("select password_hash from users")).isEqualTo("unchanged");
    }
    @Test void migratedSchemaSupportsDomainAssociationsAndCollections() throws Exception {
        twoGyms();
        migrate("7");
        var config=new org.hibernate.cfg.Configuration();
        for(String type:List.of("Gym","GymInvitation","User","ActiveMembership","Attendance","Exercise","FoodLog","MembershipPlan","Notification","NutritionGoal","NutritionPlan","ProgressMetric","TrainerProfile","TrainerSession","WorkoutExerciseLog","WorkoutLog","WorkoutPlan"))
            config.addAnnotatedClass(Class.forName("com.fitcore.gym.entity."+type));
        config.setProperty("hibernate.connection.url",System.getenv("FITCORE_POSTGRES_TEST_URL"));
        config.setProperty("hibernate.connection.username",System.getenv("FITCORE_POSTGRES_TEST_USERNAME"));
        config.setProperty("hibernate.connection.password",System.getenv("FITCORE_POSTGRES_TEST_PASSWORD"));
        config.setProperty("hibernate.default_schema",schema);
        config.setProperty("hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl");
        config.setProperty("hibernate.hbm2ddl.auto","validate");
        try(var factory=config.buildSessionFactory();var session=factory.openSession()) {
            var tx=session.beginTransaction();
            var u=session.find(com.fitcore.gym.entity.User.class,user(1));
            assertThat(u.getGym().getId()).isEqualTo(gym(1));
            assertThat(u.getAuthzVersion()).isZero();
            var plan=com.fitcore.gym.entity.MembershipPlan.builder().gymId(gym(1)).name("Roundtrip").price(new java.math.BigDecimal("25")).durationMonths(1).features(List.of("Local feature")).build();
            session.persist(plan);tx.commit();session.clear();
            assertThat(session.find(com.fitcore.gym.entity.MembershipPlan.class,plan.getId()).getFeatures()).containsExactly("Local feature");
            var gym=session.find(com.fitcore.gym.entity.Gym.class,gym(1));
            assertThat(gym.getPrimaryOwner().getId()).isEqualTo(user(1));
            tx=session.beginTransaction();
            var invitation=com.fitcore.gym.entity.GymInvitation.builder().gymId(gym(1)).email("invitee@fixture.invalid")
                .tokenHash("synthetic-token-digest").createdBy(session.find(com.fitcore.gym.entity.User.class,user(1)))
                .expiresAt(java.time.LocalDateTime.of(2030,1,2,0,0)).build();
            session.persist(invitation);tx.commit();session.clear();
            assertThat(session.find(com.fitcore.gym.entity.GymInvitation.class,invitation.getId()).getCreatedBy().getGymId()).isEqualTo(gym(1));
            tx=session.beginTransaction();var ownedPlan=session.find(com.fitcore.gym.entity.MembershipPlan.class,plan.getId());
            assertThatThrownBy(()->ownedPlan.setGymId(gym(2))).isInstanceOf(IllegalStateException.class);
            tx.rollback();session.clear();
            assertThat(session.find(com.fitcore.gym.entity.MembershipPlan.class,plan.getId()).getGymId()).isEqualTo(gym(1));
        }
    }

    void legacyRecords() throws Exception {
        migrate("1");
        for(int n=1;n<=4;n++) sql("insert into users(id,full_name,email,password_hash,role,enabled,created_at,updated_at) values(?,?,?,'preserved-hash',?,?,timestamp '2025-01-01',timestamp '2025-01-01')",user(n),"Legacy fixture "+n,"legacy"+n+"@fixture.invalid",n==4?"TRAINER":"ADMIN",n!=3);
        sql("insert into membership_plans(name,price,duration_months,recommended) values('Legacy Premium',79,3,false)");
        sql("insert into membership_plan_features(plan_id,feature) values(1,'Original feature')");
        for(int n=1;n<=3;n++) sql("insert into active_memberships(id,user_id,plan_id,start_date,end_date,status,created_at,updated_at) values(?,?,1,date '2025-01-01',date '2035-01-01','ACTIVE',timestamp '2025-01-01',timestamp '2025-01-01')",n,user(n==2?2:1));
        sql("insert into exercises(name,category,difficulty) values('Legacy Bench','Chest','BEGINNER')");
        for(int n=1;n<=2;n++) {
            sql("insert into workout_logs(id,user_id,date,created_at) values(?,?,date '2025-01-01',timestamp '2025-01-01')",n,user(n));
            sql("insert into workout_exercise_logs(id,workout_log_id,exercise_id,sets_completed,reps_completed) values(?,?,1,3,'10')",n,n);
        }
        sql("insert into workout_plans(level,duration) values('Legacy Beginner','30 min')");
        sql("insert into workout_plan_schedule(plan_id,schedule_item) values(1,'Monday')");
        sql("insert into workout_plan_exercises(plan_id,exercise_name) values(1,'Legacy Bench')");
        sql("insert into nutrition_plans(category,calories,protein,carbs,fats) values('Legacy Balanced',2000,100,250,60)");
        sql("insert into nutrition_plan_meals(plan_id,meal) values(1,'Original meal')");
        sql("insert into trainer_profiles(id,user_id,specialization,experience_years) values(1,?,'Strength',3)",user(4));
        sql("insert into trainer_certifications(trainer_id,certification) values(1,'Original certification')");
        sql("insert into trainer_available_slots(trainer_id,slot) values(1,'09:00-12:00')");
        sql("insert into trainer_sessions(id,member_id,trainer_id,session_date,start_time,end_time,status,created_at,updated_at) values(1,?,1,date '2030-01-02',time '10:00',time '11:00','SCHEDULED',timestamp '2025-01-01',timestamp '2025-01-01')",user(1));
        migrate("2");
    }
    void stageExplicitSyntheticMapping(Connection c,boolean splitBooking) throws Exception {
        // These literal choices apply ONLY to legacyRecords() synthetic UUIDs; never to production.
        for(int n=1;n<=2;n++) execute(c,"insert into gyms(id,name,slug,status,created_at,updated_at) values(?,?,?,'ACTIVE',timestamp '2030-01-01',timestamp '2030-01-01')",gym(n),"Approved synthetic gym "+n,"approved-synthetic-"+n);
        for(int n=1;n<=4;n++)execute(c,"update users set gym_id=? where id=?",gym(n==2 || (n==4 && splitBooking)?2:1),user(n));
        for(int n=1;n<=2;n++) {
            execute(c,"update users set role='OWNER',authz_version=authz_version+1 where id=?",user(n));
            execute(c,"update gyms set primary_owner_user_id=? where id=?",user(n),gym(n));
        }
        for(String table:List.of("membership_plans","exercises","workout_plans","nutrition_plans"))execute(c,"update "+table+" set gym_id=?",gym(1));
        for(String table:List.of("active_memberships","workout_logs","trainer_profiles"))execute(c,"update "+table+" t set gym_id=u.gym_id from users u where t.user_id=u.id");
        execute(c,"update trainer_sessions t set gym_id=u.gym_id from users u where t.member_id=u.id");
        execute(c,"update workout_exercise_logs e set gym_id=w.gym_id from workout_logs w where e.workout_log_id=w.id");
        for(String table:List.of("membership_plan_features","nutrition_plan_meals","workout_plan_exercises","workout_plan_schedule"))execute(c,"update "+table+" set gym_id=?",gym(1));
        for(String table:List.of("trainer_certifications","trainer_available_slots"))execute(c,"update "+table+" set gym_id=?",gym(splitBooking?2:1));
        execute(c,"select fitcore_scope_catalogs()"); // before copies, in the SAME transaction
        execute(c,"insert into membership_plans(name,price,duration_months,recommended,gym_id) select name,price,duration_months,recommended,? from membership_plans where id=1",gym(2));
        execute(c,"insert into membership_plan_features(plan_id,feature,gym_id) select 2,feature,? from membership_plan_features where plan_id=1",gym(2));
        execute(c,"update active_memberships set plan_id=2 where user_id=?",user(2));
        execute(c,"insert into exercises(name,category,difficulty,gym_id) select name,category,difficulty,? from exercises where id=1",gym(2));
        execute(c,"update workout_exercise_logs set exercise_id=2 where gym_id=?",gym(2));
    }
    @Test void explicitSyntheticMappingPreservesHistoryAndCopiesSharedCatalogs() throws Exception {
        legacyRecords();String before=value("select string_agg(id||':'||user_id||':'||start_date||':'||end_date||':'||status||':'||created_at,'|' order by id) from active_memberships");
        try(var c=connection()){c.setAutoCommit(false);stageExplicitSyntheticMapping(c,false);c.commit();}
        migrate("4");
        assertThat(value("select string_agg(id||':'||user_id||':'||start_date||':'||end_date||':'||status||':'||created_at,'|' order by id) from active_memberships")).isEqualTo(before);
        assertThat(number("select count(*) from active_memberships")).isEqualTo(3);
        assertThat(number("select count(*) from active_memberships where user_id='"+user(1)+"' and status='ACTIVE'")).isEqualTo(2); // historical overlap retained
        assertThat(number("select count(*) from users where password_hash='preserved-hash' and created_at=timestamp '2025-01-01'")).isEqualTo(4);
        assertThat(value("select role||':'||enabled from users where id='"+user(3)+"'")).isEqualTo("ADMIN:false");
        assertThat(number("select count(*) from membership_plans where name='Legacy Premium' and price=79 and duration_months=3")).isEqualTo(2);
        assertThat(number("select count(*) from membership_plan_features where feature='Original feature'")).isEqualTo(2);
        assertThat(number("select plan_id from active_memberships where user_id='"+user(2)+"'")).isEqualTo(2);
        assertThat(number("select exercise_id from workout_exercise_logs where id=2")).isEqualTo(2);
        assertThat(number("select trainer_id from trainer_sessions where id=1")).isEqualTo(1);
        assertThat(value("select user_id::text from trainer_profiles where id=1")).isEqualTo(user(4).toString());
    }
    @Test void crossGymHistoricalBookingBlocksEnforcementWithoutRewritingIt() throws Exception {
        legacyRecords();try(var c=connection()){c.setAutoCommit(false);stageExplicitSyntheticMapping(c,true);c.commit();}
        assertThatThrownBy(()->migrate("4")).hasStackTraceContaining("fk_trainer_sessions_trainer_id_gym");
        assertThat(number("select trainer_id from trainer_sessions")).isEqualTo(1);
        assertThat(number("select count(*) from trainer_sessions")).isEqualTo(1);
        assertThat(value("select max(version) from flyway_schema_history where success")).isEqualTo("2");
    }
    @Test void failedCopyRollsBackCatalogConstraintTransitionAndMapping() throws Exception {
        legacyRecords();try(var c=connection()){c.setAutoCommit(false);stageExplicitSyntheticMapping(c,false);
            assertThatThrownBy(()->execute(c,"insert into membership_plans(name,price,duration_months,recommended,gym_id) values('Legacy Premium',1,1,false,?)",gym(2))).isInstanceOf(SQLException.class).satisfies(error -> assertThat(((SQLException)error).getSQLState()).startsWith("23"));c.rollback();}
        assertThat(number("select count(*) from gyms")).isZero();
        assertThat(number("select count(*) from membership_plans")).isEqualTo(1);
        assertThat(number("select count(*) from users where gym_id is null")).isEqualTo(4);
        assertThat(number("select count(*) from pg_constraint where conrelid='membership_plans'::regclass and conname='membership_plans_name_key'")).isEqualTo(1);
    }
    @Test void explicitBaselineAdoptsExistingSchemaWithoutRecreatingRecords() throws Exception {
        migrate("1");sql("drop table flyway_schema_history");
        sql("insert into users(id,full_name,email,password_hash,role,created_at,updated_at) values(?,'Preserve','preserve@fixture.invalid','original','MEMBER',now(),now())",user(1));
        flyway("2").baseline();migrate("2");
        assertThat(number("select count(*) from users")).isEqualTo(1);assertThat(value("select password_hash from users")).isEqualTo("original");
        assertThat(number("select count(*) from gyms")).isZero();
    }

}
