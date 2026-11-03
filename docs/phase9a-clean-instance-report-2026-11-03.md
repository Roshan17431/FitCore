# Phase 9A — clean database release preparation

Date:3 November2026 (Asia/Kolkata). Strategy: Option A, fresh independent SaaS, no customer import. Source revision `ddf71b6e5a682bc99a3169b6e27572ba3b1b5903`. Branch `codex/phase9a-clean-instance`, worktree `/Users/roshan/.codex/workspaces/fitcore-multi-tenant-saas/phase9a-repository`. **New SaaS remains undeployed. Production remains BLOCKED.**

## Feasibility decision

**Clean SQL initialization: PASS. Production release-candidate readiness: BLOCKED.** The current migrations initialize genuinely empty PostgreSQL without V3, and local test-only workflows can operate two isolated gyms. However, clean and full-V8-restored schema fingerprints are absent from the readiness allowlist. Do not deploy, broaden the allowlist blindly or use a disposable marker to permit a persistent database.

The broad requirement that owners configure all relevant empty catalogs is also incomplete: membership-plan creation/editing is supported, while exercise/workout-plan/nutrition-plan catalogs expose read APIs only. Production security/infrastructure requirements below remain independently blocked. This report does not treat an empty database as production approval.

## Source/workspace preservation

Created a new linked worktree at the exact verified Phase7 publication commit; no source commit was made. Native desktop worktree creation rejected the existing isolated repository because it was not a registered chat root, so Git worktree fallback was used. Existing `codex/multi-tenant-saas`, other branches/worktrees, Phase8/9 uncommitted docs and private remediation/ownership worksheets remain in place. No reset, clean, stash, copy of real .env files or production credential read was performed.

The only application-level work is verification: no main Java/React source, migration, deployment manifest, dependency lockfile, startup guard or accepted schema fingerprint was modified. No external resource/API/hosting change, persistent database, customer-data import, actual provider or real recipient was used. No commit, push, merge or deployment occurred.

## Verified clean migration behavior

A brand-new harness-owned PostgreSQL18.6 container was checked for zero non-system tables before Flyway. Seven migrations executed in this order:

`V1 → V2 → V4 → V5 → V6 → V7 → V8`

Recorded successful version history: `1,2,4,5,6,7,8`. No V3 row/script or default gym/account is inserted. Flyway's optional schema-creation bookkeeping row has no version and is not a V3. Rerunning migrations executes zero additional versions and does not seed users/catalogs. The migrated schema has26 application tables plus Flyway history.

- V1 creates the existing entity tables empty; V2 adds gym/invitation/user ownership support.
- V4's legacy-ownership checks pass because there are no legacy rows; it still installs NOT NULL/same-gym FKs, scoped uniqueness and deferred valid-primary-owner/immutability rules. No tenant assignment is inferred.
- V5 installs authorization-version enforcement; V6 limits invitations to MEMBER/TRAINER.
- V7 supplies hashed expiring account challenges, email verification/recovery state and shared PostgreSQL abuse counters. V8 supplies encrypted delivery intent/leases; neither enables external email or a scheduler.
- The ordinary app has Flyway off, SQL init never and Hibernate validate. It will not initialize a persistent database merely because its URL is changed. Explicit migration remains a separate future authorized job.

## Readiness blocker and minimum proposed correction

Fresh source fingerprint on PostgreSQL18.6:
`6953167a756786a1de56dd68acb7940e5ae02a710d803d5f18cc737f380f24ca`

Full V8 archive/restore fingerprint:
`7f870102345b628218d6575c1c1dbe67408ad05b69e0d5c90a1d9d5ec6fefc1c`

Neither is in current `SchemaContract.ACCEPTED`. Existing accepted fingerprints were exercised by legacy-stage archive restore then forward migration. A synthetic comparison showed source→accepted-reference differences in PostgreSQL's rendered array casts for the `gyms_status_check`, `users_role_check` and `gym_invitations_role_check` definitions, while the compared column/type/nullability/identity, function, trigger, index and other constraint definitions matched. Full-V8 restore introduces its own rendered descriptor; every component must still be independently certified before acceptance. Diagnostic evidence is in sibling `evidence/phase9a-fingerprint-diff.log`; temporary diagnostic source was removed.

New tests intentionally pin this fail-closed behavior: fresh `schemaReady=false`, restored `schemaReady=false` and actual restored HTTP readiness503. Their passing status proves rejection remains active; it does NOT mean readiness was fixed or waived. Authenticated local workflows are exercised under the existing disposable-only guard, as in the existing harness.

**Minimum proposed implementation, requiring review/approval:** independently audit the complete canonical descriptors for fresh clean and full V8 round-trip paths; extend only the exact certified contracts in `SchemaContract` if all differences are harmless parser renderings. Add version-controlled readiness tests requiring the certified clean/restore paths to report true under restricted runtime identity while dropped/changed constraints, invalid enum values, disabled owner/immutability triggers, altered history or extra privileges still report false. Do not ignore all CHECK constraints, normalize away arbitrary SQL, edit applied V1–V8, invent V3, or weaken the startup guard. No schema migration is currently proposed because no missing business DDL was found. If descriptor review finds substantive drift, design an additive migration separately instead of accepting its hash.

## Two-gym coverage and limits

| Requirement | Evidence / boundary |
| --- | --- |
| Owner A creates Gym A; Owner B creates Gym B | New clean HTTP test uses pending registration, encrypted queue, test-only fake provider and single-use verification; no user exists before verification |
| Valid tenant sessions and own-gym management | Auth-me gym identity, separate gym updates/users, foreign user/catalog/invitation denial; runtime is non-owner restricted DB login |
| Own member/trainer invitations | Both roles accepted independently for both gyms through verification; foreign revocation404, ordinary management403, trainer profile creation |
| Tenant-owned catalogs | Initially empty API arrays; same membership-plan name created in both gyms; foreign plan mutation404; no default gym/catalog |
| Membership/booking behavior | Existing full regression/browser workflows retain renewal/upgrade/history and concurrent booking rules; clean recovery fixture includes representative two-gym memberships/sessions |
| Attendance/workouts/nutrition/progress/notifications | Existing full tenant/role module HTTP/browser matrices retained; empty workout/nutrition libraries additionally exercised in new browser checks |
| Admin dashboards/analytics/CSV | Existing bidirectional tenant-isolation and actual browser CSV checks retained; no global admin bypass |
| Disabled access | Fresh member login → authenticated200 → owner disables member → same JWT401; other local trainer remains authorized |
| Logout/login/page refresh/restoration | Existing browser cross-tab logout, authoritative auth-me restoration, invalid-session handling and gym settings refresh retained. There is no refresh-token protocol or server-wide logout revocation claim. |
| Empty catalog UI/configuration | Added browser empty workout/exercise/nutrition states and membership page without purchasable seeded plans; owner creates first membership plan via UI and reloads; Gym B's catalog remains empty |

**Catalog gap:** membership plans are configurable by authorized owners through existing admin UI/API. Exercise/workout/nutrition catalog creation has no corresponding write API/UI. Membership plans also lack a dedicated empty-library explanation (the page renders an empty grid). Proposed next product decision: approve owner-scoped catalog CRUD/content provenance and empty-state guidance, or explicitly approve a reduced pilot scope with those libraries unavailable. Do not silently seed shared catalogs or manually populate production. No such feature was implemented in Phase9A.

## Clean-instance recovery rehearsal

A second new, independently marked disposable PostgreSQL container received a real custom `pg_dump` archive from a fresh V8 source. Synthetic source includes enabled primary OWNERs, MEMBER/TRAINER accounts, same-name plans and collections, memberships, bookings and a pending encrypted notice. No legacy fixture mapping is used to populate this clean source.

The test compares complete row-count/value fingerprints for every table, including roles/password hashes/gym ownership/history and ciphertext, and every sequence's value/called state. Restore recreates reviewed runtime grants and PUBLIC helper REVOKE; restricted runtime cannot invoke the scoping helper or mutate Flyway history. Cross-gym plan/member FK insertion fails; identity insertion advances beyond restored explicit IDs. These are synthetic reconciliation checks, not production archive certification.

The outbox payload decrypts only with the separately supplied synthetic key; missing key fails. Ciphertext contains no plaintext notice. After restore a fake provider accepts the pending message and terminal payload is wiped. A fixture timestamp mismatch was corrected to use database localtimestamp rather than assuming JVM UTC, matching application issuance behavior.

An actual child backend starts on a random loopback port with cleared environment, the restored container's own marker and restricted runtime identity; main guard remains active. Signed owner sessions work; each owner sees local users and receives404 for the other owner. Readiness503 is asserted as the unresolved fingerprint block. Restored membership/booking preservation is established through complete value/sequence reconciliation; fresh full business behavior is covered by the existing application regressions, not a claim of every business HTTP operation replayed on this restored database.

Child app, source/target containers, archive and temporary logs are cleaned up. No persisted archive or customer-data restore exists. Real backup storage/key custody/ACL/RPO/RTO certification remains BLOCKED.

## Test results

Final `./scripts/verify-release.sh` exited0. Baseline941 backend/35frontend/41distinct browser scenarios were preserved.

| Check | Final result |
| --- | --- |
| Backend regression/security/PostgreSQL/recovery | **944 passed, zero failures/errors/skips** (941 existing +3 focused clean-instance cases) |
| Frontend session/role/contracts | **35 passed** |
| Browser workflows | **44 distinct passed** (41 existing +3 catalog checks;56 executed across both runs including12 repeated transaction/security checks) |
| Fail-closed result checker | **7 passed**; missing-new-suite tests failed before suite registration, then passed |
| Frontend lint / build | **PASS / PASS** |
| Focused clean HTTP/recovery | **3 passed** |
| Main application / V1–V8 / startup & hosting guards | **Unchanged** |
| Actual hosted CI / actual production recovery | **Not run / BLOCKED** |

Browser breakdown:8 transactional,10 role/module,3 tenant analytics/CSV,16 owner/catalog/session,3 gate-off and4 security. Owner/module/isolation collectors reported zero uncaught page errors/external requests. Expected denied requests can appear as console network errors; no claim of zero console messages is made. Both harness runs verified cleanup. Docker context was verified local (`desktop-linux`, Unix socket); all database containers were marked and harness-owned.

Evidence outside Git: sibling `evidence/phase9a-baseline-release.log`, `phase9a-focused-green.log`, `phase9a-final-release.log`, `phase9a-browser-results.json`, `phase9a-document-verification.json` and diagnostic logs. Read-only review found no actionable inconsistencies and did not rerun tests. These are local results, not hosted CI or production certification. Technical readiness remains BLOCKED as explicitly asserted by the new tests.

## Files / migrations

Created:
- `gym-backend/src/test/java/com/fitcore/gym/tenancy/CleanInstanceHttpTest.java`
- `gym-backend/src/test/java/com/fitcore/gym/tenancy/CleanInstanceRecoveryTest.java`
- `docs/phase9a-clean-instance-report-2026-11-03.md`
- `docs/phase9a-clean-environment-design.md`
- `docs/superpowers/plans/2026-11-03-phase9a-clean-instance.md`

Modified verification only:
- `gym-frontend/scripts/owner-integrated.pw.js` — three clean-catalog browser checks.
- `scripts/check_release_results.py` — require both new suites.
- `scripts/test_release_results.py` — missing-clean-suite regressions (observed RED before checker change, GREEN after).

Migrations prepared/modified: **none**. Application behavior/business rules changed: **none**. Existing tests remain present. Evidence logs remain outside Git under sibling `evidence/phase9a-*.log` and ignored browser outputs.

## Production design, remaining blockers and approvals

[Independent environment/startup design and ordered future deployment checklist](phase9a-clean-environment-design.md) provides new Render backend, Vercel frontend, PostgreSQL owner/migrator/runtime roles, separate JWT/outbox keys, URLs/CORS, backup/monitoring, deployment/rollback boundaries and configuration names only. No external resources were created/configured.

The future startup mechanism remains a design: certify immutable new target identity, exact schema/history, runtime privileges, backup/security receipts and explicit deployment authorization; reject inherited/alternate connection selectors before connection. A database comment/flag is not approval. No production mechanism is enabled or guard removed.

Still BLOCKED: fresh/full-restore fingerprint acceptance; production guard/readiness compatibility; catalog scope; actual email adapter/webhook/suppression and idempotency horizon; supervised worker/cleanup; key custody/recovery; browser bearer/XSS/CSRF decision; actual trusted-proxy/shared-limit policy; exact CORS/secrets/TLS/log privacy/audit/monitoring; real runtime roles/backup/RPO/RTO; hosted CI/required checks; explicit provisioning/migration/deployment/onboarding/sending authorization. Operational legacy V3 is NOT required for strict Option A and remains deferred for any future legacy import/migration.

The owner must decide next:

1. Approve a narrowly scoped schema-contract certification/correction milestone, including fresh and full-V8 restore negative tests; keep production startup guard unchanged.
2. Decide owner-configurable exercise/workout/nutrition catalog scope and content/bootstrap policy, and membership empty-state improvement.
3. Choose new service/project/database identity, region/availability/budget and independent origins/session namespace; provisioning is a later explicit action.
4. Select browser transport, proxy/abuse policy, provider/data handling, key custodian, worker/cleanup cadence and monitoring/audit/on-call owners.
5. Set RPO/RTO/retention and appoint independent backup/security/release certifiers; synthetic recovery does not accept those targets.
6. Separately authorize later production hardening, hosted CI/rules, new infrastructure, guard activation, exact migration/deployment, smoke writes and real delivery/public onboarding. None is approved or performed by this report.
