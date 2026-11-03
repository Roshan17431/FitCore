package com.fitcore.gym.operations;

import java.sql.*;

/** Canonical PostgreSQL18 DDL contract; excludes data, owners, ACLs and Flyway bookkeeping. */
public final class SchemaContract {
    private SchemaContract() {}
    // PostgreSQL18 renders varchar-array casts differently after pg_restore.
    // Exact certified staged-upgrade, clean V8 and full V8 restore contracts only;
    // descriptor construction and every CHECK/index/function/trigger remain enforced.
    public static final java.util.Set<String> ACCEPTED = java.util.Set.of(
        "7f6e327b3b23cd7ae5521130c1e21383ef93683fa3caf35d2862f4b00d97206e",
        "260256f9538c02f81352fd57fe541eaf95f53d8db171ecbd0fdac535fe27c2f7",
        "6953167a756786a1de56dd68acb7940e5ae02a710d803d5f18cc737f380f24ca",
        "7f870102345b628218d6575c1c1dbe67408ad05b69e0d5c90a1d9d5ec6fefc1c");
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
