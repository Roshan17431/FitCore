-- Additive lifecycle only. Preserve existing catalog visibility and all business IDs/history.
SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='30s';
ALTER TABLE exercises ADD COLUMN archived boolean NOT NULL DEFAULT false;
ALTER TABLE nutrition_plans ADD COLUMN archived boolean NOT NULL DEFAULT false;
ALTER TABLE workout_plans ADD COLUMN status varchar(16) NOT NULL DEFAULT 'PUBLISHED';
ALTER TABLE workout_plans ADD CONSTRAINT workout_plans_status_check CHECK(status IN ('DRAFT','PUBLISHED','ARCHIVED'));
