# FitCore multi-tenant SaaS implementation plan

> For agentic workers: REQUIRED SUB-SKILL: use superpowers:executing-plans for an approved inline implementation, or superpowers:subagent-driven-development only if that execution method is explicitly selected. Steps use checkbox syntax. **This document does not authorize implementation.**

**Goal:** Evolve the existing FitCore application into a shared-PostgreSQL SaaS where one account belongs to one gym, owners manage their gym, and all existing workflows remain isolated and usable.

**Architecture:** Explicit gym ownership on every business entity and collection, server-resolved tenant identity, scoped repositories and same-gym foreign keys. A designated primary owner is enforced at transaction commit. Versioned migrations use reviewed private ownership inputs and a controlled cutover; existing membership/booking algorithms and locks remain.

**Tech stack:** Existing Java 17 / Spring Boot 4.1.0 / Spring Security / Spring Data JPA / PostgreSQL and React 19 / Vite 8 / Axios / Playwright. Add Boot-compatible Flyway PostgreSQL migration support. Do not combine tenancy with framework upgrades.

**Spec:** [Phase 1 technical design](../specs/2026-10-30-multi-tenant-phase1-design.md).

**Source baseline:** 4ad96321f584dbfaaa2b3a9885bbf04f9cbe1d0f on main, including all Phase 1 security, Phase 2 membership/booking, and Phase 3 UI work.

## Global constraints

- One account belongs to one gym; emails remain globally unique.
- Each gym has at least one OWNER; ADMIN permissions are gym-scoped, not application-wide.
- Platform administration is separate from gym administration; no tenant platform role/override is introduced.
- Users, memberships, trainers, attendance, bookings, progress and relevant catalogs are tenant-isolated in shared PostgreSQL.
- Existing membership and booking business rules remain unchanged, including lock order, UTC semantics, upgrade/renewal rules, and retained history.
- Payments, SaaS subscription billing and multi-gym staff membership are deferred.
- Never trust a client-supplied gym ID as authorization; resolve tenant identity from the authenticated account.
- Never infer a legacy account's ownership; a complete approved private mapping is required before populated-data backfill.
- Preserve all current work, existing documents, private remediation files, credentials, IDs and historical values except explicitly approved ownership/role/catalog-FK changes.
- Current authorization covers documentation only: no source edits, migrations, production data access, push, merge or deployment. Every unchecked implementation action below is future work requiring coding approval; production operations require separate specific approval.

## Review focus

1. A local member path combined with a foreign trainer/plan/exercise ID must fail without even a partial parent insert; task 4/5 test mixed and nested inputs.
2. Two simultaneous owner signups or invitation acceptances must leave exactly one valid result and no ownerless gym/consumed-on-failure invitation; task 6 tests real PostgreSQL races.
3. A stale JWT after role, gym, email or enabled-state change must not retain or regain access; task 3 exercises both invalidation and re-enable behavior.
4. Shared catalog clones cannot be inserted until obsolete global uniqueness is replaced in the SAME mapping transaction; task 8 tests exact ordering and rollback of DDL plus data.
5. Existing conflicting history or an unresolved cross-gym historical booking must be preserved or block migration; task 8 tests refusal without automatic repair.

## Paths and interfaces

Use these absolute path roots when implementing:

| Symbol | Absolute path |
|---|---|
| R | /Users/roshan/Desktop/FitCore |
| B | /Users/roshan/Desktop/FitCore/gym-backend/src/main/java/com/fitcore/gym |
| T | /Users/roshan/Desktop/FitCore/gym-backend/src/test/java/com/fitcore/gym |
| RES | /Users/roshan/Desktop/FitCore/gym-backend/src/main/resources |
| F | /Users/roshan/Desktop/FitCore/gym-frontend/src |

If an isolated checkout is created after coding approval, replace only R's checkout prefix with its returned absolute workspace path; file suffixes remain exact. Do not move the current checkout or private remediation directory.

Core interface contracts to retain consistently across tasks:

| Interface | Contract |
|---|---|
| TenantActor | Immutable record (UUID userId, UUID gymId, Role role, long authzVersion), constructed exclusively from a validated database account |
| TenantContext.actor() | Returns authenticated TenantActor, or throws the documented authentication/assignment error; no fallback gym |
| TenantAccess.requireUser(UUID targetUserId) | Returns a same-gym User permitted by self-or-manager policy; foreign/missing -> 404, local forbidden -> 403 |
| TenantAccess.requireManagedUser(UUID targetUserId) | Returns local management target after caller/target-role checks; never permits an OWNER target |
| TenantAccess.requireManager() | Returns current actor only for local OWNER/ADMIN; caller-specific assignment restrictions remain |
| TenantWriteGuard.requireGym(UUID entityGymId) | Rejects mismatched ownership before mutation; no caller-supplied context |
| UserIdentityRepository | Global lookup by exact email for login/uniqueness and UUID for token authentication; auth/onboarding only; no arbitrary profile API |
| Scoped repository contracts | Section 6 of the design specifies all 15 existing repositories and parameter types/order. G is the trusted actor's UUID, U is target user UUID, I is business Long ID |
| OwnerOnboardingService.registerOwner(OwnerRegistrationRequest) | Returns AuthResponse only after transaction commit; request carries fullName/email/password/gymName/gymSlug, no role/gymId |
| GymInvitationService.issue(MemberInvitationRequest) | Authenticated manager; fixed MEMBER, 48-hour default lifetime; returns token/link once, stored digest only |
| GymInvitationService.revoke(UUID invitationId) | Scoped manager action; invalidates only a local pending invitation |
| GymInvitationService.accept(InviteRegistrationRequest) | Public high-entropy token lookup plus bound email; atomically creates local MEMBER and consumes token, returns AuthResponse after commit |

Existing auth/register fields remain but now require invitationToken; new owner registration is separate. Define Request DTOs in the exact paths below before their consumers. UUID and Long identity types must not be interchanged. Do not introduce executable SQL/source during this design-only task.

## Dependencies and release boundary

    Task 1 baseline/migration harness
      -> Task 2 gym entities and constraints
      -> Task 3 tenant identity/security
      -> Task 4 catalogs, memberships and bookings
      -> Task 5 personal/admin/report data paths
      -> Task 6 owner/member onboarding
      -> Task 7 frontend/session integration
      -> Task 8 full migration/recovery rehearsal and CI release gate

No intermediate task is a deployable multi-gym release. Public gym creation remains disabled until tasks 1-8 pass. The flag never disables tenant checks. No task authorizes a production operation or removal of the current main-branch Vercel deployment guards.

## Task 1: Preserve the integration baseline and establish migration verification

**Depends on:** design and coding approval; confirmed 4ad9632 baseline. Real legacy mapping is not required to write synthetic tests.

**Files:** modify R/gym-backend/pom.xml and test configuration; create RES/db/migration/V1__baseline_existing_fitcore.sql, T/migration/MigrationBaselineTest.java, R/gym-backend/scripts/verify-tenancy.sh, and R/gym-backend/src/test/resources/tenant-migration-fixtures/README.md. Later operational schema certification is separate from this source-derived rehearsal fixture.

**Produces:** repeatable empty/legacy-shaped disposable PostgreSQL harness and verified initial schema definition; no production baseline registration.

- [ ] Recheck branch heads and working-tree inventory. Create/reuse an isolated development checkout based on 4ad9632 after coding approval. Fingerprint/preserve original tracked/untracked work and private remediation files without copying private contents. Do not merge/cherry-pick old phases.
- [ ] Run the existing isolated baseline checks in the future checkout: backend scripts/verify-postgres.sh, frontend lint/build, Node tests and existing integrated harnesses. Record actual failures/results; do not inherit a pass claim from the October reports.
- [ ] Write failing MigrationBaselineTest cases: emptyPostgresCreatesBaselineSchema, populatedSchemaRequiresExplicitBaseline, unknownSchemaDriftIsRejected, runtimeProfileDoesNotMigrate. Assert all 15 entity and six collection tables/identity definitions are accounted for, without seed accounts.
- [ ] Run the targeted tests in an owned, loopback-only disposable PostgreSQL container and confirm expected failure before adding the migration path.
- [ ] Add compatible Flyway support under the existing Boot dependency management after checking resolved module compatibility. Set baselineOnMigrate=false and cleanDisabled=true. Keep migration execution disabled in the runtime profile; the test/migration runner opts in explicitly with its own credentials. Do not change application DDL behavior against a configured existing database.
- [ ] Implement V1 for new synthetic databases, based on the repository schema; require explicit baseline registration and schema comparison for populated fixtures. Document that production schema certification is still blocked without a separately authorized inventory.
- [ ] Implement verify-tenancy.sh by following the existing owned-container/loopback/environment-isolation conventions. Refuse non-test hosts/databases and occupied harness resources; trap cleanup; never read .env or use inherited application credentials.
- [ ] Run the targeted PostgreSQL tests and the existing backend suite; require exit 0 and no unreviewed skips. Retain sanitized evidence. Make a focused local review checkpoint only after coding is authorized; do not push.

**Risks:** a baseline generated solely from entities may miss deployed drift; Flyway startup can otherwise mutate a configured database; H2 cannot prove PostgreSQL behavior.

**Acceptance:** only disposable databases are reachable; migration auto-execution is off for the application; baseline tests pass; actual legacy schema remains an explicit operational gate.

## Task 2: Introduce gym ownership, role boundaries and relational integrity

**Depends on:** task 1.

**Files:** create B/entity/Gym.java, B/entity/GymInvitation.java, B/domain/GymStatus.java, B/entity/TenantOwnedEntity.java, B/repository/GymRepository.java, B/repository/GymInvitationRepository.java, RES/db/migration/V2__expand_gym_ownership.sql, RES/db/migration/V4__enforce_gym_integrity.sql, T/tenancy/TenantConstraintTest.java and T/tenancy/TenantFixtures.java. Modify B/domain/Role.java and every existing B/entity/*.java mapping; update existing test fixture constructors to supply synthetic gyms. The V3 populated assignment runner is task 8; tests here use deliberately staged synthetic ownership before testing V4.

**Consumes:** V1 schema and disposable harness. **Produces:** all ownership fields, owner invariant, scoped uniqueness/foreign keys and shared synthetic fixtures.

- [ ] Write failing tests for wrongGymParentIsRejected for every relationship/collection, catalogNameCanRepeatAcrossGyms, duplicateCatalogNameWithinGymFails, ownerlessGymCannotCommit, primaryOwnerMustBeLocalEnabledOwner, primaryOwnerCannotBeDisabledDemotedOrDeleted, and gymOwnershipCannotBeChangedByRuntimeWrites.
- [ ] Explicitly commit transactions in deferred-constraint tests; a rollback-only test must not falsely pass without firing the trigger. Include concurrent primary-owner invalidation attempts.
- [ ] Implement Gym/User fields and all 21 gym ownership mappings, preserving existing IDs, timestamps, collection semantics, global email uniqueness and existing role values; add OWNER. Use one writable gymId per row and ensure ORM associations/collection joins cannot write contradictory ownership columns.
- [ ] Implement V2 as expansion only: nullable ownership, version default, gyms/invitations, support keys/indexes, old catalog uniqueness retained, no inferred rows and no early enforcing composite FK against not-yet-remapped data.
- [ ] Implement/test final V4 owner constraint/FKs, immutable ownership and required gym IDs using a prepared synthetic intermediate fixture. For fresh onboarding, permit transient null primary owner within one transaction and enforce its presence/role/enabled state at commit. Do not use a cross-row CHECK.
- [ ] Test the complete gym -> OWNER -> primary-owner update transaction succeeds, while failures leave no rows. Take gym locks before privileged user mutations; do not change member-then-trainer lock order for bookings.
- [ ] Add the design's nonredundant indexes. Preserve original individual FK/PK safety while adding composite references. No new unique ACTIVE-membership, overlap, open-attendance or destructive cascade rule.
- [ ] Run TenantConstraintTest on PostgreSQL plus existing suites with updated fixtures. Review generated SQL/joins and schema validation; do not treat H2 as evidence for triggers.

**Risks:** cyclic owner insertion, deferred checks not exercised by tests, conflicting JPA gym columns, enum checks rejecting OWNER, constraints changing historical rules.

**Acceptance:** every final table is covered by the design ownership map; committed ownerless gyms/foreign references fail; legacy algorithms are unchanged; no automatic production schema change occurs.

## Task 3: Replace global identity assumptions with validated tenant sessions

**Depends on:** task 2.

**Files:** create B/security/TenantActor.java, TenantContext.java, TenantAccess.java, TenantWriteGuard.java; B/repository/UserIdentityRepository.java; B/dto/user/SessionUserDto.java; T/security/TenantSessionTest.java and TenantPrivilegeTest.java. Modify B/security/{SecurityConfig,JwtService,JwtAuthenticationFilter,CustomUserDetailsService,RecordAccess}.java, B/service/AuthService.java, B/controller/AuthController.java, B/dto/auth/AuthResponse.java and B/dto/admin/UserUpdateRequest.java. Update T/security/SecurityRegressionTest.java and existing fixtures.

**Consumes:** gym/user/version fields. **Produces:** trusted TenantActor and helper interfaces above; v2 JWTs; protected GET /api/auth/me.

- [ ] Write failing tests for rejectsLegacyOrMissingTenantClaims, databaseGymMismatchInvalidatesToken, roleChangeInvalidatesOldToken, disableAndReenableDoesNotRestoreOldToken, passwordRotationStillInvalidatesToken, ownerPublicDemoPasswordIsRejected, unassignedCredentialsIssueNoTenantToken, suspendedGymHasNoBusinessAccess, and forgedStoredBrowserRoleGrantsNoPrivilege.
- [ ] Define the exact v2 claims and UUID subject in the design. Preserve keyed credentialsVersion, signing secret and lifetime. Authenticate globally by email only for login; load UUID identity only inside authentication. Increment authz_version atomically on role/gym/email/enabled changes.
- [ ] Replace wildcard public /api/auth/** authorization and JWT-filter skipping with explicit public endpoints/methods. GET /api/auth/me MUST pass through authentication and require a valid tenant session; merely adding a controller under the old wildcard is unsafe/nonfunctional.
- [ ] Permit OWNER/ADMIN on management paths, then enforce same-gym and target-role policy in service helpers. Retire RecordAccess's global ADMIN bypass. Satisfy documented 401/403/404 outcomes without disclosing foreign existence.
- [ ] Implement assignable role restrictions: MEMBER/TRAINER/ADMIN input enum; OWNER caller required for ADMIN assignment; no ordinary target OWNER, tenant move, platform permission or version/hash mass assignment. Keep all protected mutations transactional with state/version changes.
- [ ] Add global-identity repository access allowlist checks. Avoid tenant context in a mutable global/thread variable; test sequential A/B request reuse and concurrent requests for context isolation.
- [ ] Run tenant session/privilege and existing security tests. Require signed JWT HTTP tests, real current account reloading and unchanged DB state on denial; do not replace authorization with mocks.

**Risks:** /auth/me accidentally public/skipped; token migration silently accepting old claims; OWNER promotion bypassing demo blocking; ordinary role endpoints becoming privilege grants; stale frontend identities.

**Acceptance:** no tenant-capable session without one valid gym; current disabled/credential protections hold; old tokens fail; OWNER is never a platform administrator.

## Task 4: Isolate catalogs, memberships and trainer bookings

**Depends on:** task 3.

**Files:** create B/repository/TenantRepository.java and T/tenancy/TenantCatalogAndMembershipTest.java, TenantBookingTest.java. Modify B/repository/{User,ActiveMembership,MembershipPlan,TrainerProfile,TrainerSession,Exercise,WorkoutPlan,NutritionPlan}Repository.java and B/service/{MembershipService,TrainerService}.java. Update T/service/{MembershipRegressionTest,MembershipMidnightTest,BookingRegressionTest,TrainerServiceTest}.java and T/concurrency/PostgresConcurrencyTest.java.

**Consumes:** TenantActor/access helpers, scoped repository contracts from design section 6. **Produces:** all catalog and membership/booking paths scoped without business-rule changes.

- [ ] Write failing HTTP tests for design endpoint rows 8-18 and 22: member/admin/owner in A cannot enumerate B catalogs or read/renew/upgrade B memberships; local member + foreign plan/trainer/membership body ID is rejected; ADMIN cannot bypass foreign gym scope.
- [ ] Implement narrow scoped repository interfaces and lookups/locks exactly as the design matrix. Add G outside the full OR condition in the overlap query; preserve time comparisons and current status/date calculations.
- [ ] Preserve stable user-row membership locking and member-then-trainer-profile booking locking. Validate gym, owner and referenced ID before mutations; never fall back to findById.
- [ ] Scope nested plan features, trainer users/certifications/availability and workout/nutrition catalog collections. Keep empty trainer availability unrestricted and do not add a new membership entitlement check to booking.
- [ ] Run all existing membership/booking regression cases with local gym fixtures, including duplicate current history, expectedEndDate conflicts, exclusive expiry, month ends, midnight, higher package prices and preserved cancelled upgrade rows.
- [ ] Extend PostgreSQL concurrency tests: same local resources still produce intended conflicts; independent A/B bookings and memberships succeed; foreign attempts cannot lock/update B through unscoped targets. Assert stored results after real transaction commit.

**Risks:** tenant predicates changing overlap logic; accidental new eligibility rules; parent lock order drift; legacy histories being cleaned or hidden; global catalog responses surviving one module conversion.

**Acceptance:** relevant matrix rows pass both cross-gym directions and original rules, with independent gym operations not interfering.

## Task 5: Isolate personal data, administrative management, analytics and exports

**Depends on:** task 4.

**Files:** create T/tenancy/TenantPersonalAccessTest.java, TenantAdminAndAggregateTest.java, TenantRepositoryArchitectureTest.java. Modify every remaining B/repository interface and B/service/{UserService,DashboardService,NotificationService,NutritionService,TrackingService,WorkoutService,AdminService}.java. Add or update B/dto/admin/AssignableGymRole.java and UserUpdateRequest.java. Update security/controller checks where required, and make the existing S3 activation path fail closed until owned asset support exists.

**Consumes:** task 4 repositories and role policy. **Produces:** all 44 existing endpoint rows accounted for; no unscoped business-data path.

- [ ] Build an endpoint-enum test matrix matching design rows 1-44, with applicable T codes and current conditional image routes. Missing route coverage must fail the coverage check when controllers change.
- [ ] Write failing tests for all remaining personal endpoints, local-user/foreign-exercise mixed workout batches, foreign attendance checkout, nested DTO leaks, and administrative lists/counts/charts. Include B sentinels in every table and verify absence from the ENTIRE A response, not only visible UI columns.
- [ ] Convert remaining repositories to narrow scoped operations. Move global email checks to identity-only validation returning generic conflicts, without foreign profile/gym data.
- [ ] Implement atomic workout parent/child creation and trainer user/profile mutations. Validate all foreign references before any parent write, then rely on transactional rollback and composite FKs as additional protection.
- [ ] Scope every AdminService input: all users/trainers/plans/attendance, role counts, current membership grouping, membership growth, revenue history and attendance date ranges. Preserve formulas and history semantics; apply OWNER/ADMIN target restrictions before update/disable.
- [ ] Scope every DashboardService contributing query, including workout child entries, food, progress, notifications and session/trainer nested DTOs. Protect same-gym personal records using the approved self-or-manager policy.
- [ ] Keep S3 disabled and test accidental activation is refused instead of exposing global URL deletion. Document the conditional extra work if an intended target relies on S3.
- [ ] Add architecture checks forbidding unscoped business reads, inherited bulk operations and global identity repository access outside the allowlist. Independently exercise direct scoped service calls so HTTP checks are not the only guard. Restrict runtime credentials from DDL and migration inputs in PostgreSQL tests.
- [ ] Run all backend tests through verify-tenancy.sh, review endpoint/repository matrices against source again, and require no unmapped production data path. Existing startup/seed isolation remains covered; no hidden default gym/account is seeded.

**Risks:** a single aggregate input leaks totals; filters applied after fetching/mapping; global email conflicts leaking identity; partial writes; role edits revoking the only usable owner.

**Acceptance:** all existing endpoints have verified applicable cases; denied writes leave both gyms' state unchanged; admin report JSON is already safe for browser export.

## Task 6: Add transactional owner onboarding and invitation-based member creation

**Depends on:** task 5.

**Files:** create B/service/OwnerOnboardingService.java, GymService.java, GymInvitationService.java; B/controller/GymController.java; B/dto/gym/{OwnerRegistrationRequest,GymDto,GymUpdateRequest,MemberInvitationRequest,InvitationResponse,InviteRegistrationRequest}.java; T/tenancy/OwnerOnboardingTest.java and GymInvitationTest.java; RES/catalogs/starter-v1.json only if a reviewed template set is approved. Modify AuthController/AuthService and repositories from prior tasks to integrate acceptance and auth/me.

**Consumes:** final relational invariants, trusted role policy and token factory. **Produces:** the six new endpoint contracts in design section 5.1 and controlled existing register behavior.

- [ ] Write failing tests for atomicGymAndOwnerCreation, existingAccountCannotJoinSecondGym, globalEmailRaceLeavesOneGym, slugRaceLeavesNoOrphan, failedOwnerConstraintIssuesNoToken, clientRoleGymOwnerFieldsCannotOverride, invitationCannotChangeEmailGymOrRole, invitationReplayOrExpiryFails, acceptanceFailureDoesNotConsumeToken, and parallelAcceptanceCreatesOneMember.
- [ ] Implement registerOwner using one transaction for gym, fixed-role OWNER, primary designation and optional vetted templates. Sign/return JWT only after successful commit. Public registration is not a generic role-assignment endpoint.
- [ ] Implement GET/PATCH current gym with the design's narrow metadata fields. No arbitrary ID directory, status/owner/gym move or platform endpoint.
- [ ] Implement fixed-MEMBER invitations with high-entropy random secret, stored digest, 48-hour clock-based expiry, scoped creator FK, lock/consume/revoke transaction, and generic invalid-token responses. Do not send messages/email automatically.
- [ ] Require invitationToken in member registration, and derive gym/role/email binding from the stored token. Existing global email cannot be attached or transferred to a new gym through acceptance; a collision rolls back user/consumption changes.
- [ ] Test owner/admin management privileges and target restrictions end to end with tokens issued by actual registration. Demonstrate that forged UI/request data never changes trusted tenancy.
- [ ] Run PostgreSQL onboarding/invitation race tests, full security/regression suites and seed-isolation tests. Leave public owner creation feature-gated off pending full readiness.

**Risks:** partial/orphan onboarding; secret leakage; privilege injection; consuming an invitation before a failed account insert; weak owner recovery.

**Acceptance:** one account/one gym and committed owner invariant hold under failure and concurrency; no private/production fixture or credential is used.

## Task 7: Integrate owner and gym context without regressing the existing UI

**Depends on:** task 6.

**Files:** modify F/context/AuthContext.jsx, F/services/api.js, F/routes/AdminRoute.jsx, F/routes/ProtectedRoute.jsx, F/layouts/MainLayout.jsx, F/App.jsx, F/pages/{Register,Login,Admin,Workouts}.jsx; create F/pages/CreateGym.jsx and F/pages/GymSettings.jsx. Add R/gym-frontend/scripts/tenant-api-contract.test.mjs and tenant-integrated.pw.js; adapt the owned integrated harness under R/gym-backend/scripts rather than using production endpoints.

**Consumes:** v2 auth/me and gym/invitation contracts. **Produces:** usable owner onboarding, invitations and local management with preserved Phase 3 presentation.

- [ ] Write failing API contract/browser tests for owner creation, member invitation acceptance, gym heading, OWNER management navigation, ADMIN limitations, authenticated profile refresh, and stale-session logout.
- [ ] Load current user/gym through protected auth/me after restoring browser storage; do not authorize from the stored role/gym. Preserve remember-me local/session storage and existing 401 cleanup; show assignment/suspension errors distinctly without a fallback gym.
- [ ] Add owner creation and invitation-aware registration flows, current-gym metadata and invitation generation/revocation. Show plaintext invitation link only where returned to its authorized issuer; do not log or retain it unnecessarily.
- [ ] Allow OWNER in management routes; restrict role dropdown/actions to the approved assignable roles and target policy. Server checks remain authoritative. Do not replace or redesign the current Phase 3 theme/accessibility components.
- [ ] Key local workout-plan state by gymId and userId. Migrate an old user-only selection only after matching its ID against the authenticated gym's returned plans; otherwise clear it. No cross-gym cached lists survive session changes.
- [ ] Test actual attendance CSV from scoped dashboard input: B identifiers/rows absent; A values preserved. Exercise all existing module actions and legacy URLs after fresh login.
- [ ] Run frontend Node contracts, lint/build and isolated integrated Playwright with two gyms, every role, remembered/reloaded sessions, dark/light and representative mobile/desktop layouts. Include membership/booking conflict feedback and existing focus/navigation checks.

**Risks:** restored browser identity masking stale rights, cached plan IDs, misaligned API deployment, owner UI granting unsupported roles, loss of Phase 3 accessibility behavior.

**Acceptance:** complete owner-to-member workflow works through browser/API/PostgreSQL; cross-gym requests fail independently of UI; existing workflows and styles retain regression evidence.

## Task 8: Complete versioned backfill, recovery rehearsal, CI and release checklist

**Depends on:** tasks 1-7; synthetic manifests suffice for code/rehearsal. Real ownership approval, production schema, recovery and release approvals remain separate operational gates.

**Files:** create R/gym-backend/src/main/java/db/migration/V3__apply_approved_tenant_mapping.java, B/migration/TenantMappingManifest.java, B/migration/TenantMappingValidator.java, T/migration/TenantMigrationTest.java, T/migration/TenantRecoveryTest.java, R/gym-backend/scripts/rehearse-tenant-migration.sh, R/.github/workflows/verify.yml and R/docs/multi-tenant-release-checklist.md. Update the migration runner/configuration, app readiness checks and existing PostgreSQL harness. Synthetic manifests live under test resources; no real mapping is committed.

**Consumes:** final schema/runtime, source-derived V1, verified contracts. **Produces:** complete V1->V4 rehearsal and evidence-backed release gate; still no production execution.

- [ ] Write failing migration cases: completeSyntheticMappingPreservesHistory, incompleteOrDuplicateMappingAborts, mappingDriftAborts, disabledAdminIsNotEnabledOrAutoPromoted, historicalOverlapsAreUnchanged, splitHistoricalBookingBlocksMigration, sharedCatalogCopiesPreserveValuesAndApprovedReferences, emptyDatabaseNeedsNoLegacyGym, and failedMappingRollsBackDataAndConstraintTransition.
- [ ] Implement a versioned V3 runner that verifies the private manifest target/revision/checksum/approvals and exact snapshot/count expectations. Create no fallback gym. For populated data, missing approved inputs always fail. A manifest approval reference is evidence to check, not authority conferred by the software.
- [ ] Establish original catalog ownership, then scoped uniqueness, then remove obsolete global uniqueness BEFORE identical clones, all in the M3 transaction. Clone approved collections; remap only approved plan/exercise references; preserve original business IDs/date/status/value fields. Do not install enforcing same-gym FKs until remap is complete. Finish with full same-gym/null/owner assertions.
- [ ] Test failed shared-catalog mapping restores both prior data and original global uniqueness; test successful multiple-legacy-gym mapping reaches V4 without renaming values. Include sequence advancement after explicit inserts/clones so later writes cannot reuse IDs.
- [ ] Implement bounded locks/timeouts and deterministic mapping order, private receipts, runtime denial of migration inputs, checksum validation, no Flyway clean/automatic baseline, and refusal of unknown schema/triggers. Measure index/lock durations on disposable PostgreSQL; nontransactional concurrent-index steps require a separate explicit plan if measurements justify them.
- [ ] Rehearse failures after V1/V2/V3/V4 and inspect exact applied version history. Do not assume a failed later migration reverses earlier commits; document forward recovery with traffic still stopped.
- [ ] Restore a synthetic backup to a new disposable PostgreSQL target; verify fields/counts/relationships and representative authorized reads. Test tenant-aware schema-compatible fallback. Demonstrate the release checklist forbids restoring an old unscoped app over a multi-gym database.
- [ ] Set runtime ddl-auto=validate, SQL init never, demo off and migration auto-execution off in the release candidate; migration runner alone has DDL credentials. Refuse startup/readiness when schema version/configuration is incompatible. Keep S3 and owner creation gated as specified.
- [ ] Add CI for frontend contracts/lint/build, full backend regression, Flyway/PostgreSQL migration/constraint/concurrency suites, and critical browser flows. No production secrets/targets in CI. Make required checks visible; Docker's skipTests package step is not a test gate.
- [ ] Execute all candidate checks on the exact candidate commit after coding approval, compare source endpoints/repositories against the matrices, and capture sanitized evidence. Recheck preservation fingerprints and deployment guards.
- [ ] Deliver the concrete future release checklist with unresolved ownership/overlap/admin/backup decisions, migration checksums, maintenance freeze, exact compatible frontend/backend artifacts, reconciliation/rollback criteria and explicit production approval gates. Do not push, merge, deploy, run production SQL or change hosting/default branches as part of checklist completion.

**Risks:** wrong ownership, original-ID catalog policy not approved, mutation of disputed history, uniqueness ordering, unknown deployed schema, failed backup, multiple active old writers, incompatible rollback.

**Acceptance:** all synthetic migrations/recoveries and isolation tests pass; every original field difference is expected/approved; no old application can be released against multiple gyms; all remaining real-data gates are clearly unresolved until separately evidenced.

## Future verification commands and expected evidence

These commands are specifications for the approved implementation stage; none were run during the design task. Run from the indicated directory in its isolated checkout.

| Directory | Command | Passing evidence |
|---|---|---|
| R/gym-backend | ./scripts/verify-tenancy.sh | Owned disposable PostgreSQL; complete migration/constraint/isolation/concurrency and existing regressions; exit 0; no accidental PostgreSQL skips |
| R/gym-backend | ./scripts/rehearse-tenant-migration.sh | Synthetic baseline/mapping/recovery cases and preserved field/count checks; exit 0; cleanup verified |
| R/gym-frontend | node --test scripts/api-contract.test.mjs scripts/theme.test.mjs scripts/contrast.test.mjs scripts/tenant-api-contract.test.mjs | All reported cases pass, including tenant-aware session/registration contracts |
| R/gym-frontend | npm run lint and npm run build (separate commands) | Both exit 0; frontend built against intended local API origin |
| R/gym-backend | Existing verify-phase3-ui.sh plus tenant stage | Actual local browser/API/PostgreSQL workflows and two-gym/role/CSV checks; exit 0; no production/external traffic |

Fast targeted backend runs may use the disposable runner's test selector for TenantSessionTest, TenantConstraintTest, TenantCatalogAndMembershipTest, TenantBookingTest, TenantPersonalAccessTest, TenantAdminAndAggregateTest, OwnerOnboardingTest, GymInvitationTest, TenantMigrationTest and TenantRecoveryTest. A final claim of readiness requires the complete suite; do not infer it from a targeted test or H2 run.

## Approval and unresolved operational decisions

- [ ] Approve the design's primary-owner invariant, role/target policy, same-gym administrator personal access and invitation-only member onboarding.
- [ ] Approve the development baseline and coding scope. No source work starts before this approval.
- [ ] Before real-data mapping: verify actual schema and ownership for every legacy account, select owners, approve catalogs/FK remaps, resolve historical cross-gym bookings and determine membership-conflict readiness.
- [ ] Before any production cutover: provide fresh private administrator/owner recovery and backup/restore evidence, verify intended S3 usage, applied hosting settings, write freeze, exact migration/runtime artifacts and recovery plan.
- [ ] Obtain separate specific authorization for production data access, mapping/SQL execution, infrastructure changes, push/merge, and deployment. Approval of this design/checklist does not grant those actions.

The plan is ready for architecture review. Implementation has not started; no checkbox claiming code/test completion is preselected.
