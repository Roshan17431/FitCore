# Backend tenant isolation — Phase 3 — 2026-10-31

Implementation and verification are local to `/Users/roshan/.codex/workspaces/fitcore-multi-tenant-saas/repository` on `codex/multi-tenant-saas`, starting from `27d6b9675b0e69239a2f2fdcfbf539885c1a8f5c`. At implementation completion no commits, pushes, merges, deployments, persistent migrations, customer-data access or production configuration changes had occurred. The subsequent source-publication authorization and verification are recorded in [the publication report](backend-tenant-isolation-publication-2026-10-31.md). Other checkouts/branches and private remediation documents remain untouched.

## Authentication and trusted identity

JWT schema 2 uses the stable account UUID as `sub`, with required `tokenVersion=2`, `gymId`, integer `authzVersion`, keyed `credentialsVersion` and expiry. The factory also emits issue time; its remaining validation limit is documented below. Existing signing configuration, lifetime and BCrypt hashing remain. Authentication loads the current account by UUID, rejects disabled accounts and public ADMIN/OWNER demo credentials, compares current gym and both versions, and checks the current gym status. Legacy, malformed, tampered, expired, missing-claim and stale tokens receive 401. An otherwise valid session in a suspended gym receives 403 `GYM_UNAVAILABLE`. Valid credentials without an assigned gym receive 403 `GYM_ASSIGNMENT_REQUIRED` and no token.

The principal is immutable `TenantActor(userId,gymId,role,authzVersion)` constructed from database state; JWT role claims, browser roles, body IDs and headers cannot set tenant authority. `TenantContext` reads only that authenticated principal. No mutable custom global/thread tenant state exists, and the JWT filter clears framework authentication in `finally`. Explicit public method/path rules replace `/api/auth/**`; the added `GET /api/auth/me` is protected and returns safe session fields plus current gym/version metadata.

V5 adds a PostgreSQL row trigger that atomically increments authorization version on changes to role, email, enabled state, gym or password hash. It prevents version decreases and negative versions. Account-management transactions also increment the version under gym-then-user locks, supporting ORM-created regression schemas and keeping concurrent updates serialized. Version and state changes roll back together. Re-enabling an account cannot restore its old JWT. Current requests are authorized from the state loaded for that request; subsequent requests reload it, rather than claiming retroactive cancellation of in-flight work.

## Authorization and repository conversion

All 15 business repository interfaces now extend a narrow `TenantRepository`, with gym-scoped ID/list methods and controlled persistence. Their used parent/date/status/role/count/overlap methods include explicit gym predicates. Global `findById`, `findAll`, bulk deletion and email lookup APIs have been removed from runtime business repositories. Fixture-only global repositories live on the test classpath. A source architecture test prevents global business reads and restricts `UserIdentityRepository` references to authentication and the boolean-only email uniqueness helper.

Every service resolves the trusted gym before querying, authorizes a scoped target before mapping or traversal, and assigns ownership from the actor or authorized parent. `RecordAccess` has no global ADMIN bypass. Foreign/missing targets receive the same generic 404. A local target outside a MEMBER/TRAINER's self permission receives 403. Catalog reads, all dashboard inputs, charts, role counts, history summaries, attendance and browser-export sources are scoped before DTO construction. DTO mappers also check local ownership. Same-gym composite FKs and collection join keys remain intact.

OWNER and ADMIN can manage local business records. ADMIN can edit only MEMBER/TRAINER targets and assign those roles. OWNER can additionally manage ADMIN accounts. Every OWNER target is protected, including the designated primary owner. The ordinary input enum contains MEMBER/TRAINER/ADMIN only; OWNER/platform grants cannot deserialize into it. Gym locks precede privileged user locks. Trainer account/profile creation and updates, workout parent/children, and other write operations are transactional. All exercise references are checked before inserting a workout parent. Booking preserves member-then-trainer-profile locking and the original interval expression, with gym scope outside the trainer/member OR.

Explicit tenant/owner/version/hash/platform fields, including nested fields, and client gym selectors are rejected with 400. Legacy informational user/member DTO fields remain informational; ownership comes from authorized path/database records. The frontend booking API now serializes only business fields, avoiding replay of the new session gym/version metadata. No feature redesign or frontend onboarding was added.

## Deliberately closed paths

Public registration returns 403 `INVITATION_REQUIRED` without creating an account until Phase 4. Owner registration and invitation creation/acceptance are not implemented. Forgot-password retains its generic 202 placeholder; no reset delivery is added. Unowned image upload/deletion remain absent, and enabling S3 aborts startup. Public health and API schemas contain no tenant records.

The foundation-only startup prohibition remains active: only marked owned disposable PostgreSQL or restricted test-scoped in-memory H2 is accepted. Independent Hikari/JNDI/Flyway connection selectors and inherited application/JVM settings remain blocked/scrubbed. Flyway remains disabled for ordinary application startup, and Hibernate remains `validate` by default. V5 is prepared and exercised only in disposable databases. V3 operational mapping, real ownership assignments, production schema certification and deployment remain deferred.

## Verification and isolation evidence

Baseline before changes: **347 backend tests passed**, no failures/errors/skips. The pre-review full run passed **843 tests, zero failures/errors/skips**; post-review final results are recorded below.

- `TenantIsolationTest` runs real signed HTTP requests through Spring Security, actual services/repositories and the Flyway-created PostgreSQL schema. Its application datasource is a distinct non-superuser DML-only runtime role; migration setup checks the exact loopback URL/database and live owned-container marker before using separate setup credentials. Runtime cannot run DDL, execute the catalog migration helper, read Flyway history, or read the synthetic operator-input schema.
- Synthetic A/B gyms each have separate owner, admin, trainer, two members, identical catalog names, and data in every personal/relationship/collection table. Targeted endpoints are tested for all four caller roles in both directions, with complete database snapshots before/after denial. Local personal operations and management retain expected success. Local/foreign mixed workout references leave no partial rows; foreign plans, trainers and membership IDs are rejected before writes.
- Lists, nested DTOs, counts, charts and report inputs exclude foreign sentinels. Role counts, active membership and revenue card values are checked exactly. Owner target/assignment policy is tested in both gyms. Concurrent A/B subscriptions/bookings succeed independently, preserve member/trainer contention behavior, and leave framework contexts empty; sequential A/B and unauthenticated requests do not inherit context.
- Session tests cover required claims, legacy tokens, tampered signatures, forged role claims, gym mismatch, disabled accounts, suspended/unassigned identities, stale versions and password protections. PostgreSQL tests verify atomic version changes and old-token rejection after role changes. Existing membership/booking/date/midnight/seed/security and PostgreSQL concurrency suites remain.
- A test compares the endpoint enum with controller mappings, failing if any active route is unaccounted for. The matrix has all 44 existing endpoints, plus the new authenticated `GET /api/auth/me`; image routes are tested as absent while their activation is prohibited.
- Real browser checks use two synthetic gyms. Existing membership and booking flows (8 scenarios) and member/trainer/admin workflows (10 scenarios) pass. Three additional scenarios verify A-to-B denial and both gyms' dashboard, attendance API and actual generated/downloaded CSV contents. No foreign user ID appears in the other gym's CSV. No external requests or uncaught browser errors were reported; owned servers/ports/container were cleaned up.
- Frontend `node --test`: **20 passed**, including a red-then-green booking payload regression. Lint and build pass. No backend security is disabled for frontend compatibility.

Membership renewal/upgrade/expiry formulas, package prices, historical overlap handling, booking overlap/availability and lock order, attendance calculations, workout/nutrition/progress behavior and existing historical rows are preserved. No customer records were migrated or repaired. Authentication no longer mutates account timestamps during denied reads; the full snapshot tests reproduced and verified that fix.

## Endpoint coverage matrix

All protected entries require a validated active-gym TenantActor. S = local target, then self or manager; C = local catalog; M = local OWNER/ADMIN with protected-target policy. Targeted rows receive bidirectional foreign-ID and unchanged-state tests; personal rows also receive same-gym role/success coverage. Lists/aggregates use whole-response foreign exclusion; auth/public/conditional rows use explicit policy tests. CSV has both API and real-browser coverage.

| # | Method/route | Enforced policy | Verification |
| --- | --- | --- | --- |
| 1 | `POST /api/auth/register` | Closed: INVITATION_REQUIRED | Public/auth session cases |
| 2 | `POST /api/auth/login` | Global credential authentication; assigned active gym | Public/auth session cases |
| 3 | `POST /api/auth/forgot-password` | Generic accepted; no disclosure | Public/auth session cases |
| 4 | `GET /api/health` | Public static UP | Public/auth session cases |
| 5 | `GET /api/users/{id}` | S | Local success; role checks; A↔B denial; unchanged writes |
| 6 | `GET /api/dashboard/{userId}` | S | Local success; role checks; A↔B denial; unchanged writes |
| 7 | `GET /api/notifications/{userId}` | S | Local success; role checks; A↔B denial; unchanged writes |
| 8 | `GET /api/memberships/plans` | C | Local scoped-list/nested response cases |
| 9 | `POST /api/memberships/subscribe/{userId}/{planId}` | S | Local success; role checks; A↔B denial; unchanged writes |
| 10 | `GET /api/memberships/user/{userId}` | S | Local success; role checks; A↔B denial; unchanged writes |
| 11 | `GET /api/memberships/user/{userId}/active` | S | Local success; role checks; A↔B denial; unchanged writes |
| 12 | `POST /api/memberships/renew/{userId}/{membershipId}` | S | Local success; role checks; A↔B denial; unchanged writes |
| 13 | `POST /api/memberships/upgrade/{userId}/{planId}` | S | Local success; role checks; A↔B denial; unchanged writes |
| 14 | `GET /api/trainers` | C | Local scoped-list/nested response cases |
| 15 | `POST /api/trainers/book/{memberId}/{trainerId}` | S | Local success; role checks; A↔B denial; unchanged writes |
| 16 | `GET /api/trainers/sessions/{memberId}` | S | Local success; role checks; A↔B denial; unchanged writes |
| 17 | `GET /api/workouts/exercises` | C | Local scoped-list/nested response cases |
| 18 | `GET /api/workouts/plans` | C | Local scoped-list/nested response cases |
| 19 | `GET /api/workouts/logs/{userId}` | S | Local success; role checks; A↔B denial; unchanged writes |
| 20 | `POST /api/workouts/logs/{userId}` | S | Local success; role checks; A↔B denial; unchanged writes |
| 21 | `GET /api/nutrition/dashboard/{userId}` | S | Local success; role checks; A↔B denial; unchanged writes |
| 22 | `GET /api/nutrition/plans` | C | Local scoped-list/nested response cases |
| 23 | `POST /api/nutrition/goal/{userId}` | S | Local success; role checks; A↔B denial; unchanged writes |
| 24 | `GET /api/nutrition/goal/{userId}` | S | Local success; role checks; A↔B denial; unchanged writes |
| 25 | `POST /api/nutrition/log/{userId}` | S | Local success; role checks; A↔B denial; unchanged writes |
| 26 | `GET /api/nutrition/logs/{userId}` | S | Local success; role checks; A↔B denial; unchanged writes |
| 27 | `POST /api/tracking/progress/{userId}` | S | Local success; role checks; A↔B denial; unchanged writes |
| 28 | `GET /api/tracking/progress/{userId}` | S | Local success; role checks; A↔B denial; unchanged writes |
| 29 | `POST /api/tracking/attendance/check-in/{userId}` | S | Local success; role checks; A↔B denial; unchanged writes |
| 30 | `PUT /api/tracking/attendance/check-out/{attendanceId}` | S | Local success; role checks; A↔B denial; unchanged writes |
| 31 | `GET /api/tracking/attendance/{userId}` | S | Local success; role checks; A↔B denial; unchanged writes |
| 32 | `GET /api/admin/dashboard` | M | Scoped aggregates and browser CSV |
| 33 | `GET /api/admin/users` | M | Local scoped-list/nested response cases |
| 34 | `PUT /api/admin/users/{userId}` | M | Local success; role checks; A↔B denial; unchanged writes |
| 35 | `DELETE /api/admin/users/{userId}` | M | Local success; role checks; A↔B denial; unchanged writes |
| 36 | `POST /api/admin/trainers` | M | Local success; role checks; A↔B denial; unchanged writes |
| 37 | `PUT /api/admin/trainers/{trainerId}` | M | Local success; role checks; A↔B denial; unchanged writes |
| 38 | `DELETE /api/admin/trainers/{trainerId}` | M | Local success; role checks; A↔B denial; unchanged writes |
| 39 | `POST /api/admin/membership-plans` | M | Local success; role checks; A↔B denial; unchanged writes |
| 40 | `PUT /api/admin/membership-plans/{planId}` | M | Local success; role checks; A↔B denial; unchanged writes |
| 41 | `DELETE /api/admin/membership-plans/{planId}` | M | Local success; role checks; A↔B denial; unchanged writes |
| 42 | `GET /api/admin/attendance` | M | Scoped aggregates and browser CSV |
| 43 | `POST /api/images/upload` | Absent; S3 activation refused | Conditional route/startup cases |
| 44 | `DELETE /api/images/delete` | Absent; S3 activation refused | Conditional route/startup cases |
| New | `GET /api/auth/me` | Validated session only | Anonymous/invalid/stale rejection and current gym response |

## Repository matrix

| Repository | Current access paths | Required scoped API / write validation | Required tests |
|---|---|---|---|
| UserRepository | findById; findByIdForUpdate; findAll; findByRole/countByRole; global findByEmail/existsByEmail; save | findByIdAndGymId(U,G); findByIdAndGymIdForUpdate(U,G) with same pessimistic lock; findAllByGymId(G); findByGymIdAndRole(G,role); countByGymIdAndRole(G,role). Global email/ID identity operations move to auth-only UserIdentityRepository. Save checks target gym and role policy | T2,T3,T6; no foreign row lock/update; global uniqueness preserved |
| ActiveMembershipRepository | findByUser*, findByStatus/countByStatus, findAll, findFirstByUser*, existsByPlan, save/flush | findByGymIdAndUserIdOrderByStartDateDesc(G,U); findByIdAndGymIdAndUserId(I,G,U); findByGymIdAndStatus(G,status); findAllByGymId(G); countByGymIdAndStatus(G,status) only if used; existsByGymIdAndPlanId(G,I). All saved user/plan/gym links consistent; current entitlement still uses dates, not status count alone | T2-T5; legacy duplicate handling unchanged |
| MembershipPlanRepository | inherited findAll/findById/save/delete | findAllByGymId(G); findByIdAndGymId(I,G); persistence/delete only after scoped resolution; local uniqueness/history guard | T3,T4,T6 |
| TrainerProfileRepository | findAll; findByUser; findById; findByIdForUpdate; save | findAllByGymId(G); findByGymIdAndUserId(G,U); findByIdAndGymId(I,G); findByIdAndGymIdForUpdate(I,G). Profile user must be local | T2-T6; member then profile lock order |
| TrainerSessionRepository | findByMember*, findByTrainer; existsScheduledOverlap; save | findByGymIdAndMemberIdOrderBySessionDateAscStartTimeAsc(G,U); findByGymIdAndTrainerId(G,I); existsScheduledOverlap(G,trainerId,memberId,date,start,end,status). Add gym predicate enclosing the trainer OR member condition; preserve strict interval comparisons | T2-T5; simultaneous independent gyms do not conflict |
| AttendanceRepository | findByUser*, global sorted/date lists/counts; findFirstByUserAndCheckOutTimeIsNull*; findById/save | findByIdAndGymId(I,G); findByGymIdAndUserIdOrderByCheckInTimeDesc(G,U); findAllByGymIdOrderByCheckInTimeDesc(G); findByGymIdAndCheckInDateBetween(G,start,end); scoped count if used; findFirstByGymIdAndUserIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(G,U) | T2-T4,T9 |
| WorkoutLogRepository | findByUserOrderByDateDesc; findByUserAndDateBetweenOrderByDateAsc; save | findByGymIdAndUserIdOrderByDateDesc(G,U); findByGymIdAndUserIdAndDateBetweenOrderByDateAsc(G,U,start,end); scoped parent write | T2-T4 |
| WorkoutExerciseLogRepository | findByWorkoutLog; save | findByGymIdAndWorkoutLogId(G,I); ensure parent and each Exercise local; batch transaction; no bare child lookup | T3,T4; mixed local/foreign batch leaves no partial log |
| ExerciseRepository | findAll; findByCategory; findById | findAllByGymId(G); findByGymIdAndCategory(G,category); findByIdAndGymId(I,G), or scoped bulk lookup with exact requested-ID count check | T3,T4; local name uniqueness |
| WorkoutPlanRepository | findAll | findAllByGymId(G); scoped lookup for future use only if needed; collection joins include gym | T3,T4; no foreign browser plan selection |
| NutritionPlanRepository | findAll | findAllByGymId(G); collection joins include gym | T3; category uniqueness local |
| NutritionGoalRepository | findByUser; save | findByGymIdAndUserId(G,U); upsert server-owned recipient, retain one goal per account | T2,T4 |
| FoodLogRepository | findByUserAndLogDate*; findByUserAndLogDateBetween*; save | findByGymIdAndUserIdAndLogDateOrderByLogTimeDesc(G,U,date); findByGymIdAndUserIdAndLogDateBetweenOrderByLogDateDescLogTimeDesc(G,U,start,end) | T2-T4 |
| ProgressMetricRepository | findByUserOrderByLogDateDesc; findFirstByUserOrderByLogDateDesc; save | findByGymIdAndUserIdOrderByLogDateDesc(G,U); findFirstByGymIdAndUserIdOrderByLogDateDesc(G,U); server-owned writes | T2-T4 |
| NotificationRepository | findByUserOrderByCreatedAtDesc | findByGymIdAndUserIdOrderByCreatedAtDesc(G,U); future writers must set G | T2,T3 |

All described scoped operations are implemented; unused inherited/global operations are removed. `UserIdentityRepository` is authentication/uniqueness only; `GymRepository` supports current-gym validation and management locking. The deferred invitation repository has no production endpoint in this phase. Composite collection joins remain unchanged from foundation.

## Remaining gates and readiness

No known unconverted active tenant business endpoint remains after the matrix checks and review; final findings and counts are recorded below. Authentication invalidates legacy sessions, so users must log in again. Previously unaffiliated accounts cannot use a default gym or receive a tenant session. The frontend still lacks OWNER onboarding, invitation handling, and full session-restoration UI behavior; those are Phase 4 work. Existing registration and asset routes are intentionally closed rather than bypassing security.

This is ready as a backend dependency for Phase 4 following the green final verification and review fixes. It is not production-release approval. Keep the startup prohibition until onboarding, the full operational V3 migration/recovery sequence, explicit ownership/catalog/admin decisions, actual schema/backup certification and a separately authorized cutover are complete. No persistent database or external hosting state has been verified by this work.

## Final verification and review

`./scripts/verify-tenancy.sh` completed after the review fixes with **850 tests, zero failures/errors/skips; BUILD SUCCESS**. This includes **478 tenant HTTP/PostgreSQL cases**, **44 foundation migration cases**, **22 existing PostgreSQL concurrency cases**, **175 existing/extended security regressions**, **22 tenant-session cases** and **3 repository/activation architecture checks**. Existing membership (29), booking (57), midnight (1), trainer (2), startup (1), seed (1) and safety (15) tests also passed. PostgreSQL was 18.6; Java 24 compiles with release 17.

Frontend: **20 Node tests**, lint and production build passed. Browser: **8 + 10 + 3 scenarios** passed with no uncaught errors/external requests. The final backend run includes formatting, audit fields and both review fixes. It was invoked with deliberately conflicting inherited Hikari/Flyway/JVM settings; the isolated runner scrubbed them. No production code changed after that run.

The fresh read-only whole-change review found no Critical issue or demonstrated cross-gym bypass. Two Important input-validation findings were reproduced and fixed: missing signed JWT subjects now receive 401 with no state/context residue; structured/vendor JSON receives the same root/nested forbidden-field checks and 400 response as ordinary JSON. The focused red run had two failures and one NPE; all **22 session tests** passed after the fix, including explicit null/incorrectly typed subjects and unchanged-state checks. No second reviewer or database access was used for review.

Deferred minor: the token factory emits `iat`, but an otherwise validly signed schema-2 token without `iat` is still accepted. Expiry and current gym, authorization and credential claims remain mandatory. Additional issued-at shape/time validation is deferred hardening, not a known cross-gym access path. This limitation is explicit rather than a claim of complete schema validation.

Post-review full verification passed **850/850** with zero skipped tests. Both Important findings are closed by the failing-then-passing regressions and the green complete suite. No remaining Important/Critical finding is known within this phase’s scope. Evidence is outside Git at `/Users/roshan/.codex/workspaces/fitcore-multi-tenant-saas/evidence/phase3-final-backend.log`, `phase3-browser-results.json`, and the other `phase3-*` logs. No test artifacts, tokens or synthetic database contents are part of the source changes.

## Decisions and evidence limits

- Registration remains closed until the Phase 4 invitation workflow. This avoids an unaffiliated account or inferred gym; the cost is that self-registration is unavailable until that work is complete.
- Security changes use the additive V5 row trigger plus locked service increments. V3 remains deferred; the future operational sequence must put the reviewed mapping before V4/V5. Applying this incomplete bundle to a persistent database remains prohibited.
- Most endpoint matrix cases run in rollback-managed test transactions. Committed concurrent requests, explicit migration commits and actual browser writes provide separate commit evidence; the matrix is not represented as committing every individual row.
- Revocation follows the approved subsequent-request guarantee. A transaction already authorized before a role/status change may finish; this phase does not promise retroactive cancellation.
- Onboarding, invitations, operational ownership mappings, asset ownership and full OWNER frontend remain later work. The cost of those accepted boundaries is that this build remains blocked from customer deployment.

## Files changed

- Modified: `gym-backend/scripts/verify-phase2-ui.sh`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/config/FoundationSafetyConfiguration.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/controller/AuthController.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/dto/admin/UserUpdateRequest.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/dto/auth/AuthResponse.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/exception/GlobalExceptionHandler.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/repository/ActiveMembershipRepository.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/repository/AttendanceRepository.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/repository/ExerciseRepository.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/repository/FoodLogRepository.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/repository/MembershipPlanRepository.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/repository/NotificationRepository.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/repository/NutritionGoalRepository.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/repository/NutritionPlanRepository.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/repository/ProgressMetricRepository.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/repository/TrainerProfileRepository.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/repository/TrainerSessionRepository.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/repository/UserRepository.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/repository/WorkoutExerciseLogRepository.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/repository/WorkoutLogRepository.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/repository/WorkoutPlanRepository.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/security/CustomUserDetailsService.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/security/JwtAuthenticationFilter.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/security/JwtService.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/security/RecordAccess.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/security/SecurityConfig.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/security/SecurityRequestLoggingFilter.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/service/AdminService.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/service/AuthService.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/service/DashboardService.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/service/MembershipService.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/service/NotificationService.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/service/NutritionService.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/service/TrackingService.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/service/TrainerService.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/service/UserService.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/service/WorkoutService.java`
- Modified: `gym-backend/src/test/java/com/fitcore/gym/concurrency/PostgresConcurrencyTest.java`
- Modified: `gym-backend/src/test/java/com/fitcore/gym/security/ProductionSeedIsolationTest.java`
- Modified: `gym-backend/src/test/java/com/fitcore/gym/security/SecurityRegressionTest.java`
- Modified: `gym-backend/src/test/java/com/fitcore/gym/service/BookingRegressionTest.java`
- Modified: `gym-backend/src/test/java/com/fitcore/gym/service/MembershipMidnightTest.java`
- Modified: `gym-backend/src/test/java/com/fitcore/gym/service/MembershipRegressionTest.java`
- Modified: `gym-backend/src/test/java/com/fitcore/gym/service/TrainerServiceTest.java`
- Modified: `gym-frontend/scripts/api-contract.test.mjs`
- Modified: `gym-frontend/src/services/api.js`
- Created: `gym-backend/src/main/java/com/fitcore/gym/dto/admin/AssignableGymRole.java`
- Created: `gym-backend/src/main/java/com/fitcore/gym/dto/user/SessionUserDto.java`
- Created: `gym-backend/src/main/java/com/fitcore/gym/repository/TenantRepository.java`
- Created: `gym-backend/src/main/java/com/fitcore/gym/repository/UserIdentityRepository.java`
- Created: `gym-backend/src/main/java/com/fitcore/gym/security/IdentityUniqueness.java`
- Created: `gym-backend/src/main/java/com/fitcore/gym/security/TenantAccess.java`
- Created: `gym-backend/src/main/java/com/fitcore/gym/security/TenantActor.java`
- Created: `gym-backend/src/main/java/com/fitcore/gym/security/TenantContext.java`
- Created: `gym-backend/src/main/java/com/fitcore/gym/security/TenantWriteGuard.java`
- Created: `gym-backend/src/main/resources/db/migration/V5__version_security_changes.sql`
- Created: `gym-backend/src/test/java/com/fitcore/gym/fixture/FixtureActiveMembershipRepository.java`
- Created: `gym-backend/src/test/java/com/fitcore/gym/fixture/FixtureAttendanceRepository.java`
- Created: `gym-backend/src/test/java/com/fitcore/gym/fixture/FixtureExerciseRepository.java`
- Created: `gym-backend/src/test/java/com/fitcore/gym/fixture/FixtureFoodLogRepository.java`
- Created: `gym-backend/src/test/java/com/fitcore/gym/fixture/FixtureMembershipPlanRepository.java`
- Created: `gym-backend/src/test/java/com/fitcore/gym/fixture/FixtureNotificationRepository.java`
- Created: `gym-backend/src/test/java/com/fitcore/gym/fixture/FixtureNutritionGoalRepository.java`
- Created: `gym-backend/src/test/java/com/fitcore/gym/fixture/FixtureNutritionPlanRepository.java`
- Created: `gym-backend/src/test/java/com/fitcore/gym/fixture/FixtureProgressMetricRepository.java`
- Created: `gym-backend/src/test/java/com/fitcore/gym/fixture/FixtureTrainerProfileRepository.java`
- Created: `gym-backend/src/test/java/com/fitcore/gym/fixture/FixtureTrainerSessionRepository.java`
- Created: `gym-backend/src/test/java/com/fitcore/gym/fixture/FixtureUserRepository.java`
- Created: `gym-backend/src/test/java/com/fitcore/gym/fixture/FixtureWorkoutExerciseLogRepository.java`
- Created: `gym-backend/src/test/java/com/fitcore/gym/fixture/FixtureWorkoutLogRepository.java`
- Created: `gym-backend/src/test/java/com/fitcore/gym/fixture/FixtureWorkoutPlanRepository.java`
- Created: `gym-backend/src/test/java/com/fitcore/gym/security/TenantSessionTest.java`
- Created: `gym-backend/src/test/java/com/fitcore/gym/tenancy/TenantIsolationTest.java`
- Created: `gym-backend/src/test/java/com/fitcore/gym/tenancy/TenantRepositoryArchitectureTest.java`
- Created: `gym-frontend/scripts/tenant-isolation-integrated.pw.js`
- Created: `docs/backend-tenant-isolation-2026-10-31.md`
