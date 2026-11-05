package com.fitcore.gym.operations;

import java.sql.*;

/** Canonical PostgreSQL18 V10 DDL/default contract; excludes data, owners, ACLs and Flyway bookkeeping. */
public final class SchemaContract {
    private SchemaContract() {}
    // V10 contracts certify columns INCLUDING defaults and every constraint/index/trigger/function.
    // Exact fresh, staged archive upgrade and full archive restore parser representations only.
    public static final java.util.Set<String> ACCEPTED = java.util.Set.of(
        "a21ab3b9248dcb45a7e85957bb2987b6a53b521db8233fa4a6fd3b413bc8c681",
        "c14f022fb8ed56bec51719b37b7c2c5e3ea8d194282463638e84d3d42e1af37b",
        "cd7a0c70c55f7ea4f49b5e311f321a229e4b43756b412fa84a2b2c0ffc473fd9",
        "83b2451155f889ce68955f4bba17fa5bf9476ae1435e2ec0814da80c4912f76d",
        "083367038ace44ff0908fc59212102943e2aabad68ffe4d46c94c070dd944710",
        "185cbd2dcb0f21ce7d1626a4947078afc9eb4c898a55f064bf0ac2dde0bbc528");
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
