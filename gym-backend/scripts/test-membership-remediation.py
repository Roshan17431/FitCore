#!/usr/bin/env python3
"""Rehearse review SQL exclusively inside an owned, network-isolated Docker DB.

No environment/connection files or production evidence are read. Synthetic
choices below exercise mechanics; they are not recommendations for real members.
"""
import copy
import datetime
import json
import pathlib
import subprocess
import time
import uuid

ROOT = pathlib.Path(__file__).resolve().parents[2]
SQL_DIR = ROOT / 'docs/sql'
FORWARD = (SQL_DIR / 'phase2-membership-remediation.template.sql').read_text()
ROLLBACK = (SQL_DIR / 'phase2-membership-rollback.template.sql').read_text()
POST = (SQL_DIR / 'phase2-post-remediation-checks.sql').read_text()
REVIEW = (SQL_DIR / 'phase2-private-membership-review.sql').read_text()
CONTAINER = 'fitcore-remediation-rehearsal-' + uuid.uuid4().hex[:10]
DB = 'fitcore_remediation_rehearsal'
A = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'
B = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'
C = 'cccccccc-cccc-cccc-cccc-cccccccccccc'
passed = []


def sql(source, ok=True):
    result = subprocess.run(
        ['docker', 'exec', '-i', CONTAINER, 'psql', '-X', '-q', '-t', '-A',
         '--set=ON_ERROR_STOP=1', '-U', 'postgres', '-d', DB],
        input=source, text=True, capture_output=True, timeout=45)
    if ok and result.returncode:
        raise AssertionError(result.stderr)
    if not ok and result.returncode == 0:
        raise AssertionError('Expected guarded refusal, but SQL succeeded')
    return result


def scalar(source):
    return sql(source).stdout.strip()


def rows():
    return json.loads(scalar("SELECT jsonb_agg(to_jsonb(m) ORDER BY id) FROM active_memberships m"))


def render(template, marker, manifest, commit=False):
    value = json.dumps(manifest, separators=(',', ':'))
    assert '$manifest$' not in value
    result = template.replace(marker, value)
    if commit:
        assert result.endswith('ROLLBACK;\n')
        result = result[:-len('ROLLBACK;\n')] + 'COMMIT;\n'
    return result


def fixture():
    sql("""
DROP SCHEMA public CASCADE; CREATE SCHEMA public;
CREATE TABLE users(id uuid PRIMARY KEY, full_name text, email text, role varchar(20), enabled boolean);
CREATE TABLE membership_plans(id bigint PRIMARY KEY, name text, price numeric(10,2), duration_months integer);
CREATE TABLE active_memberships(id bigint PRIMARY KEY, user_id uuid REFERENCES users(id),
 plan_id bigint REFERENCES membership_plans(id), start_date date NOT NULL, end_date date NOT NULL,
 status varchar(20) NOT NULL, created_at timestamp, updated_at timestamp);
CREATE TABLE notifications(id bigint PRIMARY KEY, user_id uuid REFERENCES users(id), title text, type text);
INSERT INTO users VALUES
 ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa','Synthetic A','a@example.invalid','MEMBER',true),
 ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb','Synthetic B','b@example.invalid','MEMBER',true),
 ('cccccccc-cccc-cccc-cccc-cccccccccccc','Synthetic C','c@example.invalid','MEMBER',true);
INSERT INTO membership_plans VALUES (1,'Basic',29,1),(2,'Premium',79,3);
WITH day AS (SELECT (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date AS d)
INSERT INTO active_memberships
SELECT i, CASE WHEN i<=8 THEN 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::uuid
 WHEN i<=12 THEN 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'::uuid
 ELSE 'cccccccc-cccc-cccc-cccc-cccccccccccc'::uuid END,
 CASE WHEN i=10 THEN 2 ELSE 1 END,
 CASE WHEN i IN (1,2,3,9,10) THEN d-2 ELSE d-80 END,
 CASE WHEN i=10 THEN d+60 WHEN i IN (1,2,3,9) THEN d+30 ELSE d-50 END,
 CASE WHEN i=15 THEN 'CANCELLED' ELSE 'ACTIVE' END,
 '2026-01-01 00:00:00'::timestamp,'2026-01-01 00:00:00'::timestamp
FROM generate_series(1,15) i CROSS JOIN day;
""")
    manifest = {
        'version': 1, 'owner_approved': True, 'execution_authorized': True,
        'approval_reference': 'SYNTHETIC ONLY', 'backup_reference': 'SYNTHETIC ONLY',
        'database_identity_verified': True, 'backup_restore_verified': True,
        'private_admin_verified': True, 'membership_writes_frozen': True,
        'expected_database': DB, 'verified_neon_endpoint': 'SYNTHETIC-ISOLATED-DOCKER',
        'expected_utc_date': scalar("SELECT (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date"),
        'expected_retirement_count': 3, 'cases': []}
    baseline = rows()
    for label, user, keep, retire in [('A', A, 1, [2, 3]), ('B', B, 10, [9])]:
        history = [r for r in baseline if r['user_id'] == user]
        chosen = next(r for r in history if r['id'] == keep)
        plan = json.loads(scalar('SELECT to_jsonb(p) FROM membership_plans p WHERE id=' + str(chosen['plan_id'])))
        manifest['cases'].append({
            'case': 'Synthetic ' + label, 'user_id': user, 'expected_role': 'MEMBER',
            'expected_enabled': True, 'expected_history': history,
            'decision': 'retain_existing', 'retain_membership_id': keep,
            'retire_membership_ids': retire, 'intended_plan_id': chosen['plan_id'],
            'intended_start_date': chosen['start_date'],
            'intended_end_date_exclusive': chosen['end_date'],
            'expected_retained_plan': plan, 'owner_reason': 'Synthetic test choice, not production evidence'})
    return manifest, baseline


def refused(name, mutate=None, database_change=None):
    manifest, baseline = fixture()
    if database_change:
        sql(database_change)
        baseline = rows()
    if mutate:
        mutate(manifest)
    sql(render(FORWARD, '__REVIEW_MANIFEST__', manifest, commit=True), ok=False)
    assert rows() == baseline, name + ': refusal changed data'
    passed.append(name)


owned = False
try:
    subprocess.run(['docker', 'run', '-d', '--rm', '--network', 'none', '--name', CONTAINER,
                    '-e', 'POSTGRES_PASSWORD=synthetic-disposable-only', '-e', 'POSTGRES_DB=' + DB,
                    'postgres:18-alpine'], check=True, capture_output=True, text=True, timeout=60)
    owned = True
    for _ in range(40):
        startup = subprocess.run(['docker', 'logs', CONTAINER], capture_output=True, text=True, timeout=5)
        ready = subprocess.run(['docker', 'exec', CONTAINER, 'pg_isready', '-U', 'postgres', '-d', DB],
                               capture_output=True, timeout=5)
        if 'PostgreSQL init process complete' in startup.stdout and ready.returncode == 0:
            break
        time.sleep(0.5)
    else:
        raise AssertionError('Disposable PostgreSQL did not become ready')
    version = scalar('SHOW server_version')

    refused('unapproved plan refuses without writes', lambda m: m.update(owner_approved=False))
    refused('missing execution approval refuses', lambda m: m.update(execution_authorized=False))
    refused('missing backup restore gate refuses', lambda m: m.update(backup_restore_verified=False))
    refused('missing private-admin gate refuses', lambda m: m.update(private_admin_verified=False))
    refused('wrong database refuses', lambda m: m.update(expected_database='neondb'))
    refused('stale audit date refuses', lambda m: m.update(expected_utc_date='2000-01-01'))
    refused('unselected intended membership refuses', lambda m: m['cases'][0].update(retain_membership_id=None))
    refused('history drift refuses', database_change="UPDATE active_memberships SET end_date=end_date+1 WHERE id=1")
    refused('second-case drift rolls back earlier case changes atomically', database_change="UPDATE active_memberships SET end_date=end_date+1 WHERE id=9")
    refused('changed account state refuses', database_change="UPDATE users SET enabled=false WHERE id='" + A + "'")
    refused('changed plan metadata refuses', database_change="UPDATE membership_plans SET price=price+1 WHERE id=1")
    refused('cross-member retirement refuses', lambda m: m['cases'][0].update(retire_membership_ids=[2,9]))
    refused('duplicate retirement IDs refuse', lambda m: m['cases'][0].update(retire_membership_ids=[2,2,3]))
    refused('invented expiry refuses', lambda m: m['cases'][0].update(intended_end_date_exclusive='2099-01-01'))
    refused('unreviewed conflicting member refuses', database_change="UPDATE active_memberships SET start_date=(CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date-1,end_date=(CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date+5,status='ACTIVE' WHERE id IN (13,14)")
    refused('unreviewed custom trigger refuses before changes', database_change="CREATE FUNCTION unexpected_trigger() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RETURN NEW; END $$; CREATE TRIGGER unreviewed BEFORE UPDATE ON active_memberships FOR EACH ROW EXECUTE FUNCTION unexpected_trigger()")

    manifest, baseline = fixture()
    review = sql(REVIEW).stdout.strip()
    review = json.loads(review)
    assert review['transaction_read_only'] == 'on' and len(review['members']) == 2
    assert sorted(len(m['memberships']) for m in review['members']) == [4,8]
    assert sum(len(m['current_conflicts']) for m in review['members']) == 4
    passed.append('private SELECT review returns exact histories and conflicts read-only')

    sql(render(FORWARD, '__REVIEW_MANIFEST__', manifest))
    assert rows() == baseline
    passed.append('default ROLLBACK preserves all rows')

    committed = sql("SET TIME ZONE 'Pacific/Kiritimati';\n" + render(FORWARD, '__REVIEW_MANIFEST__', manifest, commit=True))
    after = rows()
    assert len(after) == 15
    changed = [r['id'] for r,b in zip(after,baseline) if r != b]
    assert changed == [2,3,9]
    for r,b in zip(after,baseline):
        assert {k:v for k,v in r.items() if k not in ['status','updated_at']} == {k:v for k,v in b.items() if k not in ['status','updated_at']}
        if r['id'] in changed:
            assert r['status'] == 'CANCELLED'
    checks = json.loads(sql(POST).stdout.strip())
    assert checks['zero_current_overlaps'] and checks['current_overlap_pairs'] == 0
    assert checks['users_requiring_current_review'] == 0 and checks['current_memberships'] == 2
    assert checks['membership_rows'] == 15 and checks['total_users'] == 3
    passed.append('approved synthetic commit changes exactly 3 statuses; zero current overlaps; history preserved in UTC')
    sql(render(FORWARD, '__REVIEW_MANIFEST__', manifest, commit=True), ok=False)
    assert rows() == after
    passed.append('repeat execution safely refuses with unchanged stored state')

    rollback = {'version':1, 'rollback_approved':True,'execution_authorized':True,
        'approval_reference':'SYNTHETIC ROLLBACK','forward_commit_evidence_reference':'SYNTHETIC COMMIT',
        'membership_writes_frozen':True,'database_identity_verified':True,'expected_restore_count':3,
        'expected_database':DB,'verified_neon_endpoint':'SYNTHETIC-ISOLATED-DOCKER','cases':[]}
    for c in manifest['cases']:
        rollback['cases'].append({'user_id':c['user_id'],'before_history':c['expected_history'],
            'expected_after_history':[r for r in after if r['user_id']==c['user_id']],
            'retired_ids':c['retire_membership_ids']})
    missing = copy.deepcopy(rollback)
    missing['rollback_approved'] = False
    sql(render(ROLLBACK, '__ROLLBACK_MANIFEST__', missing, commit=True), ok=False)
    assert rows() == after
    passed.append('unapproved compensating rollback refuses')
    sql(render(ROLLBACK, '__ROLLBACK_MANIFEST__', rollback))
    assert rows() == after
    passed.append('compensating rollback also defaults to ROLLBACK')
    sql('UPDATE active_memberships SET end_date=end_date+1 WHERE id=1')
    drift = rows()
    sql(render(ROLLBACK, '__ROLLBACK_MANIFEST__', rollback, commit=True), ok=False)
    assert rows() == drift
    passed.append('rollback refuses subsequent legitimate data drift')
    sql("UPDATE active_memberships SET end_date='" + next(r['end_date'] for r in after if r['id']==1) + "' WHERE id=1")
    sql(render(ROLLBACK, '__ROLLBACK_MANIFEST__', rollback, commit=True))
    assert rows() == baseline
    passed.append('approved compensating rollback restores exact original rows without deletions')

    manifest, baseline = fixture()
    blocker = subprocess.Popen(['docker','exec','-i',CONTAINER,'psql','-X','-q','-U','postgres','-d',DB,
        '-c',"BEGIN; LOCK TABLE active_memberships IN ROW EXCLUSIVE MODE; SELECT pg_sleep(8); ROLLBACK;"],
        stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True)
    try:
        for _ in range(20):
            count = scalar("SELECT count(*) FROM pg_locks WHERE relation='active_memberships'::regclass AND mode='RowExclusiveLock' AND granted")
            if int(count): break
            time.sleep(0.1)
        else: raise AssertionError('Synthetic legacy writer did not acquire lock')
        refusal = sql(render(FORWARD, '__REVIEW_MANIFEST__', manifest, commit=True), ok=False)
        assert 'lock timeout' in refusal.stderr
        blocker.communicate(timeout=12)
        assert rows() == baseline
    finally:
        if blocker.poll() is None:
            blocker.terminate()
            blocker.communicate(timeout=5)
    passed.append('legacy writer conflict hits bounded lock timeout without partial writes')
    print(json.dumps({'passed':len(passed),'checks':passed,'postgresVersion':version,
        'syntheticOnly':True,'productionConnections':False,'container':CONTAINER},indent=2),flush=True)
finally:
    if owned:
        subprocess.run(['docker','rm','-f',CONTAINER],check=True,capture_output=True,text=True,timeout=30)
        print('Owned network-isolated PostgreSQL container removed.',flush=True)
