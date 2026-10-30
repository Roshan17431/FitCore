# FitCore multi-tenant SaaS: Phase 1 architecture and migration design

Status: **Proposed for approval; no application implementation or database execution is authorized.**

Prepared 30 October 2026, Asia/Kolkata. Source baseline: **4ad96321f584dbfaaa2b3a9885bbf04f9cbe1d0f**.

This document and the companion implementation checklist are new documentation only. Application sources, existing documents, branch refs, private remediation decisions, production data, and hosting configuration are not changed by this design task. No test/build, database, migration, push, merge, or deployment command was run. Public Git remote heads were inspected without fetching or updating refs. Public PostgreSQL, Spring Boot, and Flyway documentation was consulted.

## 1. Evidence and actual integration baseline

### 1.1 Verified Git relationships

| Ref | Verified commit | Relationship and conclusion |
|---|---|---|
| Local main and remote main | 4ad96321f584dbfaaa2b3a9885bbf04f9cbe1d0f | Current checkout and selected source baseline; remote head confirmed with git ls-remote |
| Local codex/phase3-premium-ui | Same 4ad9632 commit | Exactly identical to main: zero commits on either side |
| Local codex/phase2-membership-bookings | 806792fadd67fbe2220ecac5436d03c2e6f5c0c1 | Ancestor of main; main contains all 17 Phase 2 commits plus 12 Phase 3 commits |
| Local master and remote master | 8ef78e693d9da2e470193d3ec7acd5ac6a195d09 | Phase 1 security baseline; ancestor of main; main is 29 commits ahead, master has no exclusive commits |
| Local and remote codex/fix-functional-flows | 7da226a977330b977a9cec9acc3c702770207ec3 | Older side branch. Application source trees, backend pom, and frontend package match publication ancestor 755ea4b; its functionality was published through split commits before security hardening |

The queried remote Phase 2 and Phase 3 branch names do not exist; their work is published on remote main. No separate Phase 1 development ref appears in the local branch inventory; the verified integration point is master at 8ef78e6. The local origin/HEAD still points at origin/master; the server's current default-branch setting was not independently queried.

The linear integration chain is:

    Phase 1 security: 8ef78e6
        -> 17 Phase 2 commits -> 806792f
        -> 12 Phase 3 commits -> 4ad9632 (main)

Phase 3 changes 53 files, including UI, theme, accessibility, media fallbacks, documentation, and verification harnesses. It introduces no difference under backend src, backend pom, Render manifest, or the two Vercel configurations relative to Phase 2. There are **no missing Phase 2 or Phase 3 commits to cherry-pick into main**.

Use a future codex/multi-tenant-saas development branch based exactly on 4ad9632. Do not start from master or merge the older functional side branch. Creating that branch/worktree is a later implementation action, not performed here.

### 1.2 Existing working-tree work and preservation

At inspection start, the sole tracked modification was .DS_Store. There were no staged changes and no tracked application-source changes. Existing untracked work consists of eight .superdesign files, BACKEND_TECH_STACK_ANALYSIS.md, README_INTERVIEW.md, docs/production-readiness-2026-10-20.md, and docs/verification-2026-10-18 2.md. These are preserved; they are not missing application commits.

Content/mode fingerprints were captured for 235 tracked files, 12 existing untracked files, and 18 private remediation files. Private file contents, customer identities, decisions, and credentials are not copied into these documents. New documentation is excluded when comparing the original untracked-file fingerprint at handoff.

The private remediation directory remains outside Git. Never import it, its output, or its decision manifest into fixtures, commits, CI, screenshots, or logs. Future development must preserve this checkout and use synthetic data for tests.

Final design-task verification: the endpoint matrix matches all 44 current controller routes, all 15 repositories have matrix entries, and local document links resolve. The independent builder check's catalog-uniqueness ordering finding was corrected and re-reviewed with no further important questions. The 18 private remediation files' content/mode fingerprint matches the initial capture. Git's working-tree report still shows only the pre-existing tracked .DS_Store modification and existing untracked work, plus these two new documents; application sources were not edited. A complete final content re-hash of tracked/untracked work could not finish because the filesystem now marks several existing files compressed/dataless and returns errno 60 on reads (including all 12 pre-existing untracked files); even one committed-object read later timed out. No hydration/reset/restore/deletion was attempted. Initial fingerprints were captured successfully; final byte-level verification of those cloud-offloaded files is explicitly unconfirmed, not reported as a pass.

### 1.3 Scope of verified versus historical evidence

Verified now: Git relationships; current source and configuration; 44 controller endpoints; 15 repositories; 15 entity tables and six collection tables; existing SQL templates and test definitions.

Historical, not reverified now: the October 22 report's 281 backend tests, 19 frontend Node tests, integrated/visual checks; the October 20 report's overlapping membership cases, private-admin/recovery gates, saved SQL mode, and Render branch. See [Phase 3 verification](../../phase3-ui-review-2026-10-22.md) and [data-remediation report](../../production-data-remediation-2026-10-20.md). No current production population, schema, ownership, backup, active deployment, or remediation completion is inferred from those reports.

## 2. Accepted requirements and design choices

The user's requirements are fixed: one account belongs to one gym; globally unique email; at least one OWNER per gym; gym-scoped ADMIN; separate platform administration; shared PostgreSQL; isolation for users, memberships, trainers, attendance, bookings, progress, and relevant catalogs; unchanged membership/booking rules. Payments, SaaS billing, and multi-gym staff membership are deferred.

Proposed choices requiring approval of this document:

1. Explicit gym_id ownership and scoped repository operations, backed by same-gym foreign keys. No reliance on browser filtering, a client header, or an automatically applied ORM filter.
2. A designated primary owner anchors the at-least-one-owner invariant. All OWNER users belong to that gym. Owner appointment/transfer is not available through ordinary user editing; self-service ownership transfer is deferred.
3. OWNER and ADMIN retain existing administrative personal-record capabilities **within their gym**. TRAINER retains self-only personal access; booking a trainer does not grant the trainer access to a member's nutrition, measurements, or dashboard. An OWNER may manage ADMIN accounts; an ADMIN may manage MEMBER/TRAINER accounts only. This is an intentional restriction of current global administration.
4. Catalogs are private to each gym. New gyms start with no commercial membership plans and may receive reviewed, non-customer exercise/workout/nutrition templates. No live tenant's catalog is a global template.
5. Normal MVP application users always have a gym. Transitional unassigned legacy accounts cannot receive a tenant-capable session. There is no default or fallback gym.
6. Existing email values and exact unique-email semantics remain unchanged in this tenancy project. Case-insensitive email normalization is a separate decision/migration after collision auditing; never silently merge or rewrite existing identities.
7. Owner registration creates a fresh account and gym atomically. An existing gym account cannot create/join a second gym. Member registration requires an owner/admin-issued invitation. Email delivery is not required for the MVP; invitation links may be shared by the owner.
8. PostgreSQL versioned migrations become the schema authority; production Hibernate uses validate. A controlled maintenance cutover is preferred to a mixed old/new-writer rollout.
9. Platform operator accounts and web APIs are not introduced in this MVP. Operators use separately authorized operational identities and migration credentials; the tenant application has no platform role or cross-gym override. A future platform control plane requires a separate principal/security boundary and audit design.
10. S3 remains disabled. If an intended target currently uses S3, asset ownership is an additional prerequisite before that target can adopt this MVP; enabling the old unowned image endpoints must fail closed.

Alternative considered: PostgreSQL row-level security in addition to application scoping. It can add read/write defense, but requires transaction-bound connection context, authentication exceptions, pool reuse verification, and a separate operational model. It is not silently assumed present. This proposal uses explicit application authorization plus relational consistency constraints; direct database administrators remain outside the tenant HTTP security boundary. Database-per-gym and account-to-many-gym models conflict with the selected MVP scope.

## 3. Proposed entity relationships

~~~mermaid
erDiagram
    GYM ||--|{ USER : contains
    USER ||--o| GYM : designated_primary_owner
    USER ||--o{ ACTIVE_MEMBERSHIP : holds
    MEMBERSHIP_PLAN ||--o{ ACTIVE_MEMBERSHIP : defines_package
    USER ||--o| TRAINER_PROFILE : has
    USER ||--o{ TRAINER_SESSION : books
    TRAINER_PROFILE ||--o{ TRAINER_SESSION : coaches
    USER ||--o{ ATTENDANCE : records
    USER ||--o{ WORKOUT_LOG : records
    WORKOUT_LOG ||--o{ WORKOUT_EXERCISE_LOG : includes
    EXERCISE ||--o{ WORKOUT_EXERCISE_LOG : references
    USER ||--o{ FOOD_LOG : records
    USER ||--o| NUTRITION_GOAL : sets
    USER ||--o{ PROGRESS_METRIC : records
    USER ||--o{ NOTIFICATION : receives
    GYM ||--o{ MEMBERSHIP_PLAN : owns
    GYM ||--o{ EXERCISE : owns
    GYM ||--o{ WORKOUT_PLAN : owns
    GYM ||--o{ NUTRITION_PLAN : owns
    GYM ||--o{ GYM_INVITATION : issues
~~~

All business tables also carry gym_id even when the diagram shows their user/parent link. Existing global UUID/user IDs and bigint business IDs remain primary keys. No compound primary-key conversion or URL ID renumbering is required.

### 3.1 New/changed records

| Record | Fields and relationship |
|---|---|
| gyms | id UUID PK; name; globally unique slug for display/navigation; status ACTIVE/SUSPENDED; primary_owner_user_id; created_at/updated_at. Names need not be unique. No hard-delete API |
| users | Existing fields preserved; gym_id UUID; role MEMBER/TRAINER/ADMIN/OWNER; authz_version bigint NOT NULL DEFAULT 0. No platform role. enabled remains the account switch |
| gym_invitations | UUID id; gym_id; invited email; token_hash (unique); invited role fixed to MEMBER for MVP; created_by_user_id; expires_at; accepted_at/revoked_at; timestamps. Store only a digest of a high-entropy token; return plaintext once |
| Existing business entities | Add gym_id; retain all existing dates, statuses, notes, prices, IDs, and history semantics |

Gym slug is a selector/display value, not authorization. Timezone configuration is deferred: membership dates and trainer wall times retain UTC, with no timezone-based reinterpretation of existing data.

### 3.2 Ownership invariant and safe onboarding insertion

Every committed gym must designate an existing, enabled OWNER from the same gym. Use a deferred composite FK (gyms.id, primary_owner_user_id) -> users(gym_id, id), plus a deferred constraint trigger validating the designated user's role/enabled status and presence. The user-to-gym FK prevents orphan users.

The primary-owner column is SQL-nullable to permit transaction construction, but a committed NULL is forbidden by the deferred trigger. Onboarding inserts the gym, inserts its OWNER, assigns primary_owner_user_id, and commits all three together. No partially constructed gym is exposed through the API. A failed commit rolls back the complete onboarding transaction.

New owners have enabled=true. For a legacy row, mirror the existing User.isEnabled() semantics (NULL means enabled) when checking the owner invariant, using COALESCE(enabled,true); do not rewrite legacy NULL/false flags or enable a disabled account during backfill. Owner selection still requires the separate usability/credential gate.

Validate the invariant on gym INSERT/UPDATE and relevant user UPDATE/DELETE. Owner/administrative identity mutations take the gym row lock before user locks and recheck current state. The designated owner cannot be deleted, disabled, demoted, or moved through ordinary APIs. The primary pointer is immutable in the runtime MVP. Future ownership transfer must lock/revalidate the same gym and change designation atomically before demotion. Extra OWNER rows do not weaken the primary-owner guarantee.

This is a cross-row invariant; do not attempt a CHECK expression querying users. PostgreSQL documents the limitation of CHECK constraints and supports deferred constraint triggers. [PostgreSQL constraints](https://www.postgresql.org/docs/18/ddl-constraints.html), [constraint triggers](https://www.postgresql.org/docs/18/sql-createtrigger.html).

### 3.3 Table/foreign-key ownership map

All gym-owned tables have a required gym_id in the final schema. Referenced entity tables have UNIQUE(gym_id, id), in addition to their existing PK, to support composite FKs. Composite references use the child gym_id; a valid foreign ID in another gym must fail at the database boundary too.

| Table | Same-gym parent constraints / final uniqueness |
|---|---|
| users | gym_id -> gyms.id; existing global UNIQUE(email); UNIQUE(gym_id,id) |
| active_memberships | (gym_id,user_id) -> users; (gym_id,plan_id) -> membership_plans |
| trainer_profiles | (gym_id,user_id) -> users; retain UNIQUE(user_id), since an account has one gym/profile |
| trainer_sessions | (gym_id,member_id) -> users; (gym_id,trainer_id) -> trainer_profiles |
| attendance | (gym_id,user_id) -> users |
| workout_logs | (gym_id,user_id) -> users |
| workout_exercise_logs | (gym_id,workout_log_id) -> workout_logs; (gym_id,exercise_id) -> exercises |
| food_logs | (gym_id,user_id) -> users |
| nutrition_goals | (gym_id,user_id) -> users; retain UNIQUE(user_id) |
| progress_metrics | (gym_id,user_id) -> users |
| notifications | (gym_id,user_id) -> users |
| membership_plans | UNIQUE(gym_id,name), replacing global name uniqueness |
| exercises | UNIQUE(gym_id,name), replacing global name uniqueness |
| workout_plans | UNIQUE(gym_id,level), replacing global level uniqueness |
| nutrition_plans | UNIQUE(gym_id,category), replacing global category uniqueness |
| membership_plan_features | gym_id + composite FK to membership_plans |
| trainer_certifications / trainer_available_slots | gym_id + composite FK to trainer_profiles |
| workout_plan_schedule / workout_plan_exercises | gym_id + composite FK to workout_plans; exercise-name strings remain strings in this MVP |
| nutrition_plan_meals | gym_id + composite FK to nutrition_plans |
| gym_invitations | gym_id -> gyms; (gym_id,created_by_user_id) -> users; UNIQUE(token_hash) |

All entity gym_id changes are forbidden through runtime APIs. Historical rows and users are soft-disabled/status-preserved as today; do not add delete cascades that erase history. Keep current collection semantics and ordering; the tenancy migration is not a list-deduplication or workout-plan redesign.

### 3.4 Indexes

Add nonredundant indexes for actual predicates; avoid duplicating indexes already supplied by PK/UNIQUE constraints:

| Table | Proposed leading columns |
|---|---|
| users | (gym_id,role,created_at); global email index remains |
| active_memberships | (gym_id,user_id,start_date DESC,id); (gym_id,status,start_date,end_date); (gym_id,plan_id) |
| trainer_profiles | Composite unique gym/id; user uniqueness supplies the user lookup |
| trainer_sessions | (gym_id,member_id,session_date,start_time); (gym_id,trainer_id,session_date,start_time), with SCHEDULED partial indexes considered after EXPLAIN |
| attendance | (gym_id,user_id,check_in_time DESC); (gym_id,check_in_date); nonunique partial index (gym_id,user_id,check_in_time DESC) WHERE check_out_time IS NULL |
| workout_logs | (gym_id,user_id,date); workout_exercise_logs (gym_id,workout_log_id) and (gym_id,exercise_id) |
| food_logs | (gym_id,user_id,log_date,log_time) |
| progress_metrics | (gym_id,user_id,log_date DESC) |
| notifications | (gym_id,user_id,created_at DESC) |
| catalogs / collections | Scoped unique keys above; indexes beginning with each composite parent FK |
| gym_invitations | (gym_id,created_at); (gym_id,email); token_hash unique lookup |

Do not add a unique ACTIVE-membership constraint, a date-dependent current-membership index, or an overlap exclusion constraint in this project. Existing historical overlaps must be preserved, and membership/booking invariants retain their existing application locks. No unique-open-attendance constraint is introduced without a separate data/race assessment. PostgreSQL does not automatically create indexes on referencing FK columns; verify plans in a disposable PostgreSQL rehearsal. [Foreign-key/index behavior](https://www.postgresql.org/docs/18/ddl-constraints.html).

## 4. Backend security architecture

### 4.1 Trusted identity and authorization flow

1. Validate JWT signature, expiration, token schema, credential version, and authenticated account identity.
2. Load the account by stable UUID and its gym/status from the database. This global identity lookup is confined to authentication; it is not exposed as a tenant user lookup.
3. Require enabled account, non-null gym, ACTIVE gym, matching JWT gym_id, and matching authz_version. Build immutable TenantActor(userId UUID, gymId UUID, role Role, authzVersion long) from the database result.
4. Resolve every target through a gym-scoped query, then enforce self/manager permission. Pass a TenantActor or a gym ID obtained exclusively from this actor to data access. Missing context fails closed.
5. Writes assign gym_id server-side. Validate all referenced records before inserting the parent, and use a transaction for multi-row writes. Composite FKs provide a second consistency check.

Never derive authority from request gymId, X-Gym-ID, slug, URL user ID, body role, stored frontend user, or a JWT role claim alone. A supplied selector must either match the trusted actor or be rejected; it cannot switch the tenant. Do not let a null predicate mean all gyms. Avoid a mutable ThreadLocal; if framework context is used, clear it in finally and test pooled/concurrent request reuse.

There are no client-selectable tenant parameters in these MVP APIs. Reject explicit tenant/owner/version/hash/platform-privilege overrides with 400, including X-Gym-ID, instead of treating them as context. Keep legacy informational userId/member fields in existing write DTOs where already accepted, but ignore them for ownership and assign from the authorized path/database. Actual resource references (membershipId, trainerId, exercise IDs) are always validated. Existing booking body status cannot override SCHEDULED. Generic role assignment of OWNER is invalid input; ADMIN granting ADMIN is a 403 permission failure. These rules preserve the existing body-ID-spoofing protections without trusting new tenant fields.

Change the current wildcard public /api/auth/** and corresponding JWT-filter skip rule to an explicit public method/path allowlist. GET /api/auth/me must be authenticated and filtered; its location under /api/auth does not make it public.

Use service-level authorization as well as HTTP path rules so internal service calls cannot recover the old ADMIN bypass. Scoping precedes DTO mapping, lazy association traversal, analytics, and CSV payload construction. Foreign IDs return the same generic 404 as missing IDs; same-gym insufficient privileges return 403. Unauthenticated, disabled, invalid, and stale sessions return 401. An authenticated suspended gym receives 403 GYM_UNAVAILABLE. Valid credentials for an unassigned legacy account produce 403 GYM_ASSIGNMENT_REQUIRED and no tenant JWT; disabled credentials still produce 401.

### 4.2 Role and target policy

| Caller | Personal records | Gym management | User role/enable changes |
|---|---|---|---|
| MEMBER | Self, in own gym | None | None |
| TRAINER | Self, in own gym; no implied client access | None | None |
| ADMIN | Same-gym personal operations currently available to admins | Same-gym users, trainers, plans, attendance, reports | Only MEMBER/TRAINER targets and assignments; cannot target an ADMIN/OWNER or grant ADMIN |
| OWNER | Same-gym personal operations | Own gym plus all local management | May manage MEMBER/TRAINER/ADMIN; never assign OWNER/platform authority through ordinary updates; OWNER targets protected |
| Platform operator | No tenant JWT or tenant API bypass | No platform API in this MVP | Separate operational identity and separately authorized procedure only |

Generic update DTOs cannot deserialize OWNER/platform assignment as an assignable role. Use an assignable-role enum MEMBER/TRAINER/ADMIN with caller-specific validation. Tenant IDs, authz_version, enabled-owner state, primary-owner fields, and hashes are not mass-assignable. Existing trainer-profile/history behavior under MEMBER/TRAINER role changes must be retained and tested; this project does not invent trainer-to-client privileges.

Existing ADMIN accounts are **not** automatically owners or platform operators. Each needs an explicit gym assignment. Retain ADMIN locally unless an approved owner designation explicitly promotes that account. Existing disabled accounts remain disabled; no public/demo administrator is promoted, re-enabled, or given a replacement password by migration.

### 4.3 JWT and account change behavior

New JWT schema: tokenVersion=2, sub=stable user UUID, gymId=UUID, authzVersion=current bigint, credentialsVersion=the existing keyed password-hash version, plus issued/expiry timestamps. Preserve the current signing secret and configured lifetime unless separately approved. No password hash is exposed.

Reload enabled state, current role, gym assignment/status, and versions on every request. Change role, gym assignment, email identity, or enabled status in a transaction that also increments authz_version. Password changes retain the credential-version protection and may increment authz_version as well. Re-enabling an account does not resurrect its old token. Any controlled assignment correction increments the version, and the gym-claim comparison rejects a token from the previous gym even if versioning were missed.

All legacy tokens lack required tenancy claims and must receive 401; users sign in again. Ordinary role changes invalidate existing sessions rather than silently retaining a privileged UI. Permission changes apply to subsequent requests; this is not a claim that an already authorized in-flight transaction can be retroactively cancelled. Extend public-demo-password blocking to OWNER as well as ADMIN so a promoted legacy account cannot evade the existing protection.

### 4.4 Files, logs, exports, and platform separation

The image API has no asset model today. Default remains AWS_S3_ENABLED=false; reject enabling it in the tenant release until a separately tested asset registry (gym, object key, owner, permitted action) and tenant prefixes are implemented. A URL/prefix alone is insufficient authorization. If optional asset support is implemented, resolve deletion by scoped asset ID, not arbitrary URL.

Attendance CSV is generated in the browser from admin/dashboard attendanceHistory; there is no separate export controller. Both that aggregate and GET admin/attendance must already be scoped before export. Page search is navigation search and has no separate data API.

Add gym/user/action identifiers to security audit events without request bodies, nutrition/measurement values, password hashes, JWTs, invitation tokens, or cross-gym record details. Runtime database credentials are DML-only, not table owners/superusers or migration credentials. Future platform identities must not be represented by Role.ADMIN or a null-gym User.

## 5. Complete endpoint authorization matrix

Current behavior legend: P = authenticated self or global ADMIN through RecordAccess; C = authenticated global catalog; A = global ADMIN; U = public; I = authenticated, globally unowned S3 operation when enabled.

Required behavior: S = active gym and target in actor's gym, then self or OWNER/ADMIN; Cg = active gym, gym-scoped catalog; M = OWNER/ADMIN in actor's gym with target-role restrictions above. Every authenticated row requires valid tenancy claims and current account/gym checks.

Test codes (all applicable codes apply in both directions A->B and B->A): T1 unauthenticated/disabled/stale/no-gym; T2 self/other-member/trainer/admin/owner role matrix plus foreign/missing IDs; T3 scoped list, nested DTO, count, chart, and search exclusions; T4 foreign nested/reference/body-ID spoofing, atomic rejection and unchanged database state; T5 existing business rules and PostgreSQL contention; T6 privileged role/mass assignment/last-owner restrictions; T7 invitation/onboarding/global uniqueness; T8 conditional media denial; T9 browser report/session checks. These are specifications, not executed results.

| # | Method and route | Current | Required ownership/authorization | Reference validation and required tests |
|---:|---|---|---|---|
| 1 | POST /api/auth/register | U; creates unassigned MEMBER | Valid unexpired, unused MEMBER invitation; derive gym from stored invitation | Ignore/reject body gym/role; global email uniqueness; atomic consume. T7,T4 |
| 2 | POST /api/auth/login | U; email/password | Global identity authentication; issue tenant JWT only for assigned enabled account and active gym | No client gym selection; known public admin/owner password blocked. T1,T7 |
| 3 | POST /api/auth/forgot-password | U; placeholder | Generic accepted response remains; no lookup data disclosed | No reset token/email delivery is added by tenancy. T1, enumeration check |
| 4 | GET /api/health | U; static UP | Public, no tenant/customer details | Availability is not DB readiness; no new record access. T1 |
| 5 | GET /api/users/{id} | P | S on target user | Scoped target lookup before DTO. T1,T2 |
| 6 | GET /api/dashboard/{userId} | P | S on target user and every contributing table | Memberships, workouts, exercises, food, attendance, progress, sessions, notifications and nested trainer user all local. T1,T2,T3 |
| 7 | GET /api/notifications/{userId} | P | S; notifications and recipient local | No cross-gym title/count leak. T1,T2,T3 |
| 8 | GET /api/memberships/plans | C | Cg | Local plan features only. T1,T3 |
| 9 | POST /api/memberships/subscribe/{userId}/{planId} | P | S; target and plan local | Lock local user; scope plan; no foreign plan can be purchased. T1,T2,T4,T5 |
| 10 | GET /api/memberships/user/{userId} | P | S; local membership history and plans | All statuses/history retained. T1,T2,T3,T5 |
| 11 | GET /api/memberships/user/{userId}/active | P | S; local current selection | Preserve 204/409 and UTC eligibility. T1,T2,T3,T5 |
| 12 | POST /api/memberships/renew/{userId}/{membershipId} | P | S; membership belongs to both target user and gym | Retain expectedEndDate, latest-period/history/overlap rules and lock. T1,T2,T4,T5 |
| 13 | POST /api/memberships/upgrade/{userId}/{planId} | P | S; body membershipId, target user and selected plan local | Membership belongs to target; preserve expiry, higher package price, cancelled history. T1,T2,T4,T5 |
| 14 | GET /api/trainers | C | Cg | Profile, nested user, certifications and availability local; existing enabled filter. T1,T3 |
| 15 | POST /api/trainers/book/{memberId}/{trainerId} | P | S; member and trainer profile/user local | Existing member-then-profile locks, trainer role/enabled, times, availability and overlap query preserved. T1,T2,T4,T5 |
| 16 | GET /api/trainers/sessions/{memberId} | P | S; member and all session links local | No new trainer-client permission. T1,T2,T3 |
| 17 | GET /api/workouts/exercises | C | Cg | Local exercise metadata only. T1,T3 |
| 18 | GET /api/workouts/plans | C | Cg | Local plan and schedule/exercise-name collections. T1,T3 |
| 19 | GET /api/workouts/logs/{userId} | P | S | Log, exercise entries and nested exercise local. T1,T2,T3 |
| 20 | POST /api/workouts/logs/{userId} | P | S | Validate every exercises[].exercise.id locally before parent insert; roll back full batch on failure; body user IDs cannot redirect. T1,T2,T4 |
| 21 | GET /api/nutrition/dashboard/{userId} | P | S | Goal and food inputs local, totals local. T1,T2,T3 |
| 22 | GET /api/nutrition/plans | C | Cg | Local meal collections only. T1,T3 |
| 23 | POST /api/nutrition/goal/{userId} | P | S | Existing goal, recipient and upsert local. T1,T2,T4 |
| 24 | GET /api/nutrition/goal/{userId} | P | S | Preserve 204 for absent local goal. T1,T2 |
| 25 | POST /api/nutrition/log/{userId} | P | S | Server-assigned user/gym; body ownership ignored/rejected. T1,T2,T4 |
| 26 | GET /api/nutrition/logs/{userId} | P | S | Date filter must not replace gym/user predicates. T1,T2,T3 |
| 27 | POST /api/tracking/progress/{userId} | P | S | Server-assigned user/gym; preserve metrics. T1,T2,T4 |
| 28 | GET /api/tracking/progress/{userId} | P | S | All weights/measurements/history local. T1,T2,T3 |
| 29 | POST /api/tracking/attendance/check-in/{userId} | P | S | Open-attendance lookup local; preserve existing repeated-check-in behavior. T1,T2,T4 |
| 30 | PUT /api/tracking/attendance/check-out/{attendanceId} | P | S via scoped attendance owner | Resolve attendance by id AND gym before owner check. T1,T2,T4 |
| 31 | GET /api/tracking/attendance/{userId} | P | S | Local history/statistics inputs only. T1,T2,T3 |
| 32 | GET /api/admin/dashboard | A | M | All user/trainer/plan/attendance lists and membership/growth/revenue/attendance chart inputs local. T1,T3,T6 |
| 33 | GET /api/admin/users | A | M | Every user and membership summary local, including OWNER/ADMIN rows; edit rights remain separate. T1,T3,T6 |
| 34 | PUT /api/admin/users/{userId} | A | M with caller/target-role restrictions | No gym, OWNER/platform privilege, hash, or version assignment; email uniqueness remains global; bump authz_version. T1,T2,T4,T6 |
| 35 | DELETE /api/admin/users/{userId} | A; soft disable | M with target restrictions; never primary/other OWNER | Keep records; bump authz_version; owner invariant. T1,T2,T4,T6 |
| 36 | POST /api/admin/trainers | A | M; new user/profile inherit actor gym | Global email collision is generic; both inserts atomic; role fixed TRAINER. T1,T4,T6,T7 |
| 37 | PUT /api/admin/trainers/{trainerId} | A | M; profile and user local; no privileged target | Global email guard; optional password invalidates credentials; no gym move. T1,T2,T4,T6 |
| 38 | DELETE /api/admin/trainers/{trainerId} | A; disables user | M; profile/user local and target permitted | Preserve sessions/profile/history, invalidate session. T1,T2,T4,T6 |
| 39 | POST /api/admin/membership-plans | A | M; plan assigned actor gym | Same name allowed in other gym, rejected locally. T1,T4,T6 |
| 40 | PUT /api/admin/membership-plans/{planId} | A | M; plan local | No gym move; existing price/history semantics preserved. T1,T2,T4,T6 |
| 41 | DELETE /api/admin/membership-plans/{planId} | A | M; plan local | History existence guard scoped; preserve prohibition on deleting referenced plan. T1,T2,T4,T6 |
| 42 | GET /api/admin/attendance | A | M | Local rows only; safe input for export. T1,T3,T6,T9 |
| 43 | POST /api/images/upload | I | Disabled/absent until tenant asset design implemented | Configuration must not silently enable old API. Optional implementation needs scoped asset and prefix checks. T1,T8 |
| 44 | DELETE /api/images/delete | I | Disabled/absent until tenant asset design implemented | Arbitrary URL deletion cannot be enabled as-is. T1,T8 |

Additional access surfaces: Swagger/OpenAPI public routes disclose API schemas, not tenant records; retain/document their current exposure. OPTIONS and /error remain tenant-data-free. Attendance CSV depends on rows 32/42 and is covered by T9. Workout-plan assignment in localStorage must be keyed by gym+user and selected only from local plans. There are no implemented scheduled jobs, cache-backed data paths, native-query services, or additional data-export controllers in the current main source.

### 5.1 Proposed new endpoints

| Route | Authority and behavior | Tests |
|---|---|---|
| POST /api/auth/register-owner | Public new-account registration; create one gym and its OWNER atomically; no arbitrary owner/gym/role input | T7; global email/slug races; zero orphan rows on failure |
| GET /api/auth/me | Validated account, gym and current role/version; assigned sessions only | T1; never trust restored browser profile |
| GET /api/gym | Current active gym summary for its authenticated users | T1,T3; no arbitrary gym read/list |
| PATCH /api/gym | OWNER only; local permitted metadata fields (name); no slug/owner/status/gym-ID mass assignment | T1,T4,T6 |
| POST /api/gym/invitations | OWNER/ADMIN; MEMBER-only invitation for actor's gym | T1,T4,T6,T7 |
| DELETE /api/gym/invitations/{invitationId} | OWNER/ADMIN, same gym; revoke pending token | T1,T2,T4,T7 |

No global gym directory, cross-gym admin endpoints, account-transfer endpoint, OWNER assignment endpoint, gym-delete endpoint, or platform API is introduced.

## 6. Complete repository change matrix

Current repository files are under /Users/roshan/Desktop/FitCore/gym-backend/src/main/java/com/fitcore/gym/repository. All currently extend JpaRepository, which also exposes unscoped inherited reads/writes. Replace runtime interfaces with a deliberately narrow Spring Data Repository base exposing only needed scoped reads and controlled persistence. Authentication gets a separate UserIdentityRepository; migration code does not reuse runtime repositories.

Notation: G UUID is supplied only by TenantActor; U UUID is a target account; I Long is an existing business ID. All optional ID results use Optional<Entity>; lists keep existing ordering. Proposed method names below are implementation contracts; unused old methods should be removed rather than retained as a second unscoped API.

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

New repositories: GymRepository (findById for authenticated gym resolution, metadata update and owner-management lock); GymInvitationRepository (scoped revoke and hash lookup under lock for public acceptance); UserIdentityRepository (global identity lookup/uniqueness only). Authentication's global lookup must not return a UserDto for arbitrary IDs; duplicate-email errors must not disclose the owning gym.

Remove/forbid unscoped inherited findAll/findById/deleteAll/bulk-update calls in runtime services. Saves must receive entities constructed with the trusted gym or loaded through scoped methods and checked before mutation; new methods do not make arbitrary save(entity) safe by themselves. Use an architecture test/allowlist to confine global identity access to security/auth/onboarding. Migration JDBC access belongs in an explicit operational runner, never a user controller. Child collection loads include gym in the join; no reliance on an unscoped JPA association to establish access.

Current AdminService formulas may continue to calculate from lists, but those input lists and all counts must be scoped. DashboardService must scope every contributing repository. Do not change financial formulas, pagination contracts, membership eligibility, availability parsing, or introduce billing while changing predicates.

## 7. Additive, versioned migration design

### 7.1 Preconditions and private ownership manifest

No account-to-gym assignment can be derived from email domain, the current administrator's identity, shared plans, trainer relationships, or an assumption that the application was one gym. Before a populated database's assignment migration, an authorized owner must approve a private manifest with:

- Target database/schema identity and baseline schema fingerprint; audit timestamp and mapping revision/hash.
- Every existing user UUID, including disabled/demo/service-like accounts, mapped to exactly one approved gym, with decision source/approver.
- Each gym's name, UUID, primary owner UUID, and explicit role changes; existing enabled/password values preserved unless a separate recovery action is approved.
- Catalog ownership/copy decisions, any FK remap, expected row counts, exact immutable before-state fingerprints, and a drift policy that aborts.
- Any booking whose approved member/trainer assignments differ, and a reviewed resolution. The migration cannot invent a historical cross-gym booking model.

The manifest and before/after receipts stay outside Git in restricted local storage. It must contain **zero inferred or default assignments**. Unresolved accounts remain untouched before cutover; the final required-ownership migration cannot run until all are resolved. A fake quarantine/legacy gym is not a workaround.

Create a legacy gym only when approved mappings identify the accounts and catalogs it owns. If ownership decisions identify several existing gyms, create those gyms instead/as well; this strategy supports that outcome. Do not assume there is exactly one legacy gym.

### 7.2 Proposed migration sequence (names are design, not executable files)

| Step | Proposed artifact | Action and safety gate |
|---|---|---|
| M0 | Private schema/ownership review | Future separately authorized inventory, verified backup/isolated restore, owner/admin recovery, freeze plan. No production access is authorized by this document |
| M1 | V1__baseline_existing_fitcore.sql | Capture actual existing schema compatible with 4ad9632, including sequences/identity columns, role checks and all six collections. Empty disposable databases execute V1. Existing populated targets receive an explicit Flyway baseline at version 1 only after schema comparison; do not replay CREATEs or use automatic baselineOnMigrate |
| M2 | V2__expand_gym_ownership.sql | Add gyms, nullable gym_id to all 21 existing tables, authz_version default 0, invitation schema, temporary-compatible role checks and supporting keys/indexes. Retain old global catalog uniqueness. Do not install enforcing composite FKs before the ordered data remap. Do not seed users/gyms, choose an owner, or rewrite data. A populated legacy application is not tenant-safe just because this succeeds |
| M3 | Versioned assignment runner V3__apply_approved_tenant_mapping | In one bounded migration transaction under the controlled write window, validate a private input schema populated only by the authorized operator. Empty database plus empty manifest is valid; populated database without complete approved inputs fails. Create approved gyms, assign users and original catalog owners; establish and verify scoped catalog uniqueness; then drop obsolete global catalog uniqueness BEFORE inserting identical clones. Apply approved catalog copies/FK remaps, backfill dependent and collection gym IDs, and apply approved owner role/designation changes. Reject snapshot/count/link drift; all DDL/data changes in this step roll back together on failure; retain private receipts |
| M4 | V4__enforce_gym_integrity.sql | Install/validate same-gym composite FKs and owner invariant; enforce NOT NULL gym_id and role checks; install immutability/owner protection; verify scoped catalog uniqueness already established by M3. Do not drop/recreate business tables or remove original data columns |
| M5 | Runtime cutover/configuration | Start only the complete tenant-aware application with ddl-auto=validate, SQL initialization never, demo disabled, explicit expected schema version, DML-only credentials, and migration auto-execution disabled in the runtime profile. New gym registration stays off until smoke checks pass |
| M6 | Verification/recovery receipt | Check per-gym counts, nulls, same-gym joins, owner status, history field hashes, catalog maps, identity sequences, and representative authorized reads. Retain separately approved recovery evidence; then authorize onboarding enablement separately |

Schema migrations are versioned and immutable once applied. Environment-specific assignment inputs are not embedded in SQL committed to Git. The proposed Java/operational V3 validates its input itself; a supplied approval string alone is not evidence of authorization. Use operator-only input tables/schema, revoke runtime access, and retain/securely remove inputs according to the private retention policy. Inputs and receipts contain no plaintext password/token.

The catalog constraint replacement deliberately occurs inside M3, after complete manifest validation/original ownership assignment and before cloning. Deferring removal of global name/category/level uniqueness to M4 would prohibit the required identical clones. This replacement changes constraints without deleting/renaming customer values; it is the necessary exception to a purely add-column schema expansion. M4 must not be the first step that permits shared catalog copies. A synthetic two-gym/shared-catalog migration test must prove this exact order. New same-gym FKs cannot enforce intermediate, not-yet-remapped references during M3; final consistency is asserted before its commit and enforced by M4 before reopening traffic.

The first baseline cannot be generated or certified from entity annotations alone. The deployed code reportedly trails main, although Phase 2/3 add no persistent entity columns; actual database types, indexes, checks, triggers, sequence state and drift remain an explicit future verification gate. Unknown custom triggers/constraints abort preparation until reviewed.

Flyway baselining acknowledges existing schema state; it is not proof the schema matches. Spring recommends a single initialization mechanism. Keep baselineOnMigrate=false, cleanDisabled=true, and runtime migration execution disabled. A dedicated reviewed runner holds schema credentials. [Flyway baselines](https://documentation.red-gate.com/flyway/flyway-concepts/baselines), [Spring database initialization](https://docs.spring.io/spring-boot/how-to/data-initialization.html).

### 7.3 History and catalog preservation

Membership IDs, user IDs, start/end/creation dates, stored statuses and prices are not cleaned up by tenancy. Preserve the current exclusive UTC expiration, renewal behavior, stale-state guard, package-price upgrade comparison, cancelled upgrade history, and member-row lock. Booking IDs, dates/times/status, member and trainer identities, availability conventions and member-then-trainer-profile locks remain intact. Do not introduce a new requirement for a paid/active membership to book; current booking code does not impose one.

The historical report records overlapping memberships for two accounts. Those findings are not today's data. Tenant backfill preserves duplicate/overlapping records; it does not choose a winner, cancel rows, merge periods, recalculate dates, or mark all stored ACTIVE rows expired. Current fail-closed 409 behavior and administrative review access remain. Any earlier approved remediation is a separate operation; verify its status and keep its files unchanged. Deployment readiness must explicitly address unresolved affected users, rather than silently repair them here.

If all approved owners of a catalog are one gym, retain its original ID there. If a shared plan/exercise is referenced by users mapped to different gyms, the mapping must designate which gym retains the original. Clone identical catalog values and collection rows into the other approved gyms and remap only the explicitly approved child FKs using an old-ID/new-ID/gym map. Preserve membership/log IDs and historical field values, and record before/after plan/exercise references. This necessary FK change requires explicit approval; it is not hidden under a claim of byte-identical history.

If a historical booking's member and trainer map to different gyms, stop; cloning a trainer account is incompatible with globally unique email/one-account-one-gym and would change identity. Obtain an ownership/resolution decision or expand the model in a new design before proceeding. Never rewrite bookings to a different human.

Unreferenced catalog ownership also requires a decision. New gym defaults are reviewed, versioned static templates without customer records; do not run db/demo-data.sql or clone users, memberships, trainer contact details, logs, or live commercial plans. Workout-plan exercise-name lists are preserved; audit local name resolution during catalog copying and report unresolved names without automatic renaming.

### 7.4 Constraints, cutover, rollback and recovery

Use explicit named constraints and lock/statement timeouts. NOT VALID/VALIDATE can separate installing eligible FK/check constraints from scanning old data; required fields are enforced only after complete verified backfill. Size/lock/index timings must be measured on disposable PostgreSQL. Prefer ordinary transactional index creation during the chosen maintenance window for this MVP; if measured size requires concurrent indexes, split those into explicit nontransactional migration steps and test partial-failure recovery. [PostgreSQL ALTER TABLE](https://www.postgresql.org/docs/18/sql-altertable.html).

The safest initial release is a maintenance cutover: stop all old HTTP writers, scripts, and application instances; verify the freeze; apply the approved sequence with drift checks; start the full tenant-aware build; run smoke/reconciliation checks; reopen writes. Do not let old unscoped application code coexist with multiple gyms. A feature flag gates onboarding only, never whether tenant authorization applies.

Before a successful commit, transaction failure rolls back its changes. Earlier successful migration versions may remain as an expanded schema after a later version fails; keep traffic stopped, inspect Flyway history and the private receipt, correct via a reviewed forward migration, and do not blindly repair/re-run.

After tenant data exists, prefer a forward fix or an older **tenant-aware, schema-compatible** artifact. Never roll back to master/4ad9632 or remove gym predicates to restore service. A pre-tenancy application rollback requires restoring the corresponding pre-cutover database and proving no newer tenant writes are lost or exposed, under separate approval. There is no automatic down migration dropping gym columns, merging tenants, or reinstating global catalog uniqueness; duplicate names across gyms can make that reversal impossible.

Recovery requires a verified backup and successful restore to a distinct disposable/isolated PostgreSQL target with application writers and external integrations disabled. Record recovery time and all post-restore checks. A backup listing, provider retention window, synthetic fixture test, or code rollback alone is insufficient. Real customer restores require separately authorized private handling; normal automated rehearsals use synthetic data only.

## 8. Owner onboarding and existing administrators

New-owner workflow:

1. Public registration accepts owner name/email/password and gym name/slug. Reject body user ID, gym ID, role, owner ID, enabled/version overrides, and known public privileged passwords. Apply the existing password policy; public-request abuse controls need release verification.
2. Check global email availability and slug validity. Database uniqueness is the concurrency authority; a race must roll back all created rows and yield a generic 409 without another gym's identity.
3. In one transaction create the gym, fixed-role OWNER account, primary-owner designation, and any vetted starter catalogs. No commercial membership plan or demo customer is implicitly inserted.
4. Commit after deferred constraints succeed. Only then issue the v2 JWT and current-gym profile. A failed transaction returns no usable session and leaves no gym, user, template, or collection residue.
5. The owner manages their gym through scoped admin endpoints and issues MEMBER invitations. Invitations bind email, gym, creator and expiry, and are single-use under a row lock. Default lifetime is 48 hours using the application clock; revoke or successful acceptance makes replay invalid. Acceptance creates a MEMBER in the stored invitation gym atomically with consuming the token; a failed global email insert must not consume it.
6. ADMIN can issue MEMBER invitations and manage local MEMBER/TRAINER records. OWNER can appoint ADMIN through the controlled assignable-role policy. Neither route can create OWNER or platform authority. Additional owners/ownership transfer use a future explicit procedure, not the generic role dropdown.

Existing administrators need private verified recovery and explicit mapping before cutover. Their IDs, emails, password hashes and enabled state remain. Only specifically approved owner accounts receive OWNER; no blanket promotion of all ADMIN rows. A designated owner must be usable and enabled without a compromised public password; migration cannot manufacture recovery credentials. Unassigned users cannot be defaulted into the creating owner's gym, and existing users cannot be attached through invitation acceptance without a separately designed transfer process.

## 9. Tenant-isolation verification strategy

Use synthetic gyms A and B, owners/admins/trainers/members in each, multiple members within A, suspended gym C, disabled accounts, and transitional unassigned fixtures. Use distinct data sentinels and the same catalog names in A/B. Global IDs remain unique; cross-gym tests use known real foreign IDs rather than pretending global IDs collide.

| Layer | Required assertions |
|---|---|
| HTTP boundary matrix | Every one of the 44 existing routes plus proposed routes has authorized success/expected business error and the applicable T codes. Both directions between gyms; own user, another local user, foreign user, local/foreign administrator and owner; direct API calls bypassing UI |
| Referenced IDs | Foreign plan, membership, trainer, exercise, attendance, invitation, collection parent; mixed exercise batches; body ownership/role/version spoofing; valid local path with foreign nested ID. Reject atomically with no changes to either gym |
| Aggregates/exports | B sentinels absent from A's complete JSON, nested DTOs, counts, growth/revenue/attendance charts, notification titles, and CSV. Assert formulas remain unchanged for A's own fixture data |
| Authentication | Bad/expired/legacy JWT; missing tenant/version; tampered claim; disabled or re-enabled account; role/email/gym/version change; suspended gym; credential rotation; public demo admin/owner credentials; stored browser profile tampering |
| Ownership/privilege | Ownerless gym fails commit; foreign/disabled/non-OWNER primary fails; primary owner disable/demotion/delete forbidden; concurrent privileged edits cannot remove required ownership; ADMIN cannot target/promote peer admins or owners; no platform grant |
| Repository architecture | No unscoped runtime business lookup; explicit authentication allowlist; all scoping happens before mapping/counting; write checks do not rely only on method names; no tenant context carried across sequential/concurrent requests |
| PostgreSQL constraints | Direct invalid composite FK inserts/updates fail; parent collections local; duplicate same-gym catalog names fail and cross-gym duplicates succeed; immutable gym ownership; deferred owner trigger evaluated at commit; service DB role lacks DDL/input-schema privileges |
| Business regressions | Existing UTC start-inclusive/end-exclusive dates, month-end arithmetic, expectedEndDate races, current/expired renewals, higher package upgrades, history/conflict behavior, trainer/member overlaps, unrestricted empty schedules, invalid times and active trainer validation |
| Concurrency | Existing same-member/same-trainer races still yield intended one-success/one-conflict outcome; independent gyms succeed concurrently; denied foreign requests do not acquire/update the foreign target through unscoped locks; invitation and global-email races |
| Migration | Empty DB -> V1..V4; legacy-shaped synthetic dataset -> explicit baseline -> mapping -> enforcement; multiple legacy gyms, shared catalog copies, disabled/admin accounts, historic overlaps. Missing/duplicate/drifted maps and foreign historical booking fail without partial assignment. Preserve row/field hashes except approved gym/role/reference changes |
| Recovery | Fail after each migration stage; inspect partial version history; restore a synthetic pre-cutover backup; test tenant-aware rollback candidate; prove old unscoped runtime is refused by release procedure |
| Frontend | OWNER navigation/current gym; remembered session/reload/logout; stale session redirect; scoped member/trainer/admin lists; invitation join; local workout-plan storage migration; dark/light/mobile accessibility; CSV contents |

Keep fast H2/unit tests where useful, but PostgreSQL-only DDL, deferred constraints, migration behavior and lock/concurrency tests must run on disposable PostgreSQL with Flyway and Hibernate validate. H2 auto-created schemas cannot certify these properties. Existing tests need gym-aware fixtures and OWNER cases; the historical test count is a baseline, not a required unchanged count. No tests are marked passing by this document.

## 10. Backward compatibility and explicit blockers

| Risk / unresolved item | Required resolution and stage |
|---|---|
| Actual legacy ownership is unknown | Private complete mapping and named enabled owners required before M3; not a reason to assign all users to one gym |
| Actual deployed schema/revision is unverified | Future separately authorized schema inventory and V1 comparison before any baseline or migration |
| Historical conflicting memberships | Determine current status; retain separate owner-approved remediation/readiness decision; never auto-clean during tenancy |
| Historical booking spans approved gyms | Blocking model/data decision; no identity-changing shortcut |
| Shared and unreferenced catalog ownership | Approve original-ID gym and exact copy/remap policy before M3; starter-template allowlist before onboarding release |
| Administrator/owner recovery and backup evidence | Historical reports leave these open; obtain fresh private proof before production cutover; code design is not proof |
| Existing global administrator expectations | Approve the role/target matrix and same-gym personal access before coding; notify of reduced scope and mandatory re-login before release |
| Registration contract changes | Old uninvited registration is rejected; frontend/backend released together; preserve login and existing business URLs, body shapes and response fields where safe |
| Mutable plans/revenue history | Preserve current behavior; no ledger/proration/repricing or historical price reconstruction; approved catalog clones copy exact values |
| Legacy tokens/browser caches | Force re-login; GET auth/me validates role/gym; gym+user storage keys; accept old saved workout selection only after verifying its ID exists in local returned plans |
| Optional S3 usage | Confirm intended deployment does not depend on it, or implement asset ownership before enabling it |
| Platform operations | No platform UI/access is provided. If it is needed for MVP operations, a separate approved capability/principal design is required |
| Source branch versus release branch | Development baseline main includes improvements absent from reported production master. No default-branch change, main deployment guard removal, push or merge is implied |
| Email matching | Exact current uniqueness retained; later case-insensitive normalization needs an audit and separately approved identity policy |

Approval requested now is approval of the architecture and implementation checklist. It is not approval of any private ownership decision, production data read/write, migration execution, account/password recovery, deployment, push, or merge. The next source change must wait for explicit coding approval.

## 11. Source evidence catalog

All source paths below are rooted at /Users/roshan/Desktop/FitCore.

- gym-backend/src/main/java/com/fitcore/gym/entity: the 15 entities and six collection mappings listed above.
- gym-backend/src/main/java/com/fitcore/gym/controller: all 12 controllers yielding the 44 endpoint rows.
- gym-backend/src/main/java/com/fitcore/gym/repository: all 15 interfaces listed above.
- gym-backend/src/main/java/com/fitcore/gym/security/{RecordAccess,SecurityConfig,JwtService,JwtAuthenticationFilter,CustomUserDetailsService}.java: current global ADMIN bypass, auth flow, credential binding and demo-admin blocking.
- gym-backend/src/main/java/com/fitcore/gym/service/{MembershipService,TrainerService,TrainerAvailability,AdminService,DashboardService,WorkoutService,NutritionService,TrackingService,NotificationService,UserService,AuthService,S3Service}.java: all business data paths and locks.
- gym-frontend/src/{App.jsx,context/AuthContext.jsx,routes/AdminRoute.jsx,services/api.js,pages/Admin.jsx,pages/Workouts.jsx}: current session/routes, browser CSV and plan selection.
- gym-backend/src/main/resources/application.properties, application-demo.properties, db/demo-data.sql; gym-backend/src/test/resources/application.properties; gym-backend/pom.xml; gym-backend/Dockerfile; render.yaml; vercel.json; gym-frontend/vercel.json: initialization/test/deployment evidence.
- docs/phase2-membership-booking-2026-10-20.md, docs/phase2-source-publication-2026-10-20.md, docs/phase3-ui-review-2026-10-22.md, docs/production-data-remediation-2026-10-20.md: historical verification/release/operational limits, not fresh production evidence.

Companion checklist: [multi-tenant implementation plan](../plans/2026-10-30-multi-tenant-mvp.md).
