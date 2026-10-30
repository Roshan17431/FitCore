# Multi-tenant foundation implementation — 2026-10-30

Status at implementation completion: isolated development only, before source-publication authorization. No customer database or production service was accessed. Subsequent commit/push authorization and publication verification are recorded in [the publication report](multi-tenant-foundation-publication-2026-10-30.md). This implements foundation tasks 1 and 2 of the approved Phase 1 plan; endpoint isolation, tenant JWTs, onboarding and the operational ownership runner are later tasks.

## Development baseline and safety

The dedicated branch `codex/multi-tenant-saas` starts at main commit `4ad96321f584dbfaaa2b3a9885bbf04f9cbe1d0f`. Its checkout is `/Users/roshan/.codex/workspaces/fitcore-multi-tenant-saas/repository`. The original Desktop checkout, other branches, uncommitted design documents and private remediation files were left in place. Native worktree creation failed while reading cloud-offloaded Git objects; an independent clone provides the requested isolation instead. No cherry-picks or phase-branch integrations were needed for implementation. For the separately authorized publication, the checkout was fast-forwarded to the existing default-branch head 930d5b0 before creating the foundation commits; that brought in the already published design documents and Vercel deployment guards without changing baseline application code.

Before source changes, the existing disposable backend runner passed 281 tests. Frontend dependency installation, 19 Node tests, lint and production build passed. Sanitized command evidence is retained outside Git at `/Users/roshan/.codex/workspaces/fitcore-multi-tenant-saas/evidence`. The old integrated browser harness was not run before source changes; its post-change results are recorded below.

Application Flyway execution defaults to disabled, baseline-on-migrate is false, clean is disabled, and Hibernate defaults to validation. Automatic `.env` imports are removed from this foundation build. An early startup guard refuses PostgreSQL except the exact loopback disposable database names and a live database marker matching the harness-owned container ID. In-memory H2 remains available through the test-scoped dependency. The Maven runner clears inherited application/JVM settings. Startup also rejects independent Hikari, JNDI, datasource-type and Flyway connection selectors before accepting a URL; H2 initialization scripts are forbidden. There is no opt-out flag, unaffiliated-account fallback gym, or new global-admin bypass. This build must remain blocked from customer traffic until backend isolation is complete.

## Implemented model and relationships

- `Gym`: UUID, name, globally unique slug, ACTIVE/SUSPENDED status, timestamps and primary OWNER association.
- `User`: existing UUID/credentials/role and business relationships retained; adds immutable gym UUID, read-only Gym association and non-null authorization version defaulting to zero. OWNER extends the existing role enum; no platform role is added.
- `GymInvitation`: gym, globally unique token digest (no plaintext token), email, MEMBER-only role, same-gym creator, expiry and acceptance/revocation timestamps. No invitation endpoint or delivery is implemented.
- `TenantOwnedEntity` supplies one writable gym ID for all 15 existing entities and invitations. Java setters refuse reassignment after the first assignment; SQL triggers independently prohibit it in the final schema. All six element collections carry the parent gym in their join keys.
- Narrow Gym/invitation repositories provide persistence, scoped invitation lookup and locked gym/token lookups. Existing business repositories are deliberately not yet converted to tenant-scoped reads.

Every final gym must designate an enabled OWNER in that gym at transaction commit. The composite owner FK and deferred trigger allow gym creation, OWNER creation and designation in one transaction. Missing, foreign, disabled or demoted primary owners are rejected; pointer replacement is prohibited. Legacy NULL enabled flags retain existing `User.isEnabled()` semantics. No account is enabled, assigned, or promoted by a migration in this branch.

The final schema covers memberships → user/plan; attendance, food, notifications, nutrition goals, progress and workouts → user; trainer profiles → user; sessions → member/trainer; workout entries → workout/exercise; and all six collections → their parent. Each uses `(gym_id, parent_id)` references while existing primary keys and individual foreign keys remain. Gym-leading indexes support the approved query shapes. Email remains globally unique. Plan/exercise names, workout level and nutrition category become unique within a gym. Membership overlaps remain legal historical data; no new active-membership, open-attendance or booking uniqueness rule is introduced.

Existing record-creation paths now propagate the owning user's/parent's gym, and managed plan/trainer creation derives it from the authenticated account. Trainer account/profile creation is transactional. Ordinary HTTP and service user editing cannot assign OWNER; the public demo-password restriction also covers OWNER. Membership dates, renewal/upgrade decisions, booking locks and conflict checks, attendance calculations, nutrition, progress and workout rules are unchanged.

## Prepared migrations

| Version | Purpose | Data behavior |
| --- | --- | --- |
| V1 | Existing FitCore schema on a clean PostgreSQL database | Captured from the unchanged baseline application on an empty disposable PostgreSQL 18 database, including 15 entity tables, six collection tables, identities, checks and foreign keys. No demo/customer rows. |
| V2 | Add gyms/invitations, nullable ownership, authz version and supporting keys | Fingerprint-checks the source-baseline schema before expansion. Retains old global catalog uniqueness. Does not choose gyms, owners, mappings or copies. |
| V3 | Reserved operational manifest validator/assignment runner | Intentionally deferred to task 8 of the approved plan. No production manifest or operational backfill is supplied. |
| V4 | Enforce required ownership, same-gym integrity, owner protections, scoped catalog uniqueness and indexes | Rejects missing mappings and inconsistent references. Does not infer or repair them. Tests stage explicit synthetic ownership at V2 before applying V4. |

The V2 catalog helper installs scoped uniqueness before removing old global uniqueness, then approved synthetic copies/remaps occur in the same transaction. PUBLIC cannot execute this schema-changing helper. Its production caller must be the separate migration role. V4 can initialize empty catalogs and verify the established scoped constraints.

**This is not a persistent-database migration bundle yet.** Complete V3 and the full release sequence before applying any version to a persistent environment. Never apply V4 there now and later attempt to insert V3 out of order. Actual deployed schema, defaults, custom objects, sequence state, PostgreSQL version, backup and ownership decisions require separately authorized certification; the source-derived fingerprint is not a claim about production.

No destructive down migration is supplied. PostgreSQL rolls back a failed individual migration transaction, including staged catalog constraint changes, but earlier successfully committed Flyway versions remain. Tests verify that a failed V4 leaves V2 history and original booking references intact. Future operational recovery must keep traffic stopped, inspect applied history and manifest receipts, then use a reviewed forward fix or restore a verified backup into a separate database. Do not run Flyway clean/repair, drop ownership columns, or deploy the old application against a partially migrated customer database as an automatic rollback.

## Verification

Latest backend run: `./scripts/verify-tenancy.sh` — **347 tests, zero failures/errors/skips; BUILD SUCCESS** (Java 24 compiler targeting Java 17; PostgreSQL 18). Of these, 44 cases exercise actual Flyway/PostgreSQL migrations and integrity. The baseline had 281 tests; additional cases cover the foundation and OWNER role. All PostgreSQL operations use the runner's marked disposable database and isolated schemas, not application configuration. `verify-postgres.sh` delegates to the same safe runner.

The migration/integrity suite tests clean initialization, explicit baseline adoption, schema/checksum drift rejection, no implicit ownership, same-name catalogs in separate gyms, local uniqueness, every parent/collection relationship, owner validity at actual COMMIT, concurrent primary-owner invalidations, immutable ownership, invitation restrictions, failed mixed-write rollback and ORM validation/round trips against the migrated schema. Synthetic legacy rehearsals preserve UUIDs, password hashes, timestamps, memberships including overlap, trainer identity and booking history; failed cross-gym bookings block enforcement without rewriting history. Failed catalog copies roll back both data and uniqueness changes.

The original business suites use gym-tagged fixtures and ORM-created H2/PostgreSQL schemas. Their pass is evidence for business regression only. Constraint evidence comes from PostgreSQL with actual Flyway migrations. The browser harness opts into Flyway only for its own empty, marked PostgreSQL database and uses explicit synthetic gym/OWNER/member/admin/catalog fixtures; it no longer relies on unaffiliated registration to create its administrator.

Browser verification: `FITCORE_VERIFY_PHASE3_UI=true ./scripts/verify-phase2-ui.sh` passed eight membership/booking scenarios and ten member/trainer/admin scenarios against the Flyway-created PostgreSQL schema. This includes the renewal race (one 200, one 409), persisted history, nutrition/progress/attendance writes and admin edits. No external requests or uncaught browser errors were reported. Owned processes, ports and container were cleaned up. Frontend source is unchanged.

## Review and resolution

The fresh whole-change review found one important safety defect: checking only the root datasource URL did not cover independent Hikari/Flyway destinations, and the Maven runner inherited application settings. That finding was accepted and corrected with environment isolation and early relaxed-property validation. Eleven tests first reproduced the bypass and then passed; another covers H2 INIT refusal. The final full verification passed with conflicting inherited Hikari/Flyway/JVM settings, verifying they are scrubbed. All 15 safety tests passed without contacting the forbidden example destinations. The review found no other blocking issue within the foundation scope. Its considered limitations (source-derived schema certification, later administration lock order, deferred tenant HTTP/JWT/V3 work) remain explicit gates below.

## Next-phase readiness and outstanding gates

Foundation structures are available for backend isolation. This application is not ready to serve multiple gyms: current JWTs, RecordAccess, global list/read queries, dashboards, exports and ordinary administrative target checks remain the baseline behavior. Authorization version is stored but is not yet a JWT claim or invalidation mechanism. OWNER management routes, invitation consumption and owner onboarding are not implemented here. Registration of an unaffiliated account cannot succeed against final NOT NULL ownership constraints; the next phase must supply the approved onboarding/invitation workflow before any customer traffic.

Next work, in dependency order:

1. Implement database-resolved tenant identity, UUID JWT subjects, gym/version claims, atomic authorization-version increments and inactive/unassigned/stale-session rejection while retaining credential-version/disabled protections.
2. Convert every repository and service access path to tenant scoping; validate nested references and role/target permissions before DTO construction. Preserve business lock order. Add two-gym HTTP/service read, write, aggregate and export denial tests.
3. Implement transactional OWNER onboarding and single-use MEMBER invitations, owner/admin target restrictions and the frontend contracts.
4. Complete the private-input V3 validator/runner and full recovery rehearsal, migration-role/runtime-role separation and release gates. Obtain explicit ownership/catalog/admin decisions and certify the actual schema/backups only under separate authorization.
5. Replace the foundation-only startup prohibition only after complete isolation tests and a reviewed cutover are ready. No production approval is implied by these test results.

## Files created and modified

The inventory below is relative to the isolated repository; no frontend product or hosting configuration files are changed.

- Modified: `gym-backend/pom.xml`
- Modified: `gym-backend/scripts/verify-phase2-ui.sh`
- Modified: `gym-backend/scripts/verify-postgres.sh`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/domain/Role.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/dto/admin/UserUpdateRequest.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/entity/ActiveMembership.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/entity/Attendance.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/entity/Exercise.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/entity/FoodLog.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/entity/MembershipPlan.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/entity/Notification.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/entity/NutritionGoal.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/entity/NutritionPlan.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/entity/ProgressMetric.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/entity/TrainerProfile.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/entity/TrainerSession.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/entity/User.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/entity/WorkoutExerciseLog.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/entity/WorkoutLog.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/entity/WorkoutPlan.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/security/CustomUserDetailsService.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/service/AdminService.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/service/MembershipService.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/service/NutritionService.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/service/TrackingService.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/service/TrainerService.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/service/WorkoutService.java`
- Modified: `gym-backend/src/main/resources/application.properties`
- Modified: `gym-backend/src/test/java/com/fitcore/gym/concurrency/PostgresConcurrencyTest.java`
- Modified: `gym-backend/src/test/java/com/fitcore/gym/security/SecurityRegressionTest.java`
- Modified: `gym-backend/src/test/java/com/fitcore/gym/service/BookingRegressionTest.java`
- Modified: `gym-backend/src/test/java/com/fitcore/gym/service/MembershipMidnightTest.java`
- Modified: `gym-backend/src/test/java/com/fitcore/gym/service/MembershipRegressionTest.java`
- Modified: `gym-backend/src/test/resources/application.properties`
- Created: `docs/multi-tenant-foundation-2026-10-30.md`
- Created: `gym-backend/scripts/verify-tenancy.sh`
- Created: `gym-backend/src/main/java/com/fitcore/gym/config/FoundationSafetyConfiguration.java`
- Created: `gym-backend/src/main/java/com/fitcore/gym/domain/GymStatus.java`
- Created: `gym-backend/src/main/java/com/fitcore/gym/entity/Gym.java`
- Created: `gym-backend/src/main/java/com/fitcore/gym/entity/GymInvitation.java`
- Created: `gym-backend/src/main/java/com/fitcore/gym/entity/TenantOwnedEntity.java`
- Created: `gym-backend/src/main/java/com/fitcore/gym/repository/GymInvitationRepository.java`
- Created: `gym-backend/src/main/java/com/fitcore/gym/repository/GymRepository.java`
- Created: `gym-backend/src/main/resources/db/migration/V1__baseline_existing_fitcore.sql`
- Created: `gym-backend/src/main/resources/db/migration/V2__expand_gym_ownership.sql`
- Created: `gym-backend/src/main/resources/db/migration/V4__enforce_gym_integrity.sql`
- Created: `gym-backend/src/test/java/com/fitcore/gym/config/FoundationSafetyTest.java`
- Created: `gym-backend/src/test/java/com/fitcore/gym/tenancy/FoundationMigrationTest.java`
- Created: `gym-backend/src/test/java/com/fitcore/gym/tenancy/RegressionGym.java`
- Created: `gym-backend/src/test/resources/tenant-migration-fixtures/README.md`
