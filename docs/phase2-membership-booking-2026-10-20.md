# FitCore Phase 2 — membership and trainer booking fixes

Date: 20 October 2026. Branch: `codex/phase2-membership-bookings`, based on Phase 1 commit `8ef78e693d9da2e470193d3ec7acd5ac6a195d09`.

Source-publication follow-up: the owner subsequently requested at most 17 commits pushed to GitHub `main`. See [the source publication record](/Users/roshan/Desktop/FitCore/docs/phase2-source-publication-2026-10-20.md). The no-commit/no-push statements below describe the original local verification handoff. Source publication does not approve production data changes or deployment.

**Current status: implementation and local verification complete.** All **281 backend tests pass**, including the original **172-test baseline** and **22 PostgreSQL tests**. Frontend API tests pass **7/7**, fixture Playwright scenarios **8/8**, and real frontend-to-PostgreSQL Playwright scenarios **8/8**. Independent review passes. Production deployment remains on hold for the existing membership-data review described below.

No production settings, credentials or database records were changed. No commit, push, merge, deployment or restart was performed. Production access was limited to aggregate SELECT queries in explicit read-only transactions ending in ROLLBACK, in the owner's Neon project.

## Issues and root causes fixed

| Issue | Root cause | Resulting behavior |
| --- | --- | --- |
| Purchase, renewal and upgrade all created subscriptions | All three UI buttons called one unconditional insert method | Separate purchase, renewal and upgrade operations with state checks and ownership authorization |
| Duplicate and overlapping memberships | No transaction or lock existed around membership checks and creation | A member-row write lock covers checks and changes in one transaction; duplicate/stale requests return 409 |
| Expired or cancelled membership shown as current | Current lookup selected the newest row regardless of status or dates; dashboard checked only stored ACTIVE | A single UTC date convention determines current eligibility; expired/future/cancelled records grant no current membership selection |
| Misleading administrative membership counts | Stored ACTIVE rows were counted after their expiration | Date-aware active totals; ambiguous users appear as “Review required” and are excluded from active totals |
| Duplicate/overlapping appointments | Booking inserted without checking trainer or member conflicts | Both resources are locked; scheduled overlaps are rejected with 409, including across different trainers for one member |
| Invalid or unavailable booking times | Missing range, precise future-time and schedule checks | Future UTC starts, strictly increasing same-day intervals, valid published windows and active trainer role/account required |
| Persistence precision could alter a validated interval | Java LocalTime accepts finer fractions than the Hibernate/PostgreSQL TIME path preserves | Fractional seconds are rejected; whole-second times are verified after PostgreSQL persistence and reload |
| Upgrade could create an empty interval at midnight | Multiple calls to the current date could disagree across expiry | Each write captures one UTC business date after acquiring its member lock |
| Errors removed useful UI state | Mutation errors replaced the page and discarded available controls | Inline errors retain forms and selections; conflicts refresh membership data; synchronous submission guards prevent repeated clicks |
| Conflicts displayed an older success toast | Feedback from a previous action remained during the next failed request | A new membership or booking attempt clears prior success feedback; both browser test sets cover success followed by conflict |

All Phase 1 authentication, disabled-token and ownership protections remain in place. The existing security suite passes unchanged; the original two trainer unit tests retain their checks with updated constructor/lock fixtures.

## Approved membership rules

The owner explicitly approved these rules before implementation:

- **Dates:** periods use `startDate <= today < endDate` in UTC. A membership ending on 20 October is no longer current on 20 October. Calendar-month arithmetic preserves Java's month-end clamping; 31 October plus one month is 30 November.
- **Purchase:** creates one period starting today and ending after the selected package's duration. An existing current or future ACTIVE entitlement prevents another purchase, regardless of the selected plan.
- **Active renewal:** extends the same current record from its existing end date by that plan's duration. Its ID, original start date and creation time remain; other historical records are preserved. The existing model has no separate payment or renewal-event ledger.
- **Expired renewal:** creates a new period starting today for the latest uncancelled historical plan. The previous row and its original dates remain. Cancelled, superseded or upcoming periods cannot be used as renewal targets.
- **Upgrade:** requires a strictly higher **total package price**, cancels the old entitlement while preserving its original dates and plan, and creates the higher-plan entitlement from today through the **existing expiration date**. Upgrading neither adds a full new term nor forfeits already remaining time.
- **Billing:** no payment integration, charge, refund, credit or proration was added. Existing price values describe packages; these operations change recorded entitlements only.
- **Stale requests:** renewal requires the expected end date; upgrade requires the current membership ID and expected end date. If another request has changed that state, the later request gets 409 and must refresh.
- **History/status:** reads derive EXPIRED from elapsed ACTIVE rows without rewriting them. History also supplies an effective UPCOMING display state and server-computed remaining days/date/timezone. No expiry job or bulk status update runs against existing data.
- **Ambiguous legacy data:** multiple current periods return 409 to member entitlement reads/changes. Administrative Users and Dashboard remain accessible, showing “Review required.” No arbitrary plan is selected and no records are automatically removed.

The app's existing plan features are descriptive lists; Phase 2 corrects current-entitlement selection and its existing UI benefits display. It does not introduce new paid-access rules for unrelated workout, nutrition or booking APIs.

New endpoints, in addition to the existing purchase and history/current endpoints:

```text
POST /api/memberships/renew/{userId}/{membershipId}
{"expectedEndDate":"YYYY-MM-DD"}

POST /api/memberships/upgrade/{userId}/{planId}
{"membershipId":123,"expectedEndDate":"YYYY-MM-DD"}
```

Both use the same owner-or-administrator authorization as the existing personal endpoints. Body-supplied identities cannot replace authorized path ownership.

## Booking and concurrency strategy

Every booking executes in one transaction, locking the member's stable user row first and the trainer profile second. This works even when no prior membership/session exists. After both locks, a fresh query checks SCHEDULED rows on the requested date for either the same trainer **or** the same member:

```text
existing.startTime < requested.endTime
AND existing.endTime > requested.startTime
```

Adjacent appointments are allowed. Exact duplicates, partial intersections and containing intervals are rejected. CANCELLED and COMPLETED records do not block slots; their records remain available in history. The request cannot set its own stored member, trainer or status to bypass checks.

The trainer account must be enabled and still have role TRAINER. Starts must be strictly after the current UTC instant; end must follow start on the same date. No overnight rollover or fractional seconds are accepted.

Empty/null availability retains legacy behavior: no daily restriction has been published. Nonempty availability must contain UTC windows such as `09:00-12:00`, `Tue 09:00-12:00` or `Tuesday: 09:00-12:00`. Weekday names/three-letter abbreviations are case-insensitive. An appointment must fit entirely within a matching window; malformed entries fail closed with a useful 400 response. No new schedule editor was added.

The member lock also serializes purchase, renewal and upgrade; stale expected-state checks prevent a second renewal from extending the same observed period twice. PostgreSQL row locks are transaction-scoped, so these protections work across application instances, rather than relying on in-process synchronization. [PostgreSQL locking documentation](https://www.postgresql.org/docs/current/explicit-locking.html).

## Production-data audit — read only

Source: owner-identified Neon project **fit core**, branch **production**, database **neondb**. The database reports **PostgreSQL 18.6**, timezone GMT, consistent with the UTC convention. Render connection strings were not retrieved for an independent hostname comparison. Neither credentials nor personal records were exported.

| Observation | Count |
| --- | ---: |
| Users / membership rows | 12 / 15 |
| Stored ACTIVE memberships | 14 |
| Stored ACTIVE but already ended | 9 |
| Date-current membership rows | 5 |
| Users with current memberships / users requiring duplicate review | 2 / 2 |
| Exact duplicate membership extra rows | 6 |
| Overlapping ACTIVE membership pairs across all dates | 32 |
| Currently overlapping membership pairs | 4 |
| Invalid membership date intervals | 0 |
| Trainer session rows / SCHEDULED rows | 3 / 1 |
| Past SCHEDULED rows / future SCHEDULED rows | 1 / 0 |
| Exact duplicate scheduled bookings / trainer overlaps / member overlaps | 0 / 0 / 0 |
| Invalid session intervals / availability rows | 0 / 0 |

Actual production packages match the reviewed model: Basic 29 for 1 month, Premium 79 for 3 months, Elite 149 for 6 months. A per-month comparison would incorrectly reverse their intended upgrade order.

Pair counts are not numbers of people. Exact duplicate extras count rows beyond the first in identical user/plan/date/status groups. Past ACTIVE rows are excluded by the new date-aware reads; their status values are not bulk rewritten.

The reusable [read-only audit SQL](/Users/roshan/Desktop/FitCore/docs/sql/phase2-readonly-audit.sql) selects aggregates and guarded availability-format samples, not names, emails, user IDs, credentials or tokens. Sanitized observed results are saved in `output/playwright/phase2-2026-10-20/production-audit.json`.

## Safe remediation plan and migration requirements

**No schema migration is required for this implementation.** The only entity addition is a date predicate method; no persisted fields, tables, enum values or foreign keys were added. Existing user/profile rows provide the locks. Audit SQL lives under docs and is never run automatically at application startup.

Before any production rollout:

1. Obtain explicit approval for the separate data-review/remediation operation, verify the intended production database and a usable backup with a tested isolated restore procedure, and arrange a controlled membership-write window.
2. An authorized operator privately inspects the two affected members' rows and any original membership/purchase evidence. Do not automatically infer the correct plan from maximum price, maximum end date or total duplicated durations; the old faulty buttons could have created misleading rows.
3. Confirm the intended plan and expiration for each affected member with the owner. Preserve every row and original period. A separately approved transaction can retire only the confirmed superseded entitlements by status, retaining their historical dates/plan, and create a corrected period only if no existing row represents the approved entitlement. No such transaction was executed or embedded in this patch.
4. Retain a private before/after export, approval reference and operator timestamp. The existing schema has no remediation-reason ledger, so preserve that evidence outside this patch rather than pretending such an audit field exists.
5. Repeat the read-only audit and authorized member/admin reads. The **current** overlap-pair count should be zero and each reviewed member should have one intended current period. Historical duplicates may remain intentionally; a nonzero all-history duplicate count alone is not permission to delete history.
6. The nine elapsed ACTIVE rows need no write cleanup for correct expiration behavior. Any optional status reconciliation requires separate approval and retained evidence.

Application-level parent locks protect writes through these services. Direct SQL writers or another service that ignores the locking convention can bypass them. If database-wide exclusion constraints are later required, first review **all** conflicting ACTIVE intervals (including elapsed ones), approve their remediation, then design and rehearse the constraint migration in disposable PostgreSQL. Do not automatically add an index/constraint that rejects existing production data during startup.

## Verification

| Check | Result |
| --- | --- |
| Clean baseline before Phase 2 | 172 backend tests passed; frontend lint/build passed |
| Final `./mvnw clean verify` via disposable PostgreSQL runner | **281 passed, 0 failures, 0 errors, 0 skipped**; executable JAR built |
| Original Phase 1/existing baseline | **172/172** retained and passing |
| Membership regressions | **29/29** |
| Midnight upgrade regression | **1/1** |
| Booking regressions | **57/57** |
| PostgreSQL-specific tests on 18.6 | **22/22**, including concurrent subscription/renewal/booking, adjacency, cancellation reuse, time-precision rejection and persisted whole-second fidelity |
| Frontend API contract tests | **7/7** |
| Playwright UI fixture scenarios | **8/8**, including expired renewal, eligibility, conflict-state retention and submission guards |
| Real frontend → API → PostgreSQL Playwright | **8/8**; no API interception, no external requests; cross-tab renewal returned exactly 200/409 |
| Frontend lint/build | Passing; final logs retained |
| Independent code review | Four findings reproduced and fixed; re-review passed |
| Canonical read-only audit SQL | Passed on seeded disposable PostgreSQL 18.6, ending in ROLLBACK |
| Browser harness isolation/cleanup | Both occupied app ports correctly refused with exit 64; own processes/browser/container cleaned after success and failure |

The PostgreSQL tests coordinate genuinely concurrent HTTP requests with committed fixtures and held database locks, and check stored outcomes after completion. They use a fixed UTC clock and strictly guarded loopback JDBC URL. The runner creates and removes only its own disposable container. The original H2 profile remains isolated and is not redirected to production.

Development evidence includes expected RED failures reproducing duplicates, expiration, conflicts and missing endpoints. Two test-fixture defects were corrected: mutable collections were needed for Hibernate availability fixtures, and SQL snapshots needed a JPA flush inside shared test transactions (`repeatedActiveRenewalFromSameStateIsRejected`). The final suite has no unresolved failures or errors. Independent review additionally reproduced and fixed administrator access with duplicate data, midnight upgrade bounds, fractional TIME persistence, and host-timezone-dependent PostgreSQL fixtures. The final visual review reproduced stale success feedback in both fixture flows and a real booking-conflict flow before the two handlers were corrected and all browser checks rerun.

Reproduce backend verification from the backend directory with Docker available:

```sh
cd /Users/roshan/Desktop/FitCore/gym-backend
./scripts/verify-postgres.sh
```

The script runs **`./mvnw clean verify`**, supplying only disposable loopback database settings to the PostgreSQL-specific class. A bare `./mvnw clean verify` still runs the isolated default suite but skips the opt-in PostgreSQL class; use the runner for the complete 281-test evidence.

Frontend checks:

```sh
cd /Users/roshan/Desktop/FitCore/gym-frontend
node --test scripts/api-contract.test.mjs
npm run lint
npm run build
```

The integrated browser harness uses the verified JAR, disposable PostgreSQL, a fixture-only member account, and loopback API/Vite ports 18082/5175. It refuses occupied ports and cleans its own processes, browser session and database afterward. It disables environment-file loading, strips inherited application environment values, and verifies the served frontend API origin before login. It never uses production credentials or production endpoints.

```sh
cd /Users/roshan/Desktop/FitCore
bash gym-backend/scripts/verify-phase2-ui.sh
```

Final result: **exit 0, eight real integrated checks passed**. Persisted state after purchase, renewal, upgrade and the renewal race: two membership rows, exactly one ACTIVE and one CANCELLED. The stale cross-tab renewal produced one HTTP 200 and one 409, with exactly one additional package period. Alpha's 10:00–11:00 session and adjacent Beta 11:00–12:00 session persisted; duplicate Alpha and overlapping Beta attempts were rejected without losing the form. Both session records survived browser reload. External request count was zero.

Seven screenshots cover the active membership, renewal, preserved upgrade history, stale-state feedback, refreshed expiry, booking conflict and booking history. The [membership conflict screenshot](/Users/roshan/Desktop/FitCore/output/playwright/phase2-2026-10-20/integrated-04-membership-conflict-retained.png) and [booking conflict screenshot](/Users/roshan/Desktop/FitCore/output/playwright/phase2-2026-10-20/integrated-06-booking-conflict-retained.png) were visually reviewed. The latter confirms that the old success toast is absent while the selected trainer/date/time remain available.

The UI harness itself performs the real integrated cases; the fixture CLI script separately covers the expired-renewal and upcoming-entitlement UI branches. For repeat fixture verification, serve Vite on loopback port 5174 with `VITE_API_BASE_URL=http://127.0.0.1:18081/api`, open a fresh Playwright CLI browser at that origin and run `gym-frontend/scripts/phase2-ui.pw.js` using `run-code --filename`. That script intercepts only its designated fixture API origin and is intentionally separate from the real integration harness.

Logs, sanitized JSON summaries and browser artifacts are under `output/playwright/phase2-2026-10-20/`; the local JDK is 24.0.1 compiling to Java 17, while the production Docker runtime remains Java 17. PostgreSQL behavior was verified against the matching 18.6 database version; this is not a claim of complete Java runtime parity.

## Files changed

| Area | Files |
| --- | --- |
| Membership API and rules | [MembershipService.java](/Users/roshan/Desktop/FitCore/gym-backend/src/main/java/com/fitcore/gym/service/MembershipService.java), [MembershipController.java](/Users/roshan/Desktop/FitCore/gym-backend/src/main/java/com/fitcore/gym/controller/MembershipController.java), [ActiveMembership.java](/Users/roshan/Desktop/FitCore/gym-backend/src/main/java/com/fitcore/gym/entity/ActiveMembership.java) |
| Membership response/request contracts | [ActiveMembershipDto.java](/Users/roshan/Desktop/FitCore/gym-backend/src/main/java/com/fitcore/gym/dto/membership/ActiveMembershipDto.java), [RenewMembershipRequest.java](/Users/roshan/Desktop/FitCore/gym-backend/src/main/java/com/fitcore/gym/dto/membership/RenewMembershipRequest.java), [UpgradeMembershipRequest.java](/Users/roshan/Desktop/FitCore/gym-backend/src/main/java/com/fitcore/gym/dto/membership/UpgradeMembershipRequest.java) |
| Consistent displays | [DashboardService.java](/Users/roshan/Desktop/FitCore/gym-backend/src/main/java/com/fitcore/gym/service/DashboardService.java), [AdminService.java](/Users/roshan/Desktop/FitCore/gym-backend/src/main/java/com/fitcore/gym/service/AdminService.java) |
| Booking and availability | [TrainerService.java](/Users/roshan/Desktop/FitCore/gym-backend/src/main/java/com/fitcore/gym/service/TrainerService.java), [TrainerAvailability.java](/Users/roshan/Desktop/FitCore/gym-backend/src/main/java/com/fitcore/gym/service/TrainerAvailability.java), [TrainerSessionDto.java](/Users/roshan/Desktop/FitCore/gym-backend/src/main/java/com/fitcore/gym/dto/trainer/TrainerSessionDto.java) |
| Persistence locks and conflict query | [UserRepository.java](/Users/roshan/Desktop/FitCore/gym-backend/src/main/java/com/fitcore/gym/repository/UserRepository.java), [TrainerProfileRepository.java](/Users/roshan/Desktop/FitCore/gym-backend/src/main/java/com/fitcore/gym/repository/TrainerProfileRepository.java), [TrainerSessionRepository.java](/Users/roshan/Desktop/FitCore/gym-backend/src/main/java/com/fitcore/gym/repository/TrainerSessionRepository.java) |
| UTC clock | [TimeConfiguration.java](/Users/roshan/Desktop/FitCore/gym-backend/src/main/java/com/fitcore/gym/config/TimeConfiguration.java) |
| React integration | [Membership.jsx](/Users/roshan/Desktop/FitCore/gym-frontend/src/pages/Membership.jsx), [Trainers.jsx](/Users/roshan/Desktop/FitCore/gym-frontend/src/pages/Trainers.jsx), [api.js](/Users/roshan/Desktop/FitCore/gym-frontend/src/services/api.js) |
| Backend tests | [MembershipRegressionTest.java](/Users/roshan/Desktop/FitCore/gym-backend/src/test/java/com/fitcore/gym/service/MembershipRegressionTest.java), [MembershipMidnightTest.java](/Users/roshan/Desktop/FitCore/gym-backend/src/test/java/com/fitcore/gym/service/MembershipMidnightTest.java), [BookingRegressionTest.java](/Users/roshan/Desktop/FitCore/gym-backend/src/test/java/com/fitcore/gym/service/BookingRegressionTest.java), [PostgresConcurrencyTest.java](/Users/roshan/Desktop/FitCore/gym-backend/src/test/java/com/fitcore/gym/concurrency/PostgresConcurrencyTest.java), [TrainerServiceTest.java](/Users/roshan/Desktop/FitCore/gym-backend/src/test/java/com/fitcore/gym/service/TrainerServiceTest.java) |
| Browser/API tests and runners | [api-contract.test.mjs](/Users/roshan/Desktop/FitCore/gym-frontend/scripts/api-contract.test.mjs), [phase2-ui.pw.js](/Users/roshan/Desktop/FitCore/gym-frontend/scripts/phase2-ui.pw.js), [phase2-integrated.pw.js](/Users/roshan/Desktop/FitCore/gym-frontend/scripts/phase2-integrated.pw.js), [verify-postgres.sh](/Users/roshan/Desktop/FitCore/gym-backend/scripts/verify-postgres.sh), [verify-phase2-ui.sh](/Users/roshan/Desktop/FitCore/gym-backend/scripts/verify-phase2-ui.sh) |
| Documentation | This report and [phase2-readonly-audit.sql](/Users/roshan/Desktop/FitCore/docs/sql/phase2-readonly-audit.sql) |

Pre-existing unrelated workspace changes and untracked interview/readiness documents were preserved.

## Remaining deployment risks and readiness

- **Existing data is the rollout blocker:** both members with current periods have ambiguous overlapping entitlements. Resolve those memberships under a separately approved, evidence-backed procedure before deploying the fail-closed behavior to them. Administrative review access remains available.
- **Deploy frontend and backend together:** the new frontend uses dedicated renewal/upgrade routes. Old clients calling purchase for those actions will receive 409 instead of creating duplicate rows. There is no compatibility path that silently repeats the old unsafe behavior.
- **Preserve the Phase 1 production settings:** confirm SQL initialization is `never`, demo seeding is disabled, the existing database/credentials remain selected, private administrator access works, and backup recovery has been verified before an approved rollout. This task did not alter those settings or certify a backup.
- **Application writers must use the lock convention:** direct database writes are outside its protection. No automatic constraint migration or duplicate cleanup was applied.
- **Availability must be configured intentionally:** existing empty schedules remain unrestricted. A future nonempty schedule must use the documented UTC format. The UI continues to book one-hour sessions; the API accepts other valid whole-second, same-day intervals.
- **Historical pricing is unchanged:** memberships reference mutable plan records; this patch does not create a financial ledger or reconstruct historical payments.
- **Local changes stay local:** retain the feature branch for review. Do not push or merge to `master`, which automatically deploys to Render. No deployment is authorized by completion of local verification.
