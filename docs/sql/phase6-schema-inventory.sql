-- PREPARED ONLY. Do not execute against customer data without separate access approval.
-- psql --no-psqlrc --set ON_ERROR_STOP=1 --set review_schema=<approved-schema> --file ...
-- Store output privately. This inventories metadata, not customer rows or password hashes.
BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout='15s';
SET LOCAL lock_timeout='2s';
SELECT current_database(), current_user, version(), current_setting('TimeZone');
SELECT nspname, pg_get_userbyid(nspowner) AS owner, nspacl
  FROM pg_namespace WHERE nspname=:'review_schema';
SELECT table_name,column_name,ordinal_position,data_type,udt_name,is_nullable,column_default,
       character_maximum_length,numeric_precision,numeric_scale,datetime_precision
  FROM information_schema.columns WHERE table_schema=:'review_schema' ORDER BY table_name,ordinal_position;
SELECT t.relname,c.conname,c.contype,c.convalidated,c.condeferrable,c.condeferred,pg_get_constraintdef(c.oid)
  FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace
 WHERE n.nspname=:'review_schema' ORDER BY t.relname,c.conname;
SELECT tablename,indexname,indexdef FROM pg_indexes WHERE schemaname=:'review_schema' ORDER BY tablename,indexname;
SELECT c.relname,t.tgname,t.tgenabled,pg_get_triggerdef(t.oid)
  FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid JOIN pg_namespace n ON n.oid=c.relnamespace
 WHERE n.nspname=:'review_schema' AND NOT t.tgisinternal ORDER BY c.relname,t.tgname;
SELECT p.proname,p.prosecdef,p.proconfig,p.proacl,pg_get_functiondef(p.oid)
  FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
 WHERE n.nspname=:'review_schema' AND p.prokind='f' ORDER BY p.proname,p.oid;
SELECT t.relname,t.relkind,pg_get_userbyid(t.relowner) AS owner,t.relacl
  FROM pg_class t JOIN pg_namespace n ON n.oid=t.relnamespace
 WHERE n.nspname=:'review_schema' ORDER BY t.relname;
SELECT rolname,rolsuper,rolinherit,rolcreaterole,rolcreatedb,rolcanlogin,rolbypassrls
 FROM pg_roles WHERE rolname=current_user;
SELECT to_regclass(format('%I.flyway_schema_history', :'review_schema')) AS history_relation;
-- If present, separately review this schema's Flyway history with the approved read role.
-- Never baseline an unknown live schema automatically; no Flyway clean/repair is authorized.
COMMIT;
