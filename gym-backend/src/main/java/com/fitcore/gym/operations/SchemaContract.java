package com.fitcore.gym.operations;

import java.sql.*;

/** Canonical PostgreSQL18 V9 DDL/default contract; excludes data, owners, ACLs and Flyway bookkeeping. */
public final class SchemaContract {
    private SchemaContract() {}
    // V9 contracts certify columns INCLUDING defaults and every constraint/index/trigger/function.
    // Exact fresh, staged archive upgrade and full archive restore parser representations only.
    public static final java.util.Set<String> ACCEPTED = java.util.Set.of(
        "257f2bf8e720e2ecb27c516ecbd216d4f16b77581abd9312b432821d2da42c7f",
        "a37bfba3f80e1e41c445e62baeed36182e9bdb6d6b5e3852ee8888d69c5ea9c7",
        "b64ef5d178b784b4697de334a688a57af484a3b5c2108deb9b1571b0e1f5c4ad",
        "6b752e4590ff80986b2ee38be642a7e17e67949aa095c893e9c081348b9de8f5",
        "95097ba59ee4c182505e1946fdecab880092ae30a196e30a1dc29a941644e487");
    public static String fingerprint(Connection connection) throws SQLException {
        String sql = """
            select encode(sha256(convert_to(
            coalesce((select string_agg(table_name||':'||column_name||':'||udt_name||':'||coalesce(character_maximum_length::text,'')||':'||coalesce(numeric_precision::text,'')||':'||coalesce(numeric_scale::text,'')||':'||coalesce(datetime_precision::text,'')||':'||is_nullable||':'||is_identity||':'||replace(coalesce(column_default,''),current_schema()||'.',''),'|' order by table_name,ordinal_position) from information_schema.columns where table_schema=current_schema() and table_name<>'flyway_schema_history'),'') || '#' ||
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
