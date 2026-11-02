package com.fitcore.gym.operations;

import java.sql.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Technical probe only; never grants production release or ownership approval. */
@Component
public class ReleaseReadiness {
    private final JdbcTemplate jdbc;
    private final Environment environment;
    public ReleaseReadiness(JdbcTemplate jdbc, Environment environment) {
        this.jdbc=jdbc; this.environment=environment;
    }
    public boolean ready() {
        if (!"validate".equals(environment.getProperty("spring.jpa.hibernate.ddl-auto"))
                || !"never".equals(environment.getProperty("spring.sql.init.mode", "never"))
                || environment.getProperty("spring.flyway.enabled", Boolean.class, false)
                || environment.getProperty("fitcore.onboarding.demo-enabled", Boolean.class, false)
                || environment.getProperty("aws.s3.enabled", Boolean.class, false)
                || !"disabled".equals(environment.getProperty("fitcore.email.provider", "disabled"))) return false;
        try { return Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) ReleaseReadiness::schemaReady)); }
        catch (RuntimeException unavailable) { return false; }
    }
    private static boolean yes(Connection connection, String sql) throws SQLException {
        try(var statement=connection.createStatement()) {
            statement.setQueryTimeout(2);
            try(var result=statement.executeQuery(sql)) { return result.next() && result.getBoolean(1); }
        }
    }
    public static boolean schemaReady(Connection c) throws SQLException {
        if (!"PostgreSQL".equals(c.getMetaData().getDatabaseProductName())) return false;
        if (!yes(c,"select not (rolsuper or rolcreatedb or rolcreaterole or rolbypassrls) from pg_roles where rolname=current_user")) return false;
        if (!yes(c,"select not has_schema_privilege(current_user,current_schema(),'CREATE')")) return false;
        if (!yes(c,"select not exists(select from pg_class t join pg_namespace n on n.oid=t.relnamespace where n.nspname=current_schema() and t.relkind in ('r','p') and pg_has_role(current_user,t.relowner,'MEMBER'))")) return false;
        if (!yes(c,"select not has_table_privilege(current_user,'flyway_schema_history','INSERT,UPDATE,DELETE,TRUNCATE')")) return false;
        if (!yes(c,"select not has_function_privilege(current_user,'fitcore_scope_catalogs()','EXECUTE')")) return false;
        String requiredTables = "('account_challenges'),('security_rate_limits'),('gyms'),('gym_invitations'),('users'),('active_memberships'),('attendance'),('exercises'),('food_logs'),('membership_plans'),('membership_plan_features'),('notifications'),('nutrition_goals'),('nutrition_plans'),('nutrition_plan_meals'),('progress_metrics'),('trainer_profiles'),('trainer_sessions'),('trainer_available_slots'),('trainer_certifications'),('workout_logs'),('workout_exercise_logs'),('workout_plans'),('workout_plan_exercises'),('workout_plan_schedule')";
        if (!yes(c,"select bool_and(has_table_privilege(current_user,to_regclass(t),'SELECT') and has_table_privilege(current_user,to_regclass(t),'INSERT') and has_table_privilege(current_user,to_regclass(t),'UPDATE') and has_table_privilege(current_user,to_regclass(t),'DELETE')) and count(to_regclass(t))=25 from (values "+requiredTables+") required(t)")) return false;
        if (!yes(c,"select coalesce(bool_and(has_sequence_privilege(current_user,quote_ident(schemaname)||'.'||quote_ident(sequencename),'USAGE') and has_sequence_privilege(current_user,quote_ident(schemaname)||'.'||quote_ident(sequencename),'SELECT')),true) from pg_sequences where schemaname=current_schema()")) return false;
        if (!yes(c,"select coalesce(string_agg(version,',' order by installed_rank),'')='1,2,4,5,6,7' and bool_and(success) from flyway_schema_history")) return false;
        return SchemaContract.ACCEPTED.contains(SchemaContract.fingerprint(c));
    }
}
