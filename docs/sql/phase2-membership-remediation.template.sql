-- REVIEW TEMPLATE ONLY. Never paste this into production during a read-only review.
-- psql -X --set=ON_ERROR_STOP=1 --file=<privately reviewed copy>
-- Populate __REVIEW_MANIFEST__ in a private copy; never commit customer identifiers.
-- No automatic canonical-row choice, date/plan edits, inserts or deletes.
-- Approval must cover the exact choices AND execution, not merely this review.
\set ON_ERROR_STOP on
BEGIN;
SET LOCAL statement_timeout = '30s';
SET LOCAL lock_timeout = '5s';
SET LOCAL idle_in_transaction_session_timeout = '30s';
SET LOCAL search_path = pg_catalog, public;
DO $remediation$
DECLARE
    manifest jsonb := $manifest$__REVIEW_MANIFEST__$manifest$::jsonb;
    item jsonb;
    snapshot jsonb;
    chosen public.active_memberships%ROWTYPE;
    actual jsonb;
    wanted jsonb;
    affected_before jsonb;
    all_before jsonb;
    all_after jsonb;
    case_ids uuid[];
    retiring_ids bigint[];
    today_utc date := (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date;
    changed_at timestamp := clock_timestamp() AT TIME ZONE 'UTC';
    original_count bigint;
    changed_count bigint;
    total_changed bigint := 0;
    receipt jsonb := '[]'::jsonb;
BEGIN
    IF manifest->>'version' IS DISTINCT FROM '1'
       OR manifest->>'owner_approved' IS DISTINCT FROM 'true'
       OR manifest->>'execution_authorized' IS DISTINCT FROM 'true'
       OR nullif(btrim(manifest->>'approval_reference'), '') IS NULL
       OR nullif(btrim(manifest->>'backup_reference'), '') IS NULL
       OR manifest->>'database_identity_verified' IS DISTINCT FROM 'true'
       OR manifest->>'backup_restore_verified' IS DISTINCT FROM 'true'
       OR manifest->>'private_admin_verified' IS DISTINCT FROM 'true'
       OR manifest->>'membership_writes_frozen' IS DISTINCT FROM 'true'
       OR manifest->>'expected_retirement_count' IS DISTINCT FROM '3' THEN
        RAISE EXCEPTION 'Approval or safety prerequisites incomplete; no remediation performed';
    END IF;
    IF current_database() IS DISTINCT FROM manifest->>'expected_database'
       OR today_utc IS DISTINCT FROM (manifest->>'expected_utc_date')::date
       OR nullif(manifest->>'verified_neon_endpoint', '') IS NULL THEN
        RAISE EXCEPTION 'Database/date identity check failed; refresh private review';
    END IF;
    IF jsonb_typeof(manifest->'cases') IS DISTINCT FROM 'array'
       OR jsonb_array_length(manifest->'cases') <> 2 THEN
        RAISE EXCEPTION 'Expected exactly two explicitly reviewed cases';
    END IF;
    SELECT array_agg((c->>'user_id')::uuid ORDER BY (c->>'user_id')::uuid)
      INTO case_ids FROM jsonb_array_elements(manifest->'cases') c;
    IF (SELECT count(DISTINCT x) FROM unnest(case_ids) x) <> 2 THEN
        RAISE EXCEPTION 'Cases must identify two distinct members';
    END IF;

    -- Same member-first order as Phase 2. Global membership lock also blocks
    -- legacy Phase 1 writers, which do not follow the parent-row lock convention.
    PERFORM u.id FROM public.users u WHERE u.id = ANY(case_ids)
        ORDER BY u.id FOR UPDATE;
    IF (SELECT count(*) FROM public.users WHERE id = ANY(case_ids)) <> 2 THEN
        RAISE EXCEPTION 'Reviewed member no longer exists';
    END IF;
    LOCK TABLE public.active_memberships IN SHARE ROW EXCLUSIVE MODE;
    LOCK TABLE public.membership_plans IN SHARE MODE;
    IF EXISTS (SELECT 1 FROM pg_trigger WHERE tgrelid = 'public.active_memberships'::regclass
        AND NOT tgisinternal AND tgenabled <> 'D') THEN
        RAISE EXCEPTION 'Unreviewed membership trigger; stop for schema review';
    END IF;
    SELECT coalesce(jsonb_agg(to_jsonb(m) ORDER BY m.id), '[]'::jsonb), count(*)
        INTO all_before, original_count FROM public.active_memberships m;
    SELECT coalesce(jsonb_agg(user_id::text ORDER BY user_id), '[]'::jsonb)
        INTO actual FROM (
            SELECT user_id FROM public.active_memberships
            WHERE status = 'ACTIVE' AND start_date <= today_utc AND end_date > today_utc
            GROUP BY user_id HAVING count(*) > 1
        ) a;
    SELECT jsonb_agg(x::text ORDER BY x) INTO wanted FROM unnest(case_ids) x;
    IF actual IS DISTINCT FROM wanted THEN
        RAISE EXCEPTION 'Current conflict population changed; refresh private review';
    END IF;

    FOR item IN SELECT value FROM jsonb_array_elements(manifest->'cases') ORDER BY value->>'user_id' LOOP
        IF item->>'decision' IS DISTINCT FROM 'retain_existing'
           OR item->>'retain_membership_id' IS NULL
           OR jsonb_typeof(item->'retire_membership_ids') IS DISTINCT FROM 'array'
           OR jsonb_array_length(item->'retire_membership_ids') < 1
           OR nullif(btrim(item->>'owner_reason'), '') IS NULL THEN
            RAISE EXCEPTION 'Explicit retention/retirement choice and reason required';
        END IF;
        IF NOT EXISTS (SELECT 1 FROM public.users
            WHERE id = (item->>'user_id')::uuid
              AND role::text = item->>'expected_role'
              AND enabled IS NOT DISTINCT FROM (item->>'expected_enabled')::boolean) THEN
            RAISE EXCEPTION 'Reviewed member account state changed';
        END IF;
        SELECT coalesce(jsonb_agg(to_jsonb(m) ORDER BY m.id), '[]'::jsonb)
            INTO actual FROM public.active_memberships m WHERE m.user_id = (item->>'user_id')::uuid;
        affected_before := actual;
        IF actual IS DISTINCT FROM item->'expected_history' THEN
            RAISE EXCEPTION 'Membership history drift; no partial remediation allowed';
        END IF;
        SELECT * INTO chosen FROM public.active_memberships
            WHERE id = (item->>'retain_membership_id')::bigint
              AND user_id = (item->>'user_id')::uuid AND status = 'ACTIVE'
              AND start_date <= today_utc AND end_date > today_utc;
        IF NOT FOUND OR chosen.plan_id IS DISTINCT FROM (item->>'intended_plan_id')::bigint
           OR chosen.start_date IS DISTINCT FROM (item->>'intended_start_date')::date
           OR chosen.end_date IS DISTINCT FROM (item->>'intended_end_date_exclusive')::date THEN
            RAISE EXCEPTION 'Chosen existing row must exactly represent owner-approved entitlement';
        END IF;
        -- Plan package values may be mutable; refuse review-time metadata drift.
        IF NOT EXISTS (SELECT 1 FROM public.membership_plans p
            WHERE p.id = chosen.plan_id AND jsonb_build_object('id', p.id,
                'name', p.name, 'price', p.price, 'duration_months', p.duration_months)
                = item->'expected_retained_plan') THEN
            RAISE EXCEPTION 'Retained plan metadata changed; refresh review';
        END IF;
        SELECT coalesce(array_agg(value::bigint ORDER BY value::bigint), ARRAY[]::bigint[])
            INTO retiring_ids FROM jsonb_array_elements_text(item->'retire_membership_ids');
        IF cardinality(retiring_ids) <> (SELECT count(DISTINCT x) FROM unnest(retiring_ids) x) THEN
            RAISE EXCEPTION 'Duplicate retirement IDs';
        END IF;
        SELECT coalesce(jsonb_agg(id ORDER BY id), '[]'::jsonb) INTO actual
            FROM public.active_memberships WHERE user_id = chosen.user_id
              AND status = 'ACTIVE' AND start_date <= today_utc AND end_date > today_utc AND id <> chosen.id;
        IF actual IS DISTINCT FROM to_jsonb(retiring_ids) THEN
            RAISE EXCEPTION 'Retirement list must exactly match other current conflicting rows';
        END IF;
        UPDATE public.active_memberships SET status = 'CANCELLED', updated_at = changed_at
            WHERE id = ANY(retiring_ids) AND user_id = chosen.user_id AND status = 'ACTIVE';
        GET DIAGNOSTICS changed_count = ROW_COUNT;
        IF changed_count <> cardinality(retiring_ids) THEN
            RAISE EXCEPTION 'Unexpected change count';
        END IF;
        total_changed := total_changed + changed_count;
        SELECT jsonb_agg(to_jsonb(m) ORDER BY m.id) INTO actual
            FROM public.active_memberships m WHERE m.user_id = chosen.user_id;
        receipt := receipt || jsonb_build_array(jsonb_build_object(
            'case', item->>'case', 'user_id', chosen.user_id,
            'before_history', affected_before, 'after_history', actual,
            'retired_ids', retiring_ids, 'retained_id', chosen.id));
    END LOOP;
    IF total_changed <> (manifest->>'expected_retirement_count')::bigint THEN
        RAISE EXCEPTION 'Total retirement count differs from reviewed plan';
    END IF;
    IF EXISTS (SELECT 1 FROM public.active_memberships
        WHERE status = 'ACTIVE' AND start_date <= today_utc AND end_date > today_utc
        GROUP BY user_id HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Current overlap remains; rolling back entire remediation';
    END IF;
    IF EXISTS (SELECT 1 FROM public.active_memberships WHERE user_id = ANY(case_ids)
        AND status = 'ACTIVE' AND start_date > today_utc) THEN
        RAISE EXCEPTION 'Unreviewed future entitlement; stop for owner review';
    END IF;
    IF (SELECT count(*) FROM public.active_memberships) <> original_count THEN
        RAISE EXCEPTION 'Membership row count changed';
    END IF;
    SELECT jsonb_agg(to_jsonb(m) - 'status' - 'updated_at' ORDER BY m.id)
        INTO actual FROM public.active_memberships m;
    SELECT jsonb_agg(value - 'status' - 'updated_at' ORDER BY (value->>'id')::bigint)
        INTO wanted FROM jsonb_array_elements(all_before);
    IF actual IS DISTINCT FROM wanted THEN
        RAISE EXCEPTION 'Historical identifiers, plan, period or creation time changed';
    END IF;
    SELECT jsonb_agg(to_jsonb(m) ORDER BY m.id) INTO all_after FROM public.active_memberships m;
    -- Includes unchanged historical rows: only the explicitly approved retirement
    -- IDs may differ, and they must have exactly CANCELLED plus one UTC timestamp.
    FOR snapshot IN SELECT value FROM jsonb_array_elements(all_before) LOOP
        wanted := snapshot;
        IF EXISTS (SELECT 1 FROM jsonb_array_elements(manifest->'cases') c,
            jsonb_array_elements_text(c->'retire_membership_ids') r
            WHERE r.value::bigint = (snapshot->>'id')::bigint) THEN
            wanted := snapshot || jsonb_build_object('status', 'CANCELLED', 'updated_at', changed_at);
        END IF;
        SELECT value INTO actual FROM jsonb_array_elements(all_after)
            WHERE (value->>'id')::bigint = (snapshot->>'id')::bigint;
        IF actual IS DISTINCT FROM wanted THEN
            RAISE EXCEPTION 'Unapproved row change detected';
        END IF;
    END LOOP;
    -- PRIVATE receipt: operator must capture to a mode-600 file before COMMIT.
    -- Receipt alone is not commit proof; retain psql exit status and command tag.
    RAISE NOTICE 'FITCORE_PRIVATE_RECEIPT %', jsonb_build_object(
        'approval_reference', manifest->>'approval_reference',
        'changed_at_utc', changed_at, 'changed_count', total_changed,
        'cases', receipt, 'final_action', 'Determined by final ROLLBACK/COMMIT command');
END
$remediation$;
-- DEFAULT IS NON-PERSISTENT. A rollback preview STILL issues UPDATE statements;
-- it is forbidden in production until the owner explicitly authorizes it.
-- Rehearse on an isolated restore first. No human waiting inside this transaction.
ROLLBACK;
