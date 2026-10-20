-- Read only, aggregate output. Zero is required for both current-overlap counts.
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout = '15s';
WITH utc AS (SELECT (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date AS today),
current_periods AS (
    SELECT m.* FROM public.active_memberships m, utc
    WHERE status = 'ACTIVE' AND start_date <= today AND end_date > today
), ambiguous AS (SELECT user_id FROM current_periods GROUP BY user_id HAVING count(*) > 1)
SELECT jsonb_build_object(
    'checked_at_utc', CURRENT_TIMESTAMP AT TIME ZONE 'UTC',
    'database', current_database(), 'transaction_read_only', current_setting('transaction_read_only'),
    'users_requiring_current_review', (SELECT count(*) FROM ambiguous),
    'current_overlap_pairs', (SELECT count(*) FROM current_periods a JOIN current_periods b
        ON a.user_id = b.user_id AND a.id < b.id),
    'zero_current_overlaps', NOT EXISTS (SELECT 1 FROM ambiguous),
    'total_users', (SELECT count(*) FROM public.users),
    'membership_rows', (SELECT count(*) FROM public.active_memberships),
    'current_memberships', (SELECT count(*) FROM current_periods),
    'invalid_membership_periods', (SELECT count(*) FROM public.active_memberships WHERE end_date <= start_date),
    'all_active_overlap_pairs', (SELECT count(*) FROM public.active_memberships a
        JOIN public.active_memberships b ON a.user_id = b.user_id AND a.id < b.id
        AND a.status = 'ACTIVE' AND b.status = 'ACTIVE'
        AND a.start_date < b.end_date AND b.start_date < a.end_date)
) AS fitcore_post_remediation_checks;
ROLLBACK;
