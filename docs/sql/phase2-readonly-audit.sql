-- Read-only aggregate audit. No identifiers, credentials or personal records are selected.
BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout = '15s';
WITH utc AS (SELECT (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date AS today),
membership_duplicates AS (
    SELECT count(*) - 1 AS extras FROM active_memberships
    GROUP BY user_id, plan_id, start_date, end_date, status HAVING count(*) > 1
), booking_duplicates AS (
    SELECT count(*) - 1 AS extras FROM trainer_sessions WHERE status = 'SCHEDULED'
    GROUP BY member_id, trainer_id, session_date, start_time, end_time HAVING count(*) > 1
), safe_slots AS (
    SELECT DISTINCT CASE
        WHEN slot ~* '^\s*((mon(day)?|tue(sday)?|wed(nesday)?|thu(rsday)?|fri(day)?|sat(urday)?|sun(day)?)\s*:?[[:space:]]*)?[0-9]{2}:[0-9]{2}\s*-\s*[0-9]{2}:[0-9]{2}\s*$'
            THEN slot
        ELSE '<unsupported format withheld>' END AS format_sample
    FROM trainer_available_slots
)
SELECT json_build_object(
    'database', current_database(),
    'database_timezone', current_setting('TimeZone'),
    'postgres_version', current_setting('server_version'),
    'audit_utc_date', (SELECT today FROM utc),
    'users', (SELECT count(*) FROM users),
    'memberships', (SELECT count(*) FROM active_memberships),
    'stored_active_memberships', (SELECT count(*) FROM active_memberships WHERE status = 'ACTIVE'),
    'current_memberships', (SELECT count(*) FROM active_memberships, utc WHERE status = 'ACTIVE' AND start_date <= today AND end_date > today),
    'active_but_ended', (SELECT count(*) FROM active_memberships, utc WHERE status = 'ACTIVE' AND end_date <= today),
    'active_future_memberships', (SELECT count(*) FROM active_memberships, utc WHERE status = 'ACTIVE' AND start_date > today),
    'invalid_membership_periods', (SELECT count(*) FROM active_memberships WHERE end_date <= start_date),
    'exact_duplicate_membership_extras', (SELECT coalesce(sum(extras),0) FROM membership_duplicates),
    'overlapping_active_membership_pairs', (SELECT count(*) FROM active_memberships a JOIN active_memberships b
        ON a.id < b.id AND a.user_id = b.user_id AND a.status = 'ACTIVE' AND b.status = 'ACTIVE'
        AND a.start_date < b.end_date AND b.start_date < a.end_date),
    'current_duplicate_membership_pairs', (SELECT count(*) FROM active_memberships a JOIN active_memberships b
        ON a.id < b.id AND a.user_id = b.user_id AND a.status = 'ACTIVE' AND b.status = 'ACTIVE', utc
        WHERE a.start_date <= today AND a.end_date > today AND b.start_date <= today AND b.end_date > today),
    'users_requiring_current_membership_review', (SELECT count(*) FROM (
        SELECT user_id FROM active_memberships, utc WHERE status = 'ACTIVE' AND start_date <= today AND end_date > today
        GROUP BY user_id HAVING count(*) > 1) affected),
    'plan_packages', (SELECT json_agg(json_build_object('name', name, 'price', price,
        'duration_months', duration_months) ORDER BY price) FROM membership_plans),
    'trainer_sessions', (SELECT count(*) FROM trainer_sessions),
    'scheduled_sessions', (SELECT count(*) FROM trainer_sessions WHERE status = 'SCHEDULED'),
    'past_scheduled_sessions', (SELECT count(*) FROM trainer_sessions, utc WHERE status = 'SCHEDULED' AND session_date < today),
    'invalid_session_intervals', (SELECT count(*) FROM trainer_sessions WHERE end_time <= start_time),
    'exact_duplicate_scheduled_booking_extras', (SELECT coalesce(sum(extras),0) FROM booking_duplicates),
    'trainer_scheduled_overlap_pairs', (SELECT count(*) FROM trainer_sessions a JOIN trainer_sessions b
        ON a.id < b.id AND a.trainer_id = b.trainer_id AND a.session_date = b.session_date
        AND a.status = 'SCHEDULED' AND b.status = 'SCHEDULED' AND a.start_time < b.end_time AND b.start_time < a.end_time),
    'member_scheduled_overlap_pairs', (SELECT count(*) FROM trainer_sessions a JOIN trainer_sessions b
        ON a.id < b.id AND a.member_id = b.member_id AND a.session_date = b.session_date
        AND a.status = 'SCHEDULED' AND b.status = 'SCHEDULED' AND a.start_time < b.end_time AND b.start_time < a.end_time),
    'availability_rows', (SELECT count(*) FROM trainer_available_slots),
    'availability_format_samples', (SELECT coalesce(json_agg(format_sample), '[]'::json) FROM safe_slots),
    'inactive_trainer_profiles', (SELECT count(*) FROM trainer_profiles p JOIN users u ON u.id = p.user_id WHERE u.enabled = false),
    'profiles_without_trainer_role', (SELECT count(*) FROM trainer_profiles p JOIN users u ON u.id = p.user_id WHERE u.role <> 'TRAINER')
) AS fitcore_phase2_readonly_audit;
ROLLBACK;
