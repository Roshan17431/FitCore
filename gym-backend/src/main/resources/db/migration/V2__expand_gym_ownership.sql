-- Expansion only: no inferred accounts, gym assignments or catalog copies.
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
DO $baseline$ DECLARE actual text; BEGIN
SELECT encode(sha256(convert_to(
coalesce((SELECT string_agg(table_name||':'||column_name||':'||udt_name||':'||coalesce(character_maximum_length::text,'')||':'||coalesce(numeric_precision::text,'')||':'||coalesce(numeric_scale::text,'')||':'||coalesce(datetime_precision::text,'')||':'||is_nullable||':'||is_identity,'|' ORDER BY table_name,ordinal_position) FROM information_schema.columns WHERE table_schema=current_schema() AND table_name <> 'flyway_schema_history'),'') || '#' ||
coalesce((SELECT string_agg(t.relname||':'||c.conname||':'||replace(pg_get_constraintdef(c.oid),current_schema()||'.',''),'|' ORDER BY t.relname,c.conname) FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace WHERE n.nspname=current_schema() AND t.relname <> 'flyway_schema_history'),'') || '#' ||
coalesce((SELECT string_agg(indexname||':'||replace(indexdef,current_schema()||'.',''),'|' ORDER BY indexname) FROM pg_indexes WHERE schemaname=current_schema() AND tablename <> 'flyway_schema_history'),'') || '#' ||
coalesce((SELECT string_agg(t.relname||':'||t.relkind::text,'|' ORDER BY t.relname) FROM pg_class t JOIN pg_namespace n ON n.oid=t.relnamespace WHERE n.nspname=current_schema() AND t.relname NOT LIKE 'flyway_schema_history%'),'') || '#' ||
coalesce((SELECT string_agg(t.tgname||':'||pg_get_triggerdef(t.oid),'|' ORDER BY t.tgname) FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname=current_schema() AND NOT t.tgisinternal AND c.relname <> 'flyway_schema_history'),'')
,'UTF8')),'hex') INTO actual;
IF actual <> '05862d46dbab8f5543d5958dcd1509a3d5830e1e8b4dbecda75be674d15454ce' THEN RAISE EXCEPTION 'Baseline schema mismatch: explicit schema review required'; END IF;
END $baseline$;
CREATE TABLE gyms (
 id uuid PRIMARY KEY, name varchar(255) NOT NULL, slug varchar(255) NOT NULL UNIQUE,
 status varchar(255) NOT NULL CHECK(status IN ('ACTIVE','SUSPENDED')),
 primary_owner_user_id uuid, created_at timestamp(6) NOT NULL, updated_at timestamp(6) NOT NULL
);
CREATE TABLE gym_invitations (
 id uuid PRIMARY KEY, gym_id uuid NOT NULL, email varchar(255) NOT NULL,
 token_hash varchar(255) NOT NULL UNIQUE, role varchar(255) NOT NULL DEFAULT 'MEMBER' CHECK(role='MEMBER'),
 created_by_user_id uuid NOT NULL, expires_at timestamp(6) NOT NULL,
 accepted_at timestamp(6), revoked_at timestamp(6), created_at timestamp(6) NOT NULL
);
ALTER TABLE users ADD COLUMN authz_version bigint NOT NULL DEFAULT 0;
ALTER TABLE users DROP CONSTRAINT users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK(role IN ('MEMBER','TRAINER','ADMIN','OWNER'));
ALTER TABLE users ADD COLUMN gym_id uuid;
ALTER TABLE active_memberships ADD COLUMN gym_id uuid;
ALTER TABLE attendance ADD COLUMN gym_id uuid;
ALTER TABLE exercises ADD COLUMN gym_id uuid;
ALTER TABLE food_logs ADD COLUMN gym_id uuid;
ALTER TABLE membership_plans ADD COLUMN gym_id uuid;
ALTER TABLE notifications ADD COLUMN gym_id uuid;
ALTER TABLE nutrition_goals ADD COLUMN gym_id uuid;
ALTER TABLE nutrition_plans ADD COLUMN gym_id uuid;
ALTER TABLE progress_metrics ADD COLUMN gym_id uuid;
ALTER TABLE trainer_profiles ADD COLUMN gym_id uuid;
ALTER TABLE trainer_sessions ADD COLUMN gym_id uuid;
ALTER TABLE workout_exercise_logs ADD COLUMN gym_id uuid;
ALTER TABLE workout_logs ADD COLUMN gym_id uuid;
ALTER TABLE workout_plans ADD COLUMN gym_id uuid;
ALTER TABLE membership_plan_features ADD COLUMN gym_id uuid;
ALTER TABLE nutrition_plan_meals ADD COLUMN gym_id uuid;
ALTER TABLE trainer_available_slots ADD COLUMN gym_id uuid;
ALTER TABLE trainer_certifications ADD COLUMN gym_id uuid;
ALTER TABLE workout_plan_exercises ADD COLUMN gym_id uuid;
ALTER TABLE workout_plan_schedule ADD COLUMN gym_id uuid;
ALTER TABLE users ADD CONSTRAINT uq_users_gym_id UNIQUE(gym_id,id);
ALTER TABLE active_memberships ADD CONSTRAINT uq_active_memberships_gym_id UNIQUE(gym_id,id);
ALTER TABLE attendance ADD CONSTRAINT uq_attendance_gym_id UNIQUE(gym_id,id);
ALTER TABLE exercises ADD CONSTRAINT uq_exercises_gym_id UNIQUE(gym_id,id);
ALTER TABLE food_logs ADD CONSTRAINT uq_food_logs_gym_id UNIQUE(gym_id,id);
ALTER TABLE membership_plans ADD CONSTRAINT uq_membership_plans_gym_id UNIQUE(gym_id,id);
ALTER TABLE notifications ADD CONSTRAINT uq_notifications_gym_id UNIQUE(gym_id,id);
ALTER TABLE nutrition_goals ADD CONSTRAINT uq_nutrition_goals_gym_id UNIQUE(gym_id,id);
ALTER TABLE nutrition_plans ADD CONSTRAINT uq_nutrition_plans_gym_id UNIQUE(gym_id,id);
ALTER TABLE progress_metrics ADD CONSTRAINT uq_progress_metrics_gym_id UNIQUE(gym_id,id);
ALTER TABLE trainer_profiles ADD CONSTRAINT uq_trainer_profiles_gym_id UNIQUE(gym_id,id);
ALTER TABLE trainer_sessions ADD CONSTRAINT uq_trainer_sessions_gym_id UNIQUE(gym_id,id);
ALTER TABLE workout_exercise_logs ADD CONSTRAINT uq_workout_exercise_logs_gym_id UNIQUE(gym_id,id);
ALTER TABLE workout_logs ADD CONSTRAINT uq_workout_logs_gym_id UNIQUE(gym_id,id);
ALTER TABLE workout_plans ADD CONSTRAINT uq_workout_plans_gym_id UNIQUE(gym_id,id);

-- Called inside a future approved mapping transaction AFTER original catalog
-- ownership assignment and BEFORE identical clones. Not a public runtime API.
CREATE FUNCTION fitcore_scope_catalogs() RETURNS void LANGUAGE plpgsql SET search_path FROM CURRENT AS $scope$
DECLARE t text; col text; key text;
BEGIN
 FOR t,col IN SELECT * FROM (VALUES ('membership_plans','name'),('exercises','name'),('workout_plans','level'),('nutrition_plans','category')) p(t,c)
 LOOP
  IF EXISTS (SELECT FROM pg_class WHERE oid=to_regclass(t)) THEN
   EXECUTE format('SELECT EXISTS(SELECT FROM %I WHERE gym_id IS NULL)',t) INTO STRICT key;
   IF key::boolean THEN RAISE EXCEPTION 'Explicit catalog ownership mapping required'; END IF;
   IF NOT EXISTS(SELECT FROM pg_constraint WHERE conrelid=to_regclass(t) AND conname='uq_'||t||'_gym_'||col) THEN
    EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I UNIQUE(gym_id,%I)',t,'uq_'||t||'_gym_'||col,col);
   END IF;
   EXECUTE format('ALTER TABLE %I DROP CONSTRAINT IF EXISTS %I',t,t||'_'||col||'_key');
  END IF;
 END LOOP;
END $scope$;
REVOKE ALL ON FUNCTION fitcore_scope_catalogs() FROM PUBLIC;
