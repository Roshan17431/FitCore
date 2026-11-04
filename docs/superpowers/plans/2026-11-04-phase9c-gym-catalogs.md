# Phase 9C Gym Catalog Management Implementation Plan

> Execute with subagent-driven-development for independent frontend/tests and root-owned backend/schema integration. No commits or external operations.

**Goal:** Functional gym-local exercise/workout/nutrition management with safe archival/history.
**Architecture:** Approved conservative V9 design; existing string collection representation and historical IDs remain. Managers only, gym from TenantActor, transactions/locks, no hard deletes.
**Spec:** ../specs/2026-11-04-gym-catalog-management-design.md (user approved conservative V9).
**Stack:** Spring/Java17, PostgreSQL18, React/Vite/Node22, existing local Playwright harness.

## API contract

Prefix `/api/admin/catalog`; collections `/exercises`, `/workouts`, `/nutrition`. Exercise and nutrition each: GET collection, POST create201, GET /{id}, PUT /{id}, POST /{id}/archive. Workouts same plus POST /{id}/publish. Updates/actions200 with record. Foreign IDs404, invalid400, duplicate/reference409, unauthorized403. No gym/owner body fields trusted.

Exercise JSON: id,name,category,difficulty(BEGINNER/INTERMEDIATE/ADVANCED),recommendedSets,recommendedReps,description,imageUrl,imageApproved,archived. Request excludes id/ownership/archived/imageApproved. Strings name100/category100/reps100/description4000/image255, sets1..100; fields optional except name/category/difficulty. Image policy project-local /images/... or explicit HTTPS-origin allowlist, no backend fetch; unchanged legacy metadata retained but imageApproved computed for safe rendering.

Workout JSON: id,level,duration,schedule(string[]),exercises(existing name string[]),exerciseIds(long[]),unresolvedExercises(string[]),status(DRAFT/PUBLISHED/ARCHIVED). Request level100/duration100, schedule<=31 entries<=255, exerciseIds optional<=100 distinctpositive. Omitted IDs preserve old names on update, [] clears (draft only). New plan defaultsDRAFT; explicit status allowed. Publish requires nonempty all-active local refs; changed selections while published validate all. Unchanged legacy metadata edits preserve names. No automatic rename remapping.

Nutrition JSON: id,category,calories,protein,carbs,fats,meals(string[]),archived. Request category100, calories0..20000, macros0..2000, meals<=31 nonblank entries<=255. All macro fields required. Values are estimates.

## Tasks / file boundaries

- [x] Baseline full gate954/35/44 already passed; preserve e669f08 and Phase9A/B evidence.
- [x] Root: write failing catalog migration test, then V9 adds exercises.archived=false, nutrition_plans.archived=false, workout_plans.status=PUBLISHED defaults/constraints. New managed workout defaultDRAFT. Entities/repositories/public filters/history-safe locks, media policy and DTOs.
- [x] Backend-test agent: new CatalogManagementTest HTTP role/reference/history/validation/rollback/concurrency coverage; publish ENDPOINTS set so original all-controller coverage includes new routes; initial smoke test RED before controller implementation. Agent never runs Maven/Docker.
- [x] Root: CatalogController/CatalogService implement contract under gym serialization + sorted locked exercise reads. Block referenced rename, published-reference archive; preserve history. No membership/booking service changes.
- [x] Frontend agent: /gym/catalogs protected manager screen, APIs/nav, create/edit/archive/publish forms, role-aware empty states in Membership/Workouts/Nutrition, safe media approval flag, estimates wording, Node contracts and Playwright workflow/screenshots. Own all frontend files; may run Node/lint/build but no backend/harness/Docker. Use existing components/style, no unrelated redesign.
- [x] Root: upgrade runtime-target fixture migration calls toV9, require history9, extend production descriptor with normalized defaults; independently compare/certify fresh/staged/full restore V9 descriptors and exact hashes before acceptance. Keep old V1–V8 bytes untouched. Adapt/readiness negative tests for default/status drift and preserve existing cases.
- [x] Review backend/frontend changes, focused integration then full release gate, inspect desktop/mobile/light/dark screenshots, record source files/migration/defaults/hash proofs/counts/remaining production blockers.

## Review focus

No source changes outside worktree, production access, real recipients, persistent DB, push/merge/commit/deploy. Nested IDs never choose gym; managers only; archived records never deleted; referenced names immutable. Omitted vs empty selections must not erase legacy lists. Preserve string field limits and unspecified collection ordering. V9 readiness must reject V8 and any unknown/default drift. Race tests must prove lock ordering, no stale exercise cache. Do not weaken original endpoint/test matrices or auth/startup protection.

## Implementation rulings and review evidence

- Verified the existing image column is varchar255; request/UI limits use255 rather than an assumed1000. Existing schedule/meal/exercise-name collection widths remain255, with no ordering promise added.
- V9 preserves existing public visibility through PUBLISHED/false defaults; manager-created workouts explicitly choose DRAFT. No ownership mapping, production seed or V3 was created.
- Nullable READ_ONLY imageApproved preserves the old exercise DTO request contract; the initial primitive flag caused log requests400, reproduced before correction. Historical names/IDs/metrics remain preserved.
- Explicit exerciseIds=[] is draft-only; omitted selections preserve legacy names. Independent review added three failing regressions and fixed metadata-only already-PUBLISHED updates and empty-ARCHIVED behavior.
- The current schema descriptor now includes defaults. Independent review reproduced all five V9 hashes over seven989-record paths; only three columns, their NOT NULL constraints and one lifecycle CHECK are added to the prior982-record inventory. No old migration or security guard was edited.
- Concurrency tests verify sorted initial exercise locks/reuse, log-versus-rename/archive blocking and gym FK-compatible NO KEY UPDATE. Backend route expectations retain the earlier endpoint set and add all16 manager endpoints.
- Frontend independent review caught object-prototype collisions in category grouping and technique fallback. Behavior regressions were observed failing, then fixed with null-prototype grouping and own-property lookup. Public mobile cards wrap maximum-length meal/schedule/exercise strings; the browser runner checks those boundary cases and __proto__ display.
- Initial complete gate passed1063 backend/44frontend/lint/build before the new browser runner's finalADMIN login expectation failed. Existing login correctly restored its protected from=library route; the runner verifies the authenticated ADMIN identity and opens Catalogs directly; a same-URL revisit retained the original return destination. No auth behavior was changed. The final full gate supersedes that failed run.
- Final reviewer found no remaining issue in the frontend fixes. Final complete release gate exited0:1063 backend,46frontend,53distinct/65executed browser groups,11gate checks, lint/build. Nine screenshots inspected. Candidate342-file code manifest unchanged during the final run. Report and release checklist updated; production remains BLOCKED.
