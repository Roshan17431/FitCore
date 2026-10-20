-- PRIVATE, SEPARATELY APPROVED COMPENSATING ROLLBACK ONLY.
-- Populate __ROLLBACK_MANIFEST__ using the approved before snapshot and the
-- ACTUAL committed forward receipt. Never guess expected-after timestamps.
-- Restores only retired rows' original status/updated_at; no INSERT/DELETE.
-- Restoring the old statuses restores the old overlaps: keep writes frozen.
\set ON_ERROR_STOP on
BEGIN;
SET LOCAL statement_timeout = '30s';
SET LOCAL lock_timeout = '5s';
SET LOCAL idle_in_transaction_session_timeout = '30s';
SET LOCAL search_path = pg_catalog, public;
DO $rollback$
DECLARE
    manifest jsonb := $manifest$__ROLLBACK_MANIFEST__$manifest$::jsonb;
    item jsonb;
    old_row jsonb;
    expected_after jsonb;
    actual jsonb;
    wanted jsonb;
    case_ids uuid[];
    row_id bigint;
    changed_count bigint;
    total_changed bigint := 0;
    original_count bigint;
BEGIN
    IF manifest->>'version' IS DISTINCT FROM '1'
       OR manifest->>'rollback_approved' IS DISTINCT FROM 'true'
       OR manifest->>'execution_authorized' IS DISTINCT FROM 'true'
       OR nullif(btrim(manifest->>'approval_reference'), '') IS NULL
       OR nullif(btrim(manifest->>'forward_commit_evidence_reference'), '') IS NULL
       OR manifest->>'membership_writes_frozen' IS DISTINCT FROM 'true'
       OR manifest->>'database_identity_verified' IS DISTINCT FROM 'true'
       OR manifest->>'expected_restore_count' IS DISTINCT FROM '3' THEN
        RAISE EXCEPTION 'Rollback approval or committed forward evidence missing';
    END IF;
    IF current_database() IS DISTINCT FROM manifest->>'expected_database'
       OR nullif(manifest->>'verified_neon_endpoint', '') IS NULL
       OR jsonb_typeof(manifest->'cases') IS DISTINCT FROM 'array'
       OR jsonb_array_length(manifest->'cases') <> 2 THEN
        RAISE EXCEPTION 'Rollback database/case guard failed';
    END IF;
    SELECT array_agg((c->>'user_id')::uuid ORDER BY (c->>'user_id')::uuid)
        INTO case_ids FROM jsonb_array_elements(manifest->'cases') c;
    IF (SELECT count(DISTINCT x) FROM unnest(case_ids) x) <> 2 THEN
        RAISE EXCEPTION 'Rollback members must be distinct';
    END IF;
    PERFORM u.id FROM public.users u WHERE u.id = ANY(case_ids) ORDER BY u.id FOR UPDATE;
    LOCK TABLE public.active_memberships IN SHARE ROW EXCLUSIVE MODE;
    IF EXISTS (SELECT 1 FROM pg_trigger WHERE tgrelid = 'public.active_memberships'::regclass
        AND NOT tgisinternal AND tgenabled <> 'D') THEN
        RAISE EXCEPTION 'Unreviewed membership trigger; stop for schema review';
    END IF;
    SELECT count(*) INTO original_count FROM public.active_memberships;
    FOR item IN SELECT value FROM jsonb_array_elements(manifest->'cases') ORDER BY value->>'user_id' LOOP
        IF jsonb_typeof(item->'before_history') IS DISTINCT FROM 'array'
           OR jsonb_typeof(item->'expected_after_history') IS DISTINCT FROM 'array'
           OR jsonb_typeof(item->'retired_ids') IS DISTINCT FROM 'array'
           OR jsonb_array_length(item->'retired_ids') < 1 THEN
            RAISE EXCEPTION 'Exact before/after snapshots and retirement IDs required';
        END IF;
        IF (SELECT count(DISTINCT value::bigint) FROM jsonb_array_elements_text(item->'retired_ids'))
            <> jsonb_array_length(item->'retired_ids') THEN
            RAISE EXCEPTION 'Duplicate rollback retirement IDs';
        END IF;
        SELECT coalesce(jsonb_agg(to_jsonb(m) ORDER BY m.id), '[]'::jsonb) INTO actual
            FROM public.active_memberships m WHERE m.user_id = (item->>'user_id')::uuid;
        IF actual IS DISTINCT FROM item->'expected_after_history' THEN
            RAISE EXCEPTION 'Post-remediation data drift; stop for manual recovery review';
        END IF;
        FOR row_id IN SELECT value::bigint FROM jsonb_array_elements_text(item->'retired_ids') LOOP
            SELECT value INTO old_row FROM jsonb_array_elements(item->'before_history')
                WHERE (value->>'id')::bigint = row_id;
            SELECT value INTO expected_after FROM jsonb_array_elements(item->'expected_after_history')
                WHERE (value->>'id')::bigint = row_id;
            IF old_row IS NULL OR expected_after IS NULL
               OR old_row->>'user_id' IS DISTINCT FROM item->>'user_id'
               OR expected_after->>'user_id' IS DISTINCT FROM item->>'user_id'
               OR old_row->>'status' IS DISTINCT FROM 'ACTIVE'
               OR expected_after->>'status' IS DISTINCT FROM 'CANCELLED'
               OR (old_row - 'status' - 'updated_at') IS DISTINCT FROM (expected_after - 'status' - 'updated_at') THEN
                RAISE EXCEPTION 'Rollback row is not a verified forward retirement';
            END IF;
            UPDATE public.active_memberships
                SET status = old_row->>'status', updated_at = (old_row->>'updated_at')::timestamp
                WHERE id = row_id AND user_id = (item->>'user_id')::uuid
                  AND to_jsonb(active_memberships) = expected_after;
            GET DIAGNOSTICS changed_count = ROW_COUNT;
            IF changed_count <> 1 THEN RAISE EXCEPTION 'Rollback row changed'; END IF;
            total_changed := total_changed + changed_count;
        END LOOP;
        SELECT jsonb_agg(to_jsonb(m) ORDER BY m.id) INTO actual
            FROM public.active_memberships m WHERE m.user_id = (item->>'user_id')::uuid;
        IF actual IS DISTINCT FROM item->'before_history' THEN
            RAISE EXCEPTION 'Rollback does not exactly restore reviewed member history';
        END IF;
    END LOOP;
    IF total_changed <> (manifest->>'expected_restore_count')::bigint
       OR (SELECT count(*) FROM public.active_memberships) <> original_count THEN
        RAISE EXCEPTION 'Unexpected rollback totals';
    END IF;
    RAISE NOTICE 'FITCORE_PRIVATE_ROLLBACK_RECEIPT %', jsonb_build_object(
        'approval_reference', manifest->>'approval_reference',
        'restored_rows', total_changed, 'old_overlaps_restored', true,
        'final_action', 'Determined by final ROLLBACK/COMMIT command');
END
$rollback$;
-- Changing this to COMMIT requires explicit rollback execution approval.
ROLLBACK;
