-- V3 is reserved for the separately reviewed operational mapping runner.
-- During foundation rehearsals, populated fixtures are explicitly mapped at V2.
-- Never apply this foundation migration set to a persistent customer database.
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
DO $mapping$ DECLARE t text; missing boolean; BEGIN
IF EXISTS(SELECT FROM users WHERE gym_id IS NULL) THEN RAISE EXCEPTION 'Explicit ownership mapping required for users'; END IF;
IF EXISTS(SELECT FROM active_memberships WHERE gym_id IS NULL) THEN RAISE EXCEPTION 'Explicit ownership mapping required for active_memberships'; END IF;
IF EXISTS(SELECT FROM attendance WHERE gym_id IS NULL) THEN RAISE EXCEPTION 'Explicit ownership mapping required for attendance'; END IF;
IF EXISTS(SELECT FROM exercises WHERE gym_id IS NULL) THEN RAISE EXCEPTION 'Explicit ownership mapping required for exercises'; END IF;
IF EXISTS(SELECT FROM food_logs WHERE gym_id IS NULL) THEN RAISE EXCEPTION 'Explicit ownership mapping required for food_logs'; END IF;
IF EXISTS(SELECT FROM membership_plans WHERE gym_id IS NULL) THEN RAISE EXCEPTION 'Explicit ownership mapping required for membership_plans'; END IF;
IF EXISTS(SELECT FROM notifications WHERE gym_id IS NULL) THEN RAISE EXCEPTION 'Explicit ownership mapping required for notifications'; END IF;
IF EXISTS(SELECT FROM nutrition_goals WHERE gym_id IS NULL) THEN RAISE EXCEPTION 'Explicit ownership mapping required for nutrition_goals'; END IF;
IF EXISTS(SELECT FROM nutrition_plans WHERE gym_id IS NULL) THEN RAISE EXCEPTION 'Explicit ownership mapping required for nutrition_plans'; END IF;
IF EXISTS(SELECT FROM progress_metrics WHERE gym_id IS NULL) THEN RAISE EXCEPTION 'Explicit ownership mapping required for progress_metrics'; END IF;
IF EXISTS(SELECT FROM trainer_profiles WHERE gym_id IS NULL) THEN RAISE EXCEPTION 'Explicit ownership mapping required for trainer_profiles'; END IF;
IF EXISTS(SELECT FROM trainer_sessions WHERE gym_id IS NULL) THEN RAISE EXCEPTION 'Explicit ownership mapping required for trainer_sessions'; END IF;
IF EXISTS(SELECT FROM workout_exercise_logs WHERE gym_id IS NULL) THEN RAISE EXCEPTION 'Explicit ownership mapping required for workout_exercise_logs'; END IF;
IF EXISTS(SELECT FROM workout_logs WHERE gym_id IS NULL) THEN RAISE EXCEPTION 'Explicit ownership mapping required for workout_logs'; END IF;
IF EXISTS(SELECT FROM workout_plans WHERE gym_id IS NULL) THEN RAISE EXCEPTION 'Explicit ownership mapping required for workout_plans'; END IF;
IF EXISTS(SELECT FROM membership_plan_features WHERE gym_id IS NULL) THEN RAISE EXCEPTION 'Explicit ownership mapping required for membership_plan_features'; END IF;
IF EXISTS(SELECT FROM nutrition_plan_meals WHERE gym_id IS NULL) THEN RAISE EXCEPTION 'Explicit ownership mapping required for nutrition_plan_meals'; END IF;
IF EXISTS(SELECT FROM trainer_available_slots WHERE gym_id IS NULL) THEN RAISE EXCEPTION 'Explicit ownership mapping required for trainer_available_slots'; END IF;
IF EXISTS(SELECT FROM trainer_certifications WHERE gym_id IS NULL) THEN RAISE EXCEPTION 'Explicit ownership mapping required for trainer_certifications'; END IF;
IF EXISTS(SELECT FROM workout_plan_exercises WHERE gym_id IS NULL) THEN RAISE EXCEPTION 'Explicit ownership mapping required for workout_plan_exercises'; END IF;
IF EXISTS(SELECT FROM workout_plan_schedule WHERE gym_id IS NULL) THEN RAISE EXCEPTION 'Explicit ownership mapping required for workout_plan_schedule'; END IF;
END $mapping$;
SELECT fitcore_scope_catalogs();
ALTER TABLE users ALTER COLUMN gym_id SET NOT NULL;
ALTER TABLE users ADD CONSTRAINT fk_users_gym FOREIGN KEY(gym_id) REFERENCES gyms(id);
ALTER TABLE active_memberships ALTER COLUMN gym_id SET NOT NULL;
ALTER TABLE active_memberships ADD CONSTRAINT fk_active_memberships_gym FOREIGN KEY(gym_id) REFERENCES gyms(id);
ALTER TABLE attendance ALTER COLUMN gym_id SET NOT NULL;
ALTER TABLE attendance ADD CONSTRAINT fk_attendance_gym FOREIGN KEY(gym_id) REFERENCES gyms(id);
ALTER TABLE exercises ALTER COLUMN gym_id SET NOT NULL;
ALTER TABLE exercises ADD CONSTRAINT fk_exercises_gym FOREIGN KEY(gym_id) REFERENCES gyms(id);
ALTER TABLE food_logs ALTER COLUMN gym_id SET NOT NULL;
ALTER TABLE food_logs ADD CONSTRAINT fk_food_logs_gym FOREIGN KEY(gym_id) REFERENCES gyms(id);
ALTER TABLE membership_plans ALTER COLUMN gym_id SET NOT NULL;
ALTER TABLE membership_plans ADD CONSTRAINT fk_membership_plans_gym FOREIGN KEY(gym_id) REFERENCES gyms(id);
ALTER TABLE notifications ALTER COLUMN gym_id SET NOT NULL;
ALTER TABLE notifications ADD CONSTRAINT fk_notifications_gym FOREIGN KEY(gym_id) REFERENCES gyms(id);
ALTER TABLE nutrition_goals ALTER COLUMN gym_id SET NOT NULL;
ALTER TABLE nutrition_goals ADD CONSTRAINT fk_nutrition_goals_gym FOREIGN KEY(gym_id) REFERENCES gyms(id);
ALTER TABLE nutrition_plans ALTER COLUMN gym_id SET NOT NULL;
ALTER TABLE nutrition_plans ADD CONSTRAINT fk_nutrition_plans_gym FOREIGN KEY(gym_id) REFERENCES gyms(id);
ALTER TABLE progress_metrics ALTER COLUMN gym_id SET NOT NULL;
ALTER TABLE progress_metrics ADD CONSTRAINT fk_progress_metrics_gym FOREIGN KEY(gym_id) REFERENCES gyms(id);
ALTER TABLE trainer_profiles ALTER COLUMN gym_id SET NOT NULL;
ALTER TABLE trainer_profiles ADD CONSTRAINT fk_trainer_profiles_gym FOREIGN KEY(gym_id) REFERENCES gyms(id);
ALTER TABLE trainer_sessions ALTER COLUMN gym_id SET NOT NULL;
ALTER TABLE trainer_sessions ADD CONSTRAINT fk_trainer_sessions_gym FOREIGN KEY(gym_id) REFERENCES gyms(id);
ALTER TABLE workout_exercise_logs ALTER COLUMN gym_id SET NOT NULL;
ALTER TABLE workout_exercise_logs ADD CONSTRAINT fk_workout_exercise_logs_gym FOREIGN KEY(gym_id) REFERENCES gyms(id);
ALTER TABLE workout_logs ALTER COLUMN gym_id SET NOT NULL;
ALTER TABLE workout_logs ADD CONSTRAINT fk_workout_logs_gym FOREIGN KEY(gym_id) REFERENCES gyms(id);
ALTER TABLE workout_plans ALTER COLUMN gym_id SET NOT NULL;
ALTER TABLE workout_plans ADD CONSTRAINT fk_workout_plans_gym FOREIGN KEY(gym_id) REFERENCES gyms(id);
ALTER TABLE membership_plan_features ALTER COLUMN gym_id SET NOT NULL;
ALTER TABLE membership_plan_features ADD CONSTRAINT fk_membership_plan_features_gym FOREIGN KEY(gym_id) REFERENCES gyms(id);
ALTER TABLE nutrition_plan_meals ALTER COLUMN gym_id SET NOT NULL;
ALTER TABLE nutrition_plan_meals ADD CONSTRAINT fk_nutrition_plan_meals_gym FOREIGN KEY(gym_id) REFERENCES gyms(id);
ALTER TABLE trainer_available_slots ALTER COLUMN gym_id SET NOT NULL;
ALTER TABLE trainer_available_slots ADD CONSTRAINT fk_trainer_available_slots_gym FOREIGN KEY(gym_id) REFERENCES gyms(id);
ALTER TABLE trainer_certifications ALTER COLUMN gym_id SET NOT NULL;
ALTER TABLE trainer_certifications ADD CONSTRAINT fk_trainer_certifications_gym FOREIGN KEY(gym_id) REFERENCES gyms(id);
ALTER TABLE workout_plan_exercises ALTER COLUMN gym_id SET NOT NULL;
ALTER TABLE workout_plan_exercises ADD CONSTRAINT fk_workout_plan_exercises_gym FOREIGN KEY(gym_id) REFERENCES gyms(id);
ALTER TABLE workout_plan_schedule ALTER COLUMN gym_id SET NOT NULL;
ALTER TABLE workout_plan_schedule ADD CONSTRAINT fk_workout_plan_schedule_gym FOREIGN KEY(gym_id) REFERENCES gyms(id);
ALTER TABLE active_memberships ADD CONSTRAINT fk_active_memberships_user_id_gym FOREIGN KEY(gym_id,user_id) REFERENCES users(gym_id,id);
ALTER TABLE active_memberships ADD CONSTRAINT fk_active_memberships_plan_id_gym FOREIGN KEY(gym_id,plan_id) REFERENCES membership_plans(gym_id,id);
ALTER TABLE attendance ADD CONSTRAINT fk_attendance_user_id_gym FOREIGN KEY(gym_id,user_id) REFERENCES users(gym_id,id);
ALTER TABLE food_logs ADD CONSTRAINT fk_food_logs_user_id_gym FOREIGN KEY(gym_id,user_id) REFERENCES users(gym_id,id);
ALTER TABLE notifications ADD CONSTRAINT fk_notifications_user_id_gym FOREIGN KEY(gym_id,user_id) REFERENCES users(gym_id,id);
ALTER TABLE nutrition_goals ADD CONSTRAINT fk_nutrition_goals_user_id_gym FOREIGN KEY(gym_id,user_id) REFERENCES users(gym_id,id);
ALTER TABLE progress_metrics ADD CONSTRAINT fk_progress_metrics_user_id_gym FOREIGN KEY(gym_id,user_id) REFERENCES users(gym_id,id);
ALTER TABLE trainer_profiles ADD CONSTRAINT fk_trainer_profiles_user_id_gym FOREIGN KEY(gym_id,user_id) REFERENCES users(gym_id,id);
ALTER TABLE trainer_sessions ADD CONSTRAINT fk_trainer_sessions_member_id_gym FOREIGN KEY(gym_id,member_id) REFERENCES users(gym_id,id);
ALTER TABLE trainer_sessions ADD CONSTRAINT fk_trainer_sessions_trainer_id_gym FOREIGN KEY(gym_id,trainer_id) REFERENCES trainer_profiles(gym_id,id);
ALTER TABLE workout_exercise_logs ADD CONSTRAINT fk_workout_exercise_logs_workout_log_id_gym FOREIGN KEY(gym_id,workout_log_id) REFERENCES workout_logs(gym_id,id);
ALTER TABLE workout_exercise_logs ADD CONSTRAINT fk_workout_exercise_logs_exercise_id_gym FOREIGN KEY(gym_id,exercise_id) REFERENCES exercises(gym_id,id);
ALTER TABLE workout_logs ADD CONSTRAINT fk_workout_logs_user_id_gym FOREIGN KEY(gym_id,user_id) REFERENCES users(gym_id,id);
ALTER TABLE membership_plan_features ADD CONSTRAINT fk_membership_plan_features_plan_id_gym FOREIGN KEY(gym_id,plan_id) REFERENCES membership_plans(gym_id,id);
ALTER TABLE nutrition_plan_meals ADD CONSTRAINT fk_nutrition_plan_meals_plan_id_gym FOREIGN KEY(gym_id,plan_id) REFERENCES nutrition_plans(gym_id,id);
ALTER TABLE trainer_available_slots ADD CONSTRAINT fk_trainer_available_slots_trainer_id_gym FOREIGN KEY(gym_id,trainer_id) REFERENCES trainer_profiles(gym_id,id);
ALTER TABLE trainer_certifications ADD CONSTRAINT fk_trainer_certifications_trainer_id_gym FOREIGN KEY(gym_id,trainer_id) REFERENCES trainer_profiles(gym_id,id);
ALTER TABLE workout_plan_exercises ADD CONSTRAINT fk_workout_plan_exercises_plan_id_gym FOREIGN KEY(gym_id,plan_id) REFERENCES workout_plans(gym_id,id);
ALTER TABLE workout_plan_schedule ADD CONSTRAINT fk_workout_plan_schedule_plan_id_gym FOREIGN KEY(gym_id,plan_id) REFERENCES workout_plans(gym_id,id);
ALTER TABLE gym_invitations ADD CONSTRAINT fk_invitation_gym FOREIGN KEY(gym_id) REFERENCES gyms(id);
ALTER TABLE gym_invitations ADD CONSTRAINT fk_invitation_creator_gym FOREIGN KEY(gym_id,created_by_user_id) REFERENCES users(gym_id,id);
ALTER TABLE gyms ADD CONSTRAINT fk_gym_primary_owner FOREIGN KEY(id,primary_owner_user_id) REFERENCES users(gym_id,id) DEFERRABLE INITIALLY DEFERRED;

CREATE FUNCTION fitcore_immutable_gym() RETURNS trigger LANGUAGE plpgsql AS $immutable$
BEGIN
 IF NEW.gym_id IS DISTINCT FROM OLD.gym_id THEN RAISE EXCEPTION 'Gym ownership is immutable' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $immutable$;
CREATE TRIGGER immutable_gym BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION fitcore_immutable_gym();
CREATE TRIGGER immutable_gym BEFORE UPDATE ON active_memberships FOR EACH ROW EXECUTE FUNCTION fitcore_immutable_gym();
CREATE TRIGGER immutable_gym BEFORE UPDATE ON attendance FOR EACH ROW EXECUTE FUNCTION fitcore_immutable_gym();
CREATE TRIGGER immutable_gym BEFORE UPDATE ON exercises FOR EACH ROW EXECUTE FUNCTION fitcore_immutable_gym();
CREATE TRIGGER immutable_gym BEFORE UPDATE ON food_logs FOR EACH ROW EXECUTE FUNCTION fitcore_immutable_gym();
CREATE TRIGGER immutable_gym BEFORE UPDATE ON membership_plans FOR EACH ROW EXECUTE FUNCTION fitcore_immutable_gym();
CREATE TRIGGER immutable_gym BEFORE UPDATE ON notifications FOR EACH ROW EXECUTE FUNCTION fitcore_immutable_gym();
CREATE TRIGGER immutable_gym BEFORE UPDATE ON nutrition_goals FOR EACH ROW EXECUTE FUNCTION fitcore_immutable_gym();
CREATE TRIGGER immutable_gym BEFORE UPDATE ON nutrition_plans FOR EACH ROW EXECUTE FUNCTION fitcore_immutable_gym();
CREATE TRIGGER immutable_gym BEFORE UPDATE ON progress_metrics FOR EACH ROW EXECUTE FUNCTION fitcore_immutable_gym();
CREATE TRIGGER immutable_gym BEFORE UPDATE ON trainer_profiles FOR EACH ROW EXECUTE FUNCTION fitcore_immutable_gym();
CREATE TRIGGER immutable_gym BEFORE UPDATE ON trainer_sessions FOR EACH ROW EXECUTE FUNCTION fitcore_immutable_gym();
CREATE TRIGGER immutable_gym BEFORE UPDATE ON workout_exercise_logs FOR EACH ROW EXECUTE FUNCTION fitcore_immutable_gym();
CREATE TRIGGER immutable_gym BEFORE UPDATE ON workout_logs FOR EACH ROW EXECUTE FUNCTION fitcore_immutable_gym();
CREATE TRIGGER immutable_gym BEFORE UPDATE ON workout_plans FOR EACH ROW EXECUTE FUNCTION fitcore_immutable_gym();
CREATE TRIGGER immutable_gym BEFORE UPDATE ON membership_plan_features FOR EACH ROW EXECUTE FUNCTION fitcore_immutable_gym();
CREATE TRIGGER immutable_gym BEFORE UPDATE ON nutrition_plan_meals FOR EACH ROW EXECUTE FUNCTION fitcore_immutable_gym();
CREATE TRIGGER immutable_gym BEFORE UPDATE ON trainer_available_slots FOR EACH ROW EXECUTE FUNCTION fitcore_immutable_gym();
CREATE TRIGGER immutable_gym BEFORE UPDATE ON trainer_certifications FOR EACH ROW EXECUTE FUNCTION fitcore_immutable_gym();
CREATE TRIGGER immutable_gym BEFORE UPDATE ON workout_plan_exercises FOR EACH ROW EXECUTE FUNCTION fitcore_immutable_gym();
CREATE TRIGGER immutable_gym BEFORE UPDATE ON workout_plan_schedule FOR EACH ROW EXECUTE FUNCTION fitcore_immutable_gym();
CREATE TRIGGER immutable_gym BEFORE UPDATE ON gym_invitations FOR EACH ROW EXECUTE FUNCTION fitcore_immutable_gym();

CREATE FUNCTION fitcore_immutable_primary_owner() RETURNS trigger LANGUAGE plpgsql AS $immutable$
BEGIN
 IF OLD.primary_owner_user_id IS NOT NULL AND NEW.primary_owner_user_id IS DISTINCT FROM OLD.primary_owner_user_id THEN
  RAISE EXCEPTION 'Primary owner transfer is not an ordinary update' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $immutable$;
CREATE TRIGGER immutable_primary_owner BEFORE UPDATE ON gyms FOR EACH ROW EXECUTE FUNCTION fitcore_immutable_primary_owner();

CREATE FUNCTION fitcore_assert_owner(gid uuid) RETURNS void LANGUAGE plpgsql SET search_path FROM CURRENT AS $owner$
DECLARE uid uuid;
BEGIN
 SELECT primary_owner_user_id INTO uid FROM gyms WHERE id=gid FOR UPDATE;
 IF NOT FOUND THEN RETURN; END IF;
 IF uid IS NULL OR NOT EXISTS(SELECT FROM users WHERE id=uid AND gym_id=gid AND role='OWNER' AND coalesce(enabled,true)) THEN
  RAISE EXCEPTION 'Gym requires a valid enabled primary OWNER' USING ERRCODE='23514';
 END IF;
END $owner$;
CREATE FUNCTION fitcore_check_owner() RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $owner$
BEGIN
 IF TG_TABLE_NAME='gyms' THEN PERFORM fitcore_assert_owner(NEW.id);
 ELSE PERFORM fitcore_assert_owner(OLD.gym_id); END IF;
 RETURN NULL;
END $owner$;
CREATE CONSTRAINT TRIGGER valid_primary_owner AFTER INSERT OR UPDATE ON gyms DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION fitcore_check_owner();
CREATE CONSTRAINT TRIGGER preserve_primary_owner AFTER UPDATE OR DELETE ON users DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION fitcore_check_owner();
DO $owners$ DECLARE gid uuid; BEGIN FOR gid IN SELECT id FROM gyms ORDER BY id LOOP PERFORM fitcore_assert_owner(gid); END LOOP; END $owners$;
CREATE INDEX ix_gyms_primary_owner ON gyms(primary_owner_user_id);
CREATE INDEX ix_users_gym_role_created ON users(gym_id,role,created_at);
CREATE INDEX ix_memberships_gym_user_start ON active_memberships(gym_id,user_id,start_date DESC,id);
CREATE INDEX ix_memberships_gym_status_dates ON active_memberships(gym_id,status,start_date,end_date);
CREATE INDEX ix_memberships_gym_plan ON active_memberships(gym_id,plan_id);
CREATE INDEX ix_sessions_gym_member_date ON trainer_sessions(gym_id,member_id,session_date,start_time);
CREATE INDEX ix_sessions_gym_trainer_date ON trainer_sessions(gym_id,trainer_id,session_date,start_time);
CREATE INDEX ix_attendance_gym_user_time ON attendance(gym_id,user_id,check_in_time DESC);
CREATE INDEX ix_attendance_gym_date ON attendance(gym_id,check_in_date);
CREATE INDEX ix_attendance_open ON attendance(gym_id,user_id,check_in_time DESC) WHERE check_out_time IS NULL;
CREATE INDEX ix_workouts_gym_user_date ON workout_logs(gym_id,user_id,date);
CREATE INDEX ix_workout_entries_log ON workout_exercise_logs(gym_id,workout_log_id);
CREATE INDEX ix_workout_entries_exercise ON workout_exercise_logs(gym_id,exercise_id);
CREATE INDEX ix_food_gym_user_date ON food_logs(gym_id,user_id,log_date,log_time);
CREATE INDEX ix_progress_gym_user_date ON progress_metrics(gym_id,user_id,log_date DESC);
CREATE INDEX ix_notifications_gym_user_created ON notifications(gym_id,user_id,created_at DESC);
CREATE INDEX ix_invites_gym_created ON gym_invitations(gym_id,created_at);
CREATE INDEX ix_invites_gym_email ON gym_invitations(gym_id,email);
CREATE INDEX ix_membership_plan_features_parent ON membership_plan_features(gym_id,plan_id);
CREATE INDEX ix_nutrition_plan_meals_parent ON nutrition_plan_meals(gym_id,plan_id);
CREATE INDEX ix_trainer_available_slots_parent ON trainer_available_slots(gym_id,trainer_id);
CREATE INDEX ix_trainer_certifications_parent ON trainer_certifications(gym_id,trainer_id);
CREATE INDEX ix_workout_plan_exercises_parent ON workout_plan_exercises(gym_id,plan_id);
CREATE INDEX ix_workout_plan_schedule_parent ON workout_plan_schedule(gym_id,plan_id);
