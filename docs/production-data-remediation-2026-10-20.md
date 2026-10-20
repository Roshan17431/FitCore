# FitCore production-data remediation review

20 October 2026. **Decision: HOLD production data changes and Phase 2 deployment.** This review prepares decisions and SQL; it authorizes no execution. Customer identity and row-level evidence are in a private local packet outside Git. No customer information or credentials appear in this report.

Source-publication follow-up: the owner subsequently authorized committing these changes and pushing GitHub `main`, in at most 17 commits. The historical no-commit/no-push statements in this report describe the preceding review. [Source publication](/Users/roshan/Desktop/FitCore/docs/phase2-source-publication-2026-10-20.md) keeps production deployment and database changes on hold; it supplies no membership decision or execution approval.

No production UPDATE, INSERT, DELETE, DDL, row-locking remediation, restore, configuration save, restart, deployment, commit, push or merge was performed. Database review used SELECTs in explicit read-only transactions ending in ROLLBACK. Ordinary HTTP health and rejected authentication checks caused no application data writes; the login implementation was reviewed before those checks.

## Database identity and current security configuration

| Check | Finding |
| --- | --- |
| Render target | Existing **FitCore** web service in the previously confirmed **My Workspace**; Docker backend, `master`, automatic deployment enabled |
| Neon target | Existing **fit core** project, **production** default branch, **neondb** |
| Database identity comparison | The unique compute endpoint ID extracted from Render's database hostname matches the primary compute on the selected Neon production branch. The database name also matches. The JDBC URL requires TLS. Only the host/name/TLS facts were retained; no connection string, password, JWT secret or database username was exported. |
| Configuration precedence | All 15 service environment keys inspected; no datasource/profile/JVM-option override keys, linked groups or secret files listed. Docker copies only the built JAR, excludes `.env` and has no command override. |
| Runtime corroboration and limit | Latest live deployment is Phase 1 commit `8ef78e693d9da2e470193d3ec7acd5ac6a195d09`, completed **20 October at 11:08 IST**. Its startup log reports the default profile. One PostgreSQL JDBC client session was observed in the matched Neon database after live authentication checks. These establish configuration mapping and corroborate use; the running process environment was not dumped, and a successful private-admin database-backed read still awaits the owner. A later saved-only environment edit cannot be ruled out solely from the dashboard. |
| SQL initialization | **Saved dashboard `SQL_INIT_MODE=never`.** This supersedes the earlier readiness report's `always` observation. Source defaults to `never`, demo SQL is opt-in, and the observed runtime uses the default profile. Effective runtime SQL mode was not independently dumped. |
| Other security-related settings | `AWS_S3_ENABLED=false`, `JPA_SHOW_SQL=false`, `JPA_FORMAT_SQL=false`, `JWT_EXPIRATION_MS=86400000`, exact production frontend CORS origin; `JPA_DDL_AUTO=update` retained. JWT/database credential values stayed masked and unchanged by this review. |
| Phase 1 code | No differences from the deployed Phase 1 base in security classes, auth service or application SQL/profile configuration. Public administrator password blocking, owner/admin authorization and per-request enabled-account/credential-bound JWT checks remain present. |
| Live HTTP checks | Health **200**; unauthenticated admin Users **401**; published demo administrator login **401**, no token. No private credentials were requested or used. |
| Fresh isolated security verification | `./mvnw -Dtest=SecurityRegressionTest,ProductionSeedIsolationTest test`: **169 passed, 0 failures/errors/skips**, finished **15:17 IST**. Includes personal read/write authorization and disabled/legacy-token regressions. This is a targeted recheck, separate from the previous Phase 2 clean **281-test** run. |

Dashboard values are saved configuration, not a live environment dump. Render supports saving changes without applying them until deployment; confirm the applied revision during the eventual approved rollout. [Render environment documentation](https://render.com/docs/configure-environment-variables).

## Existing-data findings and owner decisions

The earlier Phase 2 audit was reviewed and the affected histories were refreshed using authorized read-only access. The fresh aggregate baseline remains **12 users, 15 memberships, 5 date-current records, 2 affected accounts and 4 current conflict pairs**. There are **6 exact duplicate extra rows** across all history and **9 ended rows still stored as ACTIVE**. No invalid membership intervals were found. The 32 all-date ACTIVE overlap pairs include historical intervals and current conflicts; they do not represent 32 people.

| Private case | Full history reviewed | Current rows / conflict pairs | Finding | Owner decision |
| --- | ---: | ---: | --- | --- |
| Member A | 8 rows | 3 / 3 | All three current records describe the same plan and period | **PENDING**; no canonical row selected |
| Member B | 4 rows | 2 / 1 | Current records differ in plan and period | **PENDING**; plan/expiry cannot be inferred |

The private packet identifies both accounts and every conflicting record ID, and lists their complete membership history, stored status, effective current status, plan, period and timestamps. It provides a separate approval table for each account: intended plan, inclusive start, **exclusive UTC expiration**, retained row ID, explicit superseded IDs, reason, approver and timestamp.

**Purchase evidence is insufficient to infer intent.** Public-schema metadata contains no purchase/payment/receipt/invoice/transaction ledger or matching evidence columns. Neither affected account has a matching membership/purchase notification. The owner confirms **no external purchase records**. Current plan prices and row timestamps are not historical payment evidence. The former UI sent purchase/renewal/upgrade through the same insert operation. The owner must resolve intent privately; this review does not choose the newest, longest or most expensive row, sum duplicate durations, or contact customers.

Private packet: [OWNER-REVIEW.md](/Users/roshan/.codex/private-reviews/fitcore-memberships-2026-10-20/OWNER-REVIEW.md). Its directory is mode **700**, files mode **600**, outside the Git repository. Do not copy its customer data into chat, a PR, a public report or deployment logs.

## Prepared SQL and its limits

| Artifact | Purpose |
| --- | --- |
| [Private review SELECT](/Users/roshan/Desktop/FitCore/docs/sql/phase2-private-membership-review.sql) | Read-only identification, full affected histories, conflict pairs and evidence/schema metadata. Output is private. |
| [Forward template](/Users/roshan/Desktop/FitCore/docs/sql/phase2-membership-remediation.template.sql) | Guarded transactional status-only retirement after exact owner choices and execution approval |
| [Rollback template](/Users/roshan/Desktop/FitCore/docs/sql/phase2-membership-rollback.template.sql) | Separately approved compensating rollback using exact before and committed after snapshots |
| [Post-remediation SELECT](/Users/roshan/Desktop/FitCore/docs/sql/phase2-post-remediation-checks.sql) | Aggregate zero-current-overlap checks, counts and invalid-period check |
| [Synthetic rehearsal](/Users/roshan/Desktop/FitCore/gym-backend/scripts/test-membership-remediation.py) | Executes SQL mechanics only in its own network-isolated disposable PostgreSQL container |

The private `decisions.json` and `remediation-review-only.sql` contain the observed before snapshots, **no preselected retention choice**, false approval/execution/backup/admin/write-freeze gates, and a final `ROLLBACK`. They cannot perform remediation as prepared. The private rollback packet likewise has no expected-after snapshot and no approval.

For this dataset the supported plan is to retain **one existing owner-approved current row per account**, and mark **exactly three explicitly approved other current rows CANCELLED**. It makes no INSERT/DELETE, changes no plan/date/creation field, and leaves all other historical rows untouched. Updated timestamps for the three retired rows are recorded in the private receipt, along with full before/after snapshots. The nine elapsed ACTIVE rows require no bulk change: Phase 2 derives expiration correctly.

If the intended plan/period is absent from the current records, or the owner intends no current entitlement, **stop and prepare a separately reviewed variant**. Do not alter dates to make this template fit. No schema migration or global constraint is included. A zero all-history overlap count is not required and is not permission to delete history.

Execution guards validate approval references, backup/admin/write-freeze attestations, database name, audit UTC date, exact member/account/full-history snapshots, intended plan metadata, all retirement IDs and expected change counts. They reject new conflicts, future entitlements and unreviewed custom membership triggers. Locks acquire members in UUID order, then block membership-table writers, then freeze plan metadata during the short transaction. The table lock also covers legacy Phase 1 code, which does not take the Phase 2 parent-row lock. Read-only traffic can continue. Timeouts bound lock waits and transaction duration. [PostgreSQL locking](https://www.postgresql.org/docs/18/explicit-locking.html).

The database-name guard alone cannot verify a remote endpoint; the operator must independently check the trusted connection profile against the reviewed Neon endpoint before attesting `database_identity_verified`. The script compares before/after rows, aborts on drift or partial results, preserves row counts and asserts **zero current overlaps globally** before its final action. A receipt emitted before COMMIT is not commit proof; retain the successful command tag/exit status too.

**A rolled-back UPDATE preview is still a production mutation attempt.** Neither SQL template may be executed in production during this read-only review. Complete choices and regenerate a private SQL copy, rejecting any JSON containing its `$manifest$` delimiter; review the entire generated script and its SHA-256. Do not edit the manifest, SQL or final ROLLBACK/COMMIT action after approval without re-review.

## Backup and rollback strategy

Observed Neon Backup & Restore: **six-hour history window**, **no snapshots**, **no schedule listed**. The owner confirms **neither private-admin login nor an isolated recovery test has been performed**. A recovery window is not a tested, durable backup; approval and execution must remain on hold.

Before approved production changes, an authorized operator must:

1. Verify the exact database endpoint and obtain a fresh consistent backup in access-controlled, encrypted storage outside Git. Record UTC timestamp, database/branch identity, successful exit status, SHA-256 and retention/expiry. Allow for the short provider history window; do not increase retention or create provider resources without approval.
2. Restore that backup to an isolated PostgreSQL 18 target, with application writes/email/external integrations disabled. Verify schema, row-level membership snapshots, aggregate counts and representative authorized application reads. Record the target identity, test date, outcome and restoration time. A successful archive listing or synthetic SQL test alone is insufficient.
3. Rehearse the completed owner choices, postchecks and compensating rollback on the isolated restore. Preserve private before/after evidence and command outcomes.

Illustrative commands for an operator's **already configured, verified private libpq service profiles**; no credentials belong in these commands or chat. They have **not** been executed against production:

```sh
set -euo pipefail
umask 077
# task_backup_dir is an existing encrypted private directory outside the repo.
: "${task_backup_dir:?Set the verified private backup directory first}"
test -d "$task_backup_dir"
pg_dump --dbname='service=fitcore_prod_verified' --format=custom \
  --no-owner --no-acl --file="$task_backup_dir/fitcore-before.dump"
shasum -a 256 "$task_backup_dir/fitcore-before.dump"
pg_restore --list "$task_backup_dir/fitcore-before.dump"
# Confirm this profile points ONLY to the isolated recovery target before running.
pg_restore --dbname='service=fitcore_isolated_restore' --exit-on-error \
  --single-transaction --no-owner --no-acl "$task_backup_dir/fitcore-before.dump"
```

Use trusted PostgreSQL 18 tools, a verified direct Neon connection for the backup, TLS, and existing secret-managed authentication. The dump includes sensitive account data/hashes and must remain private. Verify dump success before hashing or restoring. [PostgreSQL SQL-dump guidance](https://www.postgresql.org/docs/18/backup-dump.html).

Before COMMIT, any SQL error aborts the transaction; the prepared default final ROLLBACK persists no membership changes. After an approved successful COMMIT, prefer the narrow compensating rollback template if separately approved: it restores only the three changed rows' prior status/update timestamps and refuses any changed post-remediation history. **This restores the former overlaps**, so maintain the write freeze and reassess rollout. Never overwrite subsequent legitimate changes to force rollback.

Whole-branch point-in-time restore is a last-resort, separately approved recovery operation. It replaces the branch's database timeline, affects all databases on that branch, interrupts connections and can discard legitimate later writes. Restore to an isolated target for investigation first; do not click production Restore during this review. [Neon restore documentation](https://neon.com/docs/postgres/backup-restore/branch-restore).

## Rehearsal evidence

Fresh SQL rehearsal: **25 checks passed on PostgreSQL 18.6**, using synthetic accounts and choices only, Docker `--network none`, no host database ports, no production connection files/environment, own container removed afterward. Covers default-deny approvals, wrong database, stale date, missing backup/admin gates, missing retention choice, account/plan/history drift, cross-member/duplicate retirement IDs, invented expiry, unknown conflicts/triggers, atomic rollback after a second-case failure, default ROLLBACK, exact three-row commit, preserved history, zero current overlaps, repeat-execution refusal, compensating rollback/drift refusal and bounded legacy-writer lock contention.

Reproduce locally with Docker:

```sh
cd /Users/roshan/Desktop/FitCore
python3 gym-backend/scripts/test-membership-remediation.py
```

No product code changed in this remediation review. The earlier Phase 2 report retains the full **281-test**, frontend and Playwright evidence; this review adds the SQL templates, rehearsal and documentation. Synthetic rehearsal does not certify a production backup, owner intent or private-administrator access.

## Approval and deployment-readiness checklist

- [x] Review prior audit, refresh affected histories read-only, identify both accounts privately and list exact conflicts.
- [x] Match Render's configured endpoint/database to Neon production; inspect saved security settings and current live deployment/profile.
- [x] Verify live public-admin rejection and unauthenticated admin denial; re-run isolated Phase 1 security read/write tests.
- [x] Prepare default-deny forward/rollback SQL and aggregate postchecks; rehearse them without production access.
- [ ] **Owner decides each case:** approve exact retained ID, plan, inclusive start, exclusive expiration and explicit retirement IDs/reason in the private review. No choices are inferred.
- [ ] **Owner tests private administrator:** fresh private browser login, Admin Dashboard, Users read, no edits. Report only pass/fail; keep credentials private.
- [ ] **Backup/recovery gate:** verified backup and successful isolated restore, with privately retained evidence and adequate retention.
- [ ] **Refresh immediately before execution:** same verified target, UTC date, histories/account/plan metadata and trigger inventory; regenerate the private SQL/rollback packet after any drift.
- [ ] **Approve exact execution:** script/hash, specific three status transitions, backup reference, operator/window and final COMMIT; separately approve any production preview, rollback, deployment or infrastructure change. Review approval is not execution approval.
- [ ] **Coordinate the write window:** freeze all membership and plan writers and drain requests before remediation. The old live backend can recreate duplicates after locks release. Keep the freeze until Phase 2 frontend/backend deployment and verification complete; no production freeze or rollout has been initiated here.
- [ ] **Approved operator executes:** capture private before evidence, receipt and successful COMMIT outcome with `psql -X --set=ON_ERROR_STOP=1`. Stop on error; do not retry by disabling guards.
- [ ] **Run postchecks read-only:** `zero_current_overlaps=true`, `users_requiring_current_review=0`, `current_overlap_pairs=0`, `invalid_membership_periods=0`. For this status-only plan at the reviewed date: 15 membership rows, 12 users, 2 current rows, exactly 3 newly CANCELLED rows; verify exact row snapshots, not just counts. Rebaseline if legitimate changes occur.
- [ ] **Approved deployment only:** verify tests for the exact release revision, preserve Phase 1 settings and existing credentials, deploy frontend/backend together, verify applied environment revision/default profile, then private-admin and owner-authorized member read-only smoke tests. Do not rotate JWT secret or credentials as a data repair step. Existing valid Phase 1 credential-bound tokens should not need invalidation solely for membership status repair.
- [ ] **Monitor and reopen writes:** confirm no ambiguous membership 409s for the reviewed accounts, no authorization regressions, stable database-backed reads and unchanged unrelated records. Record rollback readiness before ending the controlled window.

The current production baseline remains **2 affected accounts / 4 current overlap pairs**. Production readiness remains **on hold** until the owner decisions, administrator access, backup recovery and explicit execution/deployment approvals are complete.

## Owner-guided readiness follow-up — 20 October 2026

The existing private `OWNER-REVIEW.md` has been read and extended with a guided choice table. It maps **A1/A2/A3** and **B1/B2** to the exact candidate record, plan, inclusive start, exclusive expiration and proposed retirement IDs. That mapping stays in the private mode-600 file outside Git. Labels express no preference; neither case has a selection. The owner can choose an existing candidate, defer, or specify a different intended entitlement privately. Choosing a different outcome requires a separately reviewed script variant.

The owner has been asked for newer administrator and backup evidence. Until provided, the last explicit owner confirmation remains **neither test completed**. No newer success evidence is present in the private packet. The Neon Backup & Restore page was inspected again read-only and still shows **six-hour history, no snapshots and no schedule**. This does not exclude an independently created `pg_dump` elsewhere; its existence, usability and successful isolated restore remain unverified.

Safe owner checks:

1. Open the production login in a fresh private browser window and enter the existing private administrator credentials personally. Open Admin Dashboard, then Users without editing anything. Retain the test time and separate pass/fail results for login and both reads; never send a password, token, customer list or screenshot containing records.
2. If an actual backup/restore was already performed, identify its private local evidence file. The evidence must connect the verified production source, backup timestamp/hash and successful backup command to a **distinct isolated restore target**, successful restore outcome and schema/count/record checks. A provider recovery window, archive listing or synthetic SQL test alone does not establish recoverability. No new backup, provider branch, restore or infrastructure operation has been initiated here.
3. Review both private candidate tables. Reply using aliases only, or complete the owner fields in the private file. A choice is permission to prepare a proposal; it is **not production execution approval**. Existing business decisions, approval flags and SQL remain unchanged while choices are pending.

Once explicit choices arrive, prepare the exact private SQL with the retained plan/period, explicit retirement IDs, refreshed expected snapshots, exact change count, default ROLLBACK, rollback conditions and verification checklist. Review the completed SQL/hash before requesting approval for its precise production operation. Keep execution disabled until the administrator, backup/recovery, target, data-freshness and controlled-write-window requirements are satisfied. Do not use this follow-up request as approval to modify production.

Follow-up owner confirmation: **private production administrator login, Dashboard and Users remain not tested**, explicitly reconfirmed on 20 October 2026. Membership choices and newer backup/isolated-restore evidence are still pending. No execution approval was given.
