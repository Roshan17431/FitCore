package com.fitcore.gym.operations;

import java.sql.*;

/** Canonical PostgreSQL18 DDL contract; excludes data, owners, ACLs and Flyway bookkeeping. */
public final class SchemaContract {
    private SchemaContract() {}
    // PostgreSQL18 re-parses the V6 enum check's array casts when pg_restore recreates it.
    // Accept exactly the two reviewed complete descriptors, not arbitrary semantic drift.
    public static final java.util.Set<String> ACCEPTED = java.util.Set.of(
        "5f9f9f93a3f880f0bc503800cd5e83589adb7ceecfd1cfc048d3149ecf838a7c",
        "066e6402a356035b983a81fc346a6bf15dd9cdaa4b32bff66421ea8a2e454940");
    public static String fingerprint(Connection connection) throws SQLException {
        String sql = """
            select encode(sha256(convert_to(
            coalesce((select string_agg(table_name||':'||column_name||':'||udt_name||':'||coalesce(character_maximum_length::text,'')||':'||coalesce(numeric_precision::text,'')||':'||coalesce(numeric_scale::text,'')||':'||coalesce(datetime_precision::text,'')||':'||is_nullable||':'||is_identity,'|' order by table_name,ordinal_position) from information_schema.columns where table_schema=current_schema() and table_name<>'flyway_schema_history'),'') || '#' ||
            coalesce((select string_agg(t.relname||':'||c.conname||':'||c.convalidated::text||':'||replace(pg_get_constraintdef(c.oid),current_schema()||'.',''),'|' order by t.relname,c.conname) from pg_constraint c join pg_class t on t.oid=c.conrelid join pg_namespace n on n.oid=t.relnamespace where n.nspname=current_schema() and t.relname<>'flyway_schema_history'),'') || '#' ||
            coalesce((select string_agg(indexname||':'||replace(indexdef,current_schema()||'.',''),'|' order by indexname) from pg_indexes where schemaname=current_schema() and tablename<>'flyway_schema_history'),'') || '#' ||
            coalesce((select string_agg(t.relname||':'||t.relkind::text,'|' order by t.relname) from pg_class t join pg_namespace n on n.oid=t.relnamespace where n.nspname=current_schema() and t.relname not like 'flyway_schema_history%'),'') || '#' ||
            coalesce((select string_agg(t.tgname||':'||t.tgenabled::text||':'||replace(pg_get_triggerdef(t.oid),current_schema()||'.',''),'|' order by c.relname,t.tgname) from pg_trigger t join pg_class c on c.oid=t.tgrelid join pg_namespace n on n.oid=c.relnamespace where n.nspname=current_schema() and not t.tgisinternal and c.relname<>'flyway_schema_history'),'') || '#' ||
            coalesce((select string_agg(replace(pg_get_functiondef(p.oid),current_schema(),'fitcore_schema'),'|' order by p.proname,pg_get_function_identity_arguments(p.oid)) from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname=current_schema()),'')
            ,'UTF8')),'hex')
            """;
        try (var statement=connection.createStatement()) {
            statement.setQueryTimeout(2);
            try(var result=statement.executeQuery(sql)) { if(!result.next())throw new SQLException("Missing schema contract");return result.getString(1); }
        }
    }
}
