package com.fitcore.gym.tenancy;

import java.sql.*;
import java.util.*;

/** Test-only independent metadata inventory; never used to relax the production hash. */
final class SchemaDescriptorSnapshot {
    private SchemaDescriptorSnapshot() {}
    static Map<String,String> collect(Connection c)throws SQLException {
        var out=new TreeMap<String,String>();String schema=c.getSchema();
        collect(c,out,schema,"column", "table_name||':'||column_name", """
            select table_name,column_name,ordinal_position,data_type,udt_name,is_nullable,column_default,
             character_maximum_length,numeric_precision,numeric_scale,datetime_precision,
             is_identity,identity_generation,identity_start,identity_increment,identity_minimum,identity_maximum,identity_cycle,
             is_generated,generation_expression,collation_name
            from information_schema.columns where table_schema=current_schema() and table_name<>'flyway_schema_history'
            """);
        collect(c,out,schema,"constraint","relation||':'||name", """
            select t.relname as relation,c.conname as name,c.contype,c.convalidated,c.condeferrable,c.condeferred,
             pg_get_constraintdef(c.oid) as definition
            from pg_constraint c join pg_class t on t.oid=c.conrelid join pg_namespace n on n.oid=t.relnamespace
            where n.nspname=current_schema() and t.relname<>'flyway_schema_history'
            """);
        collect(c,out,schema,"index","relation||':'||name", """
            select t.relname as relation,i.relname as name,pg_get_indexdef(x.indexrelid) as definition,
             x.indisvalid,x.indisready,x.indisunique,x.indisprimary,x.indisexclusion,x.indimmediate,x.indnullsnotdistinct
            from pg_index x join pg_class i on i.oid=x.indexrelid join pg_class t on t.oid=x.indrelid
             join pg_namespace n on n.oid=t.relnamespace
            where n.nspname=current_schema() and t.relname<>'flyway_schema_history'
            """);
        collect(c,out,schema,"trigger","relation||':'||name", """
            select c.relname as relation,t.tgname as name,t.tgenabled,t.tgdeferrable,t.tginitdeferred,
             pg_get_triggerdef(t.oid) as definition
            from pg_trigger t join pg_class c on c.oid=t.tgrelid join pg_namespace n on n.oid=c.relnamespace
            where n.nspname=current_schema() and not t.tgisinternal and c.relname<>'flyway_schema_history'
            """);
        collect(c,out,schema,"internal_trigger","relation||':'||name||':'||function_name||':'||tgtype", """
            select c.relname as relation,coalesce(k.conname,'') as name,p.proname as function_name,
             t.tgtype,t.tgenabled,t.tgdeferrable,t.tginitdeferred
            from pg_trigger t join pg_class c on c.oid=t.tgrelid join pg_namespace n on n.oid=c.relnamespace
             join pg_proc p on p.oid=t.tgfoid left join pg_constraint k on k.oid=t.tgconstraint
            where n.nspname=current_schema() and t.tgisinternal and c.relname<>'flyway_schema_history'
            """);
        collect(c,out,schema,"function","name||':'||arguments", """
            select p.proname as name,pg_get_function_identity_arguments(p.oid) as arguments,
             pg_get_functiondef(p.oid) as definition,p.prosecdef,p.provolatile,p.proisstrict,p.proleakproof,p.proparallel,p.proconfig
            from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname=current_schema()
            """);
        collect(c,out,schema,"relation","name", """
            select t.relname as name,t.relkind,t.relpersistence,t.relrowsecurity,t.relforcerowsecurity,t.relreplident,t.reloptions,
             pg_get_partkeydef(t.oid) as partition_key,pg_get_expr(t.relpartbound,t.oid) as partition_bound
            from pg_class t join pg_namespace n on n.oid=t.relnamespace
            where n.nspname=current_schema() and t.relname not like 'flyway_schema_history%'
            """);
        collect(c,out,schema,"sequence","name", """
            select t.relname as name,format_type(s.seqtypid,null) as type,s.seqstart,s.seqincrement,s.seqmax,s.seqmin,s.seqcache,s.seqcycle
            from pg_sequence s join pg_class t on t.oid=s.seqrelid join pg_namespace n on n.oid=t.relnamespace
            where n.nspname=current_schema()
            """);
        collect(c,out,schema,"sequence_owner","name", """
            select seq.relname as name,t.relname as relation,a.attname as column_name,d.deptype
            from pg_class seq join pg_namespace n on n.oid=seq.relnamespace
             join pg_depend d on d.classid='pg_class'::regclass and d.objid=seq.oid and d.refclassid='pg_class'::regclass and d.deptype in ('a','i')
             join pg_class t on t.oid=d.refobjid join pg_attribute a on a.attrelid=t.oid and a.attnum=d.refobjsubid
            where n.nspname=current_schema() and seq.relkind='S'
            """);
        collect(c,out,schema,"policy","relation||':'||name", """
            select t.relname as relation,p.polname as name,p.polcmd,p.polpermissive,
             pg_get_expr(p.polqual,p.polrelid) as predicate,pg_get_expr(p.polwithcheck,p.polrelid) as check_expression
            from pg_policy p join pg_class t on t.oid=p.polrelid join pg_namespace n on n.oid=t.relnamespace
            where n.nspname=current_schema()
            """);
        return out;
    }
    private static void collect(Connection c,Map<String,String> out,String schema,String kind,String key,String query)throws SQLException {
        try(var s=c.createStatement();var r=s.executeQuery("select "+key+",row_to_json(q)::text from ("+query+") q order by 1")){
            while(r.next())if(out.put(kind+":"+r.getString(1),r.getString(2).replace(schema,"fitcore_schema"))!=null)
                throw new SQLException("Duplicate schema inventory identity");
        }
    }
}
