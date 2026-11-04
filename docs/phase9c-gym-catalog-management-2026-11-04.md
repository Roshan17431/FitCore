# Phase 9C — gym catalog management

4 November2026 (Asia/Kolkata). Local implementation on `codex/phase9a-clean-instance`, based on `e669f08476bf6c9c7e8c26935f8526915ab843ef`. The owner approved the [conservative V9 design](superpowers/specs/2026-11-04-gym-catalog-management-design.md). All changes remain uncommitted. No production access, persistent database, real email, hosting change, commit, push, merge or deployment is part of this work.

**Phase9C local implementation and verification: PASS. Production and persistent/hosted demonstration: BLOCKED.** The startup prohibition is unchanged; a certified schema does not authorize persistent startup.

## Features and existing relationships

OWNER and gym-scoped ADMIN can manage their own exercises, workout plans and nutrition plans at `/gym/catalogs`. Members and trainers retain their existing public libraries, with informative empty states. Managers receive appropriate creation links from empty exercise/workout/nutrition libraries and membership plans. Existing membership-plan management remains the source of membership creation actions; no duplicate membership API was introduced.

The screen supports create/edit, search, category and lifecycle filters, workout publication, archival confirmations, loading/error/empty states and responsive light/dark presentation. Nutrition figures are labeled gym-provided estimates. Existing shared components and emerald/graphite tokens are reused. No global catalog or automatic seed data is created.

The existing model stores workout-plan exercises as **name strings**, schedule entries as strings, nutrition meals as strings, and workout exercise logs as exercise-ID references. New selections accept IDs, validate every ID in the authenticated gym and persist the canonical local names. No normalized exercise-link table, meal entity or implicit cross-gym association was invented. Existing UUIDs, account credentials, owner relationships and historical activity remain unchanged.

### Archival and history rules

- Exercises and nutrition plans have an archival flag. Archived records remain in management/history, but leave active public libraries. There are no hard-delete or unarchive endpoints for them.
- A referenced exercise name cannot change when any same-gym workout plan or workout log uses it. Managers can edit its descriptive metadata or create a replacement exercise. No automatic rename/remapping occurs.
- An exercise cannot be archived while a published same-gym plan references its name. Edit/archive that plan first. Existing log metrics and exercise IDs remain intact; new logs reject archived exercises.
- New managed workouts start DRAFT. PUBLISHED plans are visible in the public workout-plan library. ARCHIVED plans retain their stored names/schedules and leave that library. Publication requires at least one active local exercise with no unresolved names.
- An update that omits `exerciseIds` preserves existing names, including unresolved legacy names. An explicit `[]` clears selections only when the final status is DRAFT. Explicit replacement validates all selected IDs. A metadata-only edit to an already PUBLISHED legacy plan preserves its list; explicit publication always revalidates it.
- Existing uniqueness is gym-scoped and case-sensitive: exercise name, workout level/name and nutrition category. Archived records continue reserving these keys. Existing string collection storage has no newly guaranteed ordering.

## Backend API and authorization

All paths below start `/api/admin/catalog`. All 16 endpoints require authenticated OWNER/ADMIN plus `TenantAccess.manager()`. Every operation derives the gym from the current `TenantActor`; body ownership fields are rejected by the existing write guard. No global ADMIN bypass is added.

| Resource | List / create | Individual read / edit | Lifecycle |
| --- | --- | --- | --- |
| Exercises | `GET /exercises`, `POST /exercises` | `GET /exercises/{id}`, `PUT /exercises/{id}` | `POST /exercises/{id}/archive` |
| Workouts | `GET /workouts`, `POST /workouts` | `GET /workouts/{id}`, `PUT /workouts/{id}` | `POST /workouts/{id}/publish`, `POST /workouts/{id}/archive` |
| Nutrition | `GET /nutrition`, `POST /nutrition` | `GET /nutrition/{id}`, `PUT /nutrition/{id}` | `POST /nutrition/{id}/archive` |

Creates return201; successful reads/edits/actions200. Foreign record/nested exercise IDs return404 without revealing ownership. Invalid payloads return400, duplicates/reference conflicts409, and unauthorized roles403. Requests whitelist business fields and validate bounded strings, enum values, list sizes, distinct positive exercise IDs and nonnegative integer nutrition estimates (calories≤20000; macros≤2000). Schedule/meal strings remain≤255 characters, image references≤255 to match existing storage.

Every manager write is transactional and serializes on the gym row. Exercise locks use ascending IDs. Existing PostgreSQL/Hibernate gym locking uses `FOR NO KEY UPDATE`, preserving compatibility with gym foreign-key checks. Workout logging acquires sorted read locks on local exercises before checking archival status and reuses those locked objects through insertion. This prevents rename/archive racing between validation and log creation. No booking or membership concurrency rule changes.

### Image references

No upload or server-side fetch is added. New image references allow project-local `/images/...` paths or exact HTTPS origins explicitly configured through `fitcore.catalog.image-origins` (comma-separated; empty by default). Traversal, encoded/control characters, credentials, query strings, fragments and unapproved origins are rejected. Image length follows the actual existing255-character column.

Unchanged legacy image metadata can be retained without becoming trusted. The backend computes `imageApproved`; the frontend renders only approved references with `referrerPolicy="no-referrer"` and the existing technique fallback on load failure. This flag is nullable/read-only in the preexisting exercise DTO so old workout-log requests do not need to send it. The category grouping and technique-note fallback also treat prototype-like names (such as `constructor` and `__proto__`) as ordinary text, with regression tests. Public catalog meals, schedules and exercise badges wrap maximum-length unbroken values on mobile. No browser JWT/token transport changes are made.

## V9 and schema certification

Only `V9__gym_catalog_lifecycle.sql` is new:

| Table | Addition | Existing-data behavior |
| --- | --- | --- |
| `exercises` | `archived boolean NOT NULL DEFAULT false` | Existing exercises stay active |
| `nutrition_plans` | `archived boolean NOT NULL DEFAULT false` | Existing plans stay active |
| `workout_plans` | `status varchar(16) NOT NULL DEFAULT 'PUBLISHED'` plus exact DRAFT/PUBLISHED/ARCHIVED CHECK | Existing public plans stay visible |

Lock/statement timeouts are5/30seconds. V9 does not seed catalogs, map owners, change references or alter historical rows. New manager workout creation explicitly chooses DRAFT rather than relying on the compatibility default. V1,V2,V4–V8 remain byte-identical; no V3 is created.

The certified application/schema pair now targetsV9; earlier V8 readiness contracts do not certifyV9. A future authorized release must coordinate artifacts and migration. There is no automatic destructive down migration: an approved rollback must use compatible code or a certified prior backup/restoration plan, accounting for any writes after the recovery point. Local disposable rehearsal is not a real production recovery certification.

Readiness now requires successful ordered history `1,2,4,5,6,7,8,9`. The schema descriptor is strengthened to include normalized column defaults, so a changed archival/publication default fails closed. It continues hashing CHECK constraints, indexes, functions and triggers; no security component is excluded. Exact V9 contracts replace the prior V8 contracts:

| Certified PostgreSQL18 path | SHA256 |
| --- | --- |
| Fresh V9 | `257f2bf8e720e2ecb27c516ecbd216d4f16b77581abd9312b432821d2da42c7f` |
| Full V9 archive/restore | `a37bfba3f80e1e41c445e62baeed36182e9bdb6d6b5e3852ee8888d69c5ea9c7` |
| V2/V4/V5 archive then upgrade toV9 | `b64ef5d178b784b4697de334a688a57af484a3b5c2108deb9b1571b0e1f5c4ad` |
| V6 archive then upgrade toV9 | `6b752e4590ff80986b2ee38be642a7e17e67949aa095c893e9c081348b9de8f5` |
| V8 archive then upgrade toV9 | `95097ba59ee4c182505e1946fdecab880092ae30a196e30a1dc29a941644e487` |

Before acceptance, an independent reviewer reproduced all hashes and compared seven complete structural descriptors: fresh, fullV9 restore, and stagedV2/V4/V5/V6/V8 restores followed byV9. Each contains989 records: the prior982 are unchanged apart from already reviewed PostgreSQL parser/search-path renderings, and exactly7 are added (3 columns,3 NOT NULL constraints and1 CHECK). Object keys and all other structural fields match. V9 contributes one additional equivalent CHECK array-cast representation across fresh/restored paths. Production hashing uses exact contracts; the test-only equivalence helper is not production normalization.

Fresh/full-restored restricted-login readiness and actual HTTP readiness retain their positive tests. Negative tests retain unknown-schema, ownership-trigger, tenant-FK, migration-history and excessive-privilege cases; add changed/dropped archival defaults, wrong workout default, relaxed status CHECK and missingV9 history; and exercise actual invalid-status INSERT rejection. Existing V8 alone is no longer a current ready schema. Flyway validates resolved checksums separately; the runtime probe does not replace operator inventory, real backup certification or authorization.

## Verification evidence

Baseline `./scripts/verify-release.sh` passed before source changes:954 backend,35frontend,44 distinct browser groups,8release-gate checks. Test development reproduced missing migration/API, old request compatibility, publication/empty-selection and category-grouping issues before their fixes. Final counts and screenshot review are recorded below.

Final `./scripts/verify-release.sh` exited0. Both browser harness invocations verified their owned processes/ports/container cleanup.

| Verification | Result |
| --- | --- |
| Backend regressions, tenant/security, PostgreSQL migration/concurrency/recovery | **1063 passed; 0 failures/errors/skips** (954 existing +109 catalog tests) |
| New catalog API/migration/descriptor suites | **106 +2 +1 passed** |
| Frontend contracts/session/roles/theme | **46 passed** (35 existing +11) |
| Release-result gate regressions | **11 passed** |
| Browser workflows | **53 distinct passed; 65 executed groups** including12 repeated transaction/security groups |
| Frontend lint / production build | **PASS / PASS** |
| Fresh/restored restricted-runtime and actual HTTP readiness | **PASS / PASS**, currentV9 contract |
| Seven complete descriptor paths / Flyway checksum validation | **PASS**,989 records each, five exact hashes |
| Source preservation, shell syntax, diff whitespace, new document links | **PASS** |

Browser breakdown:8 transactional membership/booking,10 existing role/module,3 tenant analytics/CSV,16 owner/session/catalog-availability,9 new catalog groups,3 onboarding-disabled and4 security groups. Instrumented catalog/owner/role/isolation collectors recorded zero uncaught browser errors and zero external requests. New catalog checks cover manager create/edit/publish/archive, server validation, draft visibility, omitted-vs-empty selections, archival conflicts/cancellation, GymB foreign list/item/write/archive/nested reference denial and rollback, MEMBER/TRAINER restrictions, ADMIN access, approved-image fallback, themes and maximum-length/prototype-like text on public mobile pages.

Two initial complete runs reached the new runner's final ADMIN login assertion and failed because it expected `/dashboard` while the existing login correctly restored `/workouts/library`. A same-URL login revisit retained that return destination. The corrected runner checks the authenticated ADMIN identity and opens Catalogs directly; product login/authentication behavior is unchanged. The final complete gate supersedes both failures. Independent backend/frontend reviews found no remaining actionable issues after the documented regression fixes.

All342 candidate non-document source/config files were fingerprinted and remained unchanged during the final gate. This is an uncommitted local working-tree result; hosted CI and an eventual immutable release artifact still require separate verification.

### Screenshots

Nine successful local captures were visually inspected. Desktop1440×1000 and mobile390×844 checks cover light/dark management, archived selections/confirmation, and boundary-length public content. Catalog tabs use local horizontal scrolling on narrow screens; page-level overflow checks pass. Finite screenshot animations are disabled to capture final tab colors.

- [Exercise management, desktop light](../output/playwright/phase9c-catalog-exercises-desktop-light.png)
- [Nutrition management, desktop dark](../output/playwright/phase9c-catalog-desktop-dark.png)
- [Management, mobile light](../output/playwright/phase9c-catalog-mobile-light.png) and [mobile dark](../output/playwright/phase9c-catalog-mobile-dark.png)
- [Archive confirmation, mobile dark](../output/playwright/phase9c-catalog-archive-mobile-dark.png) and [retained archived workout selection](../output/playwright/phase9c-catalog-archived-selection.png)
- Boundary public mobile: [exercise/name/category](../output/playwright/phase9c-catalog-boundary-exercise-mobile.png), [workout schedule/selection](../output/playwright/phase9c-catalog-boundary-workout-mobile.png), [nutrition meal](../output/playwright/phase9c-catalog-boundary-nutrition-mobile.png)

The full-page modal capture includes off-viewport fixed-page content above the active overlay; the live viewport dialog's focus trap, Escape, cancel and action checks pass. Screenshots are ignored local artifacts, not committed source. Earlier failure captures/logs are diagnostic evidence, not successful-gallery evidence.

Evidence is outside Git in sibling `evidence/phase9c-*.log`, preservation manifests and `phase9c-catalog-schema-descriptors.json`. The certification test writes structural metadata to ignored `gym-backend/target/catalog-schema-certification.json`. All test accounts/rows are synthetic, databases are disposable marked loopback PostgreSQL18.6, archives are temporary, and no real recipients or production ownership manifest is used. Focused API tests cover all16 routes, direct service authorization, independent GymA/B records, foreign nested IDs, rollback, duplicates, history, archive/publication, limits and genuine lock contention. Existing auth/outbox/recovery/session/security suites remain mandatory.

## Source inventory

New backend: `controller/CatalogController.java`, `domain/WorkoutPlanStatus.java`, `dto/catalog/CatalogDtos.java`, `service/{CatalogService,CatalogImagePolicy}.java`, `db/migration/V9__gym_catalog_lifecycle.sql` (paths under `gym-backend/src/main/{java/com/fitcore/gym,resources}`). New tests under `gym-backend/src/test/java/com/fitcore/gym/tenancy`: `CatalogManagementTest.java`, `CatalogMigrationTest.java`, `CatalogSchemaCertificationTest.java`.

Modified backend Java: `entity/{Exercise,WorkoutPlan,NutritionPlan}.java`; `dto/workout/ExerciseDto.java`; `repository/{ExerciseRepository,WorkoutPlanRepository,NutritionPlanRepository,WorkoutExerciseLogRepository}.java`; `service/{WorkoutService,NutritionService}.java`; `operations/{SchemaContract,ReleaseReadiness}.java`. Existing runtime/ORM test fixtures move toV9 in `AccountSecurityTest`, `QueuedAccountEmailTest`, `EmailOutboxTest`, `FoundationMigrationTest`, `CleanInstanceHttpTest`, `CleanInstanceRecoveryTest`; `SchemaReadinessCertificationTest` gains current-schema drift checks; `TenantIsolationTest` retains existing route expectations and adds the new endpoint set.

New frontend: `src/pages/Catalogs.jsx`; `src/components/catalog/{CatalogEditor,CatalogFields,CatalogRecord}.jsx`; `src/components/common/CatalogEmptyState.jsx`; `src/utils/catalog.js`; `scripts/{catalog-contract.test.mjs,catalog-integrated.pw.js}`. Modified frontend: `src/App.jsx`, `src/layouts/MainLayout.jsx`, `src/pages/{Membership,Workouts,Nutrition}.jsx`, `src/components/common/ExerciseMedia.jsx`, `src/services/api.js`, `scripts/{api-contract,tenant-contract}.test.mjs`.

Verification wiring: `gym-backend/scripts/verify-phase2-ui.sh` invokes the new catalog browser workflow; `scripts/check_release_results.py` requires all three new PostgreSQL suites; `scripts/test_release_results.py` checks each missing-suite failure. Documentation: this report, the approved design, implementation plan and release-checklist companion entry.

Source fingerprints confirm all seven original migrations, `FoundationSafetyConfiguration`, `MembershipService` and `TrainerService` match the baseline. Original Phase8/9 decision documents in the other worktree match their prior evidence manifests. Other branches/private remediation files are preserved.

## Limitations and Phase 9D readiness

The conservative MVP does not normalize historical name links or snapshot descriptive metadata. Exercise names with references remain immutable; old unresolved plan names require an explicit manager replacement before re-publication. Archival does not erase saved history. There is no bulk import, upload, pagination, exercise/nutrition reactivation, catalog seed or new exercise ordering guarantee. Existing gym uniqueness includes archived rows. Catalog list rendering resolves stored names against local exercises; it is suitable for the current small catalogs, with large-catalog query optimization deferred.

Phase9D may prepare the separate clean-instance security/deployment design after local acceptance. Persistent/hosted execution remains blocked by: approved target identity/region/budget and access; reviewed startup authorization mechanism; restricted real DB roles, TLS/CORS/secrets; browser JWT/XSS/CSRF decision; trusted proxy and shared limiter configuration; email provider/domain/webhook/suppression/idempotency approvals; outbox key custody/rotation/recovery, supervised worker and cleanup; actual backup/restore/RPO/RTO certification; monitoring/audit/on-call; hosted CI/required checks, security review and immutable candidate artifacts; coordinated deployment/rollback approval. Public production onboarding and real sending remain disabled.

The clean-empty release path needs no invented legacy ownership mapping. Migrating the existing production application still requires the separate authorized inventory and approved operationalV3 decisions. Catalog completion does not resolve or execute those decisions. No production readiness is claimed.
