# Phase 5 — Owner dashboard and frontend integration

Date: 2026-11-01. Isolated checkout: `/Users/roshan/.codex/workspaces/fitcore-multi-tenant-saas/repository`, branch `codex/multi-tenant-saas`. Source: published Phase 4 commit `6f7efa2b2fbc75e602e7c441403804422618f4a4`. Implements Task 7 of the approved multi-tenant MVP plan and the supplied Phase 5 brief.

## Features and routes

| Route | Access | Behavior |
| --- | --- | --- |
| `/create-gym` | Public form, backend demo gate enforced | Owner identity/password/confirmation/gym name/unique slug; client validation, backend duplicate/error feedback; register then authoritative `/auth/me`, redirect `/owner` |
| `/owner` | OWNER | Trusted current gym name and member/trainer/admin counts, authorized membership catalog, management links, useful empty state |
| `/gym/settings` | OWNER | View metadata/statistics; change name only; refresh authoritative account gym name |
| `/gym/invitations` | OWNER, ADMIN | Invite MEMBER/TRAINER; status and local expiration display; confirm revocation; show/copy demo link once |
| `/register#invitationToken=…` | Public form, backend demo gate enforced | Capture secret in memory and immediately scrub fragment; exact invited email required by backend; password validation; acceptance/session/dashboard; invalid/expired/revoked/used errors |
| Existing protected routes | Existing roles; management restricted | Preserve membership, booking, workout, nutrition, progress, attendance, notifications, admin tables, analytics and CSV interfaces |

Owner dashboard uses no invented revenue, billing, or active-membership statistics. Membership catalog displays the existing authorized plan API. Gym settings cannot submit ID, slug, status, or ownership. OWNER navigation includes dashboard, settings, members, trainer management, invitations and administration. ADMIN has current-gym management and invitations, without owner settings. TRAINER and MEMBER retain their personal modules and cannot see management navigation. Existing administration hides/disables protected OWNER targets and ADMIN-to-ADMIN actions; backend remains authoritative.

## API integrations

| Backend API | Frontend use |
| --- | --- |
| `POST /api/auth/register-owner` | Whitelisted fullName, email, password, gymName, slug |
| `POST /api/auth/register` | Whitelisted account fields and in-memory invitationToken; no client role/gym ownership |
| `GET /api/auth/me` | Authoritative identity, role, gym and authorization-version metadata on login, signup and reload |
| `GET /api/gym` | Current authenticated gym metadata and statistics |
| `PATCH /api/gym` | `{name}` only |
| `GET/POST /api/gym/invitations` | Safe history; email + MEMBER/TRAINER invitation creation |
| `DELETE /api/gym/invitations/{id}` | Confirmed pending revocation |
| Existing membership, admin and personal APIs | Existing business workflows retained |

No new backend endpoint, domain service, migration, dependency, production configuration, or business-rule change was needed. The owned disposable browser harness alone accepts an opt-in demo gate command-line argument; default is false. Ordinary backend startup prohibition and onboarding restrictions remain unchanged.

## Session and secret handling

- Stored roles and gym IDs never authorize route access: protected content waits for `/auth/me`; saved profiles are overwritten by the server response.
- All protected requests include the current JWT; `/auth/me` is no longer accidentally treated as public authentication.
- Requests capture the credential they used. Responses from superseded sessions are discarded; an old failed request cannot clear a newer session.
- Current-session 401 and suspended-gym `403 GYM_UNAVAILABLE` clear authentication and training state. Ordinary permission 403 preserves the valid session and displays an error.
- Logout, account changes and cross-tab changes clear stale workout selections. Selection keys include gym ID and account ID, and selected plans must be present in the current authorized catalog.
- Gym/account/authorization-version changes remount the business-module layout and its local state. Preferences such as theme remain intact.
- Adopting a remembered account discards older per-tab credentials; cross-tab logout cannot revive an earlier gym session.
- Incoming invitation fragments are scrubbed both at initial boot before React StrictMode and on same-document link changes. Secrets live only in the acceptance context or one-time creation modal. Leaving the acceptance screen, signing out, or completing registration clears the context. Closing the creation modal discards its secret. Reloading an acceptance page intentionally requires reopening the invitation link.
- Request, response, auth-payload and registration debug logging was removed. No invitation email or email verification is claimed. Links must be copied and shared privately for controlled demos.
- Native slug validation uses the browser's Unicode-set-compatible pattern; password rules include the backend BCrypt UTF-8 byte ceiling.

## Changed files

All paths below are repository-relative.

Modified application files:

- `gym-frontend/src/App.jsx`: routes, tenant layout remount, fragment lifecycle.
- `gym-frontend/src/main.jsx`: pre-React fragment capture.
- `gym-frontend/src/context/AuthContext.jsx`: authoritative restore/authentication and session lifecycle.
- `gym-frontend/src/services/api.js`: protected bearer handling, stale-response guards, error invalidation, whitelisted gym/onboarding APIs; remove payload logging.
- `gym-frontend/src/layouts/MainLayout.jsx`: gym identity and role-aware links/search.
- `gym-frontend/src/routes/AdminRoute.jsx`: OWNER and ADMIN management access.
- `gym-frontend/src/pages/Login.jsx`: session messages and role-appropriate redirect.
- `gym-frontend/src/pages/Register.jsx`: shared invitation signup integration.
- `gym-frontend/src/pages/Admin.jsx`: role/target-aware management actions and role options.
- `gym-frontend/src/pages/Workouts.jsx`: gym/account selection scope and catalog validation.

New application files:

- `gym-frontend/src/pages/{Signup,CreateGym,GymWorkspace,Invitations}.jsx`.
- `gym-frontend/src/routes/OwnerRoute.jsx`.
- `gym-frontend/src/utils/tenant-session.js`.

Verification and documentation:

- `gym-frontend/scripts/tenant-contract.test.mjs`: real Axios adapter contracts, payload whitelists, current/old session response behavior and native slug validation.
- `gym-frontend/scripts/tenant-session.test.mjs`: fragment, signup, management policy, storage scoping and credential precedence.
- `gym-frontend/scripts/owner-integrated.pw.js`: real browser owner/manager/invitation/session coverage.
- `gym-frontend/scripts/onboarding-disabled.pw.js`: gate-off UI/API verification.
- `gym-backend/scripts/verify-phase2-ui.sh`: owned local-only gate selection and new browser suites, retaining cleanup and final persisted business invariants.
- This report.

## Verification

Verification uses generated synthetic accounts and explicitly seeded fixtures in marked, disposable loopback PostgreSQL containers. No real customer database, `.env` credentials, production API or persistent migration is used. The harness checks local ports, owns its server processes/container/browser, checks persisted final membership/session counts, and removes its resources on success and failure.

Fresh backend command: `cd gym-backend && ./scripts/verify-tenancy.sh` — **865 passed; 0 failed, 0 errors, 0 skipped**, packaged successfully.

Frontend command: `node --test gym-frontend/scripts/*.test.mjs` — **31 passed; 0 failed**. `npm run lint` and `npm run build` in `gym-frontend` pass. No lint warnings remain.

Browser verification passed:

| Suite | Passing checks |
| --- | ---: |
| Existing transactional membership/booking browser suite | 8 |
| Existing Phase 3 module/role browser suite | 10 |
| Two-gym reporting and real CSV isolation | 3 |
| New owner/invitation/session/ADMIN browser suite | 13 |
| Gate-disabled registration and existing-owner access | 3 |
| **Distinct browser checks** | **37** |

Gate-enabled command: `FITCORE_VERIFY_PHASE3_UI=true FITCORE_VERIFY_OWNER_UI=true ./gym-backend/scripts/verify-phase2-ui.sh` — 34 checks, final PostgreSQL business invariants and cleanup passed. Gate-disabled command: `FITCORE_VERIFY_PHASE3_UI=false FITCORE_VERIFY_OWNER_UI=false ./gym-backend/scripts/verify-phase2-ui.sh` — 3 additional checks plus the same 8 transactional checks repeated; final database invariants and cleanup passed. Thus 45 check groups executed across the two final runs, covering 37 distinct cases. The 13-check owner suite, 10-check module suite and 3-check CSV suite reported zero uncaught browser errors and zero external requests. Backend requests used loopback only. Both runs verified owned processes stopped, ports 18082/5175 free and the disposable database container removed.

The final review regressions pass: a replacement fragment is removed without reload, a superseded per-tab identity does not reappear after cross-tab logout, and the native slug pattern compiles with Unicode-set semantics. An intermediate ADMIN test failed because its immediate option query raced React initialization; waiting for the role control resolved the test without changing authorization behavior.

Regression coverage includes actual membership purchase/renewal/upgrade and renewal race (one 200/one 409), retained cancelled history, trainer booking/duplicate/overlap/adjacency with persisted sessions, nutrition/progress writes, attendance, keyboard search, all four roles, actual admin edit, gym-specific attendance analytics and real CSV generation.

New coverage includes independent owner registration; correct member/trainer invite acceptance/profile; owner name persistence; invalid/reused/revoked links; duplicate email/slug feedback; stored role/gym spoof rejection; foreign URL 404; member management denial; restricted ADMIN actions; old-response/current-session error tests; cross-tab identity switch/logout; fragment replacement; light/dark desktop and mobile layout/navigation without horizontal overflow; gate-off registration rejection without a session.

Fresh-context whole-change review found two P2 issues (cross-tab credential fallback and same-document invitation capture); both were addressed with regressions. Red evidence was recorded for missing API contracts, slug browser pattern, credential precedence and replacement-fragment behavior before fixes. Evidence resides outside Git in the checkout's sibling `evidence/phase5-*.log` files; browser logs and screenshots are ignored under `output/playwright/`. Private remediation documents and previous evidence are preserved.

## Screenshots

Visually inspect the final images in the isolated repository's `output/playwright/`:

- `phase5-owner-desktop-light.png`
- `phase5-owner-desktop-dark.png`
- `phase5-owner-mobile-dark.png`
- `phase5-invitations-mobile.png` (stable empty state with an existing owner while onboarding is disabled)
- `phase5-onboarding-disabled.png` (explicit public-onboarding-unavailable feedback)

## Remaining limitations and Phase 6 readiness

This is a local demo implementation, not production release authorization. No commits, pushes, merges, deployments, production configuration changes, production SQL, real-data migration, or production service restarts are part of this phase.

Production preparation still requires the approved legacy account/gym/catalog ownership mapping and membership-overlap decisions, administrator migration decisions, backup/recovery and migration rehearsal/reconciliation, secure owner onboarding and recovery with email verification/delivery/rate limits, release/rollback and CI gates, and a separate decision about replacing browser-stored bearer tokens. Existing credentials-version and authorization-version backend invalidation remain intact; a stale role is rejected by the backend and clears the frontend session on the next API request. There is no live push-based revocation channel.

Newly created gyms start with empty catalogs. Owners use existing authorized management to create plans. One account per gym, globally unique emails, no multi-gym staff, payments or SaaS billing remain the approved MVP limits. Invitations are controlled demo links with no delivery service; an acceptance-page reload loses the memory-only secret and requires reopening the original link. Browser checks use Chromium on representative desktop/mobile sizes; they are not an exhaustive device or screen-reader certification.

No known failing checks remain at handoff. Phase 5 establishes the frontend integration needed for Phase 6 production preparation. Startup prohibition and the production onboarding gate must remain active until the separate production requirements and approvals are met.
