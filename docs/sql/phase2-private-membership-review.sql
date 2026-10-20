-- PRIVATE OUTPUT: contains customer identity and membership history.
-- Run only in the already-authorized production console. Store output outside Git.
-- No credentials, password hashes, tokens or unrelated customer records are selected.
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout = '15s';
WITH utc AS (
    SELECT (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date AS today
), affected AS (
    SELECT user_id FROM public.active_memberships, utc
    WHERE status = 'ACTIVE' AND start_date <= today AND end_date > today
    GROUP BY user_id HAVING count(*) > 1
), members AS (
    SELECT u.id, u.full_name, u.email, u.role, u.enabled,
        (SELECT jsonb_agg(jsonb_build_object(
            'id', m.id, 'plan_id', m.plan_id, 'plan', p.name,
            'package_price_now', p.price, 'duration_months_now', p.duration_months,
            'start_date', m.start_date, 'end_date', m.end_date,
            'status', m.status, 'created_at', m.created_at, 'updated_at', m.updated_at,
            'current_on_audit_date', m.status = 'ACTIVE' AND m.start_date <= utc.today AND m.end_date > utc.today
        ) ORDER BY m.start_date, m.created_at, m.id)
         FROM public.active_memberships m
         JOIN public.membership_plans p ON p.id = m.plan_id CROSS JOIN utc
         WHERE m.user_id = u.id) AS memberships,
        (SELECT jsonb_agg(jsonb_build_object(
            'left_id', a.id, 'right_id', b.id,
            'exact_same_plan_period', a.plan_id = b.plan_id AND a.start_date = b.start_date AND a.end_date = b.end_date
        ) ORDER BY a.id, b.id)
         FROM public.active_memberships a JOIN public.active_memberships b
           ON a.user_id = b.user_id AND a.id < b.id CROSS JOIN utc
         WHERE a.user_id = u.id AND a.status = 'ACTIVE' AND b.status = 'ACTIVE'
           AND a.start_date <= utc.today AND a.end_date > utc.today
           AND b.start_date <= utc.today AND b.end_date > utc.today) AS current_conflicts,
        (SELECT count(*) FROM public.notifications n WHERE n.user_id = u.id
          AND (n.title ~* '(membership|purchase|renew|upgrade|payment|receipt|invoice)'
               OR n.type ~* '(membership|purchase|renew|upgrade|payment|receipt|invoice)')) AS possible_purchase_notification_count
    FROM public.users u JOIN affected a ON a.user_id = u.id
)
SELECT jsonb_build_object(
    'audit_utc_timestamp', CURRENT_TIMESTAMP AT TIME ZONE 'UTC',
    'audit_utc_date', (SELECT today FROM utc),
    'database', current_database(), 'timezone', current_setting('TimeZone'),
    'transaction_read_only', current_setting('transaction_read_only'),
    'members', (SELECT coalesce(jsonb_agg(to_jsonb(m) ORDER BY m.id), '[]'::jsonb) FROM members m),
    'public_tables', (SELECT jsonb_agg(table_name ORDER BY table_name)
        FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE'),
    'possible_purchase_evidence_columns', (SELECT coalesce(jsonb_agg(jsonb_build_object(
        'table', table_name, 'column', column_name) ORDER BY table_name, ordinal_position), '[]'::jsonb)
        FROM information_schema.columns WHERE table_schema = 'public'
          AND (table_name ~* '(payment|purchase|invoice|receipt|order|transaction|audit)'
               OR column_name ~* '(payment|purchase|invoice|receipt|transaction|amount|provider|reference)')),
    'membership_columns', (SELECT jsonb_agg(jsonb_build_object('column', column_name, 'type', data_type)
        ORDER BY ordinal_position) FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'active_memberships'),
    'jdbc_client_sessions', (SELECT count(*) FROM pg_stat_activity
        WHERE datname = current_database() AND application_name = 'PostgreSQL JDBC Driver'),
    'total_users', (SELECT count(*) FROM public.users),
    'total_memberships', (SELECT count(*) FROM public.active_memberships)
) AS fitcore_private_membership_review;
ROLLBACK;
