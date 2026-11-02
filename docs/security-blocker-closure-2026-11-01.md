# Onboarding and account-recovery security closure — local implementation

2026-11-01, isolated `codex/multi-tenant-saas`, base commit cf407f0 plus the preserved uncommitted Phase6 candidate. No commit, push, merge, hosting change, production connection/migration, external email, credential change or browser token-transport change was performed. **Production remains BLOCKED.** Existing Phase6 source/evidence was snapshotted outside Git before this work; relevant files were extended rather than discarded.

## Implemented workflows

### Verified owner and invitation registration

New public POST `/api/auth/registration/owner` and `/api/auth/registration/invitation` validate the existing owner/invitation account DTOs and return generic202. They are gated by `fitcore.security-flows.demo-enabled=false` by default, independently of the legacy immediate-signup demo gate. The pending record lives outside `users`/`gyms`; there is no account, gym, trainer profile or JWT before successful email verification. Passwords are BCrypt hashes, never pending plaintext. Invitation records retain only the invitation digest and frozen email; completion rechecks stored role/gym, revocation, expiry, active status and global account uniqueness.

A256-bit CSPRNG URL-safe43-character token is delivered through the existing sender boundary; only its SHA256 digest is stored. Verification tokens expire after24h. Links use a configured fixed HTTPS origin (or loopback for tests), never Host/forwarding headers; no arbitrary redirect parameter exists. POST `/api/auth/verify-email` accepts token and exact account email, not user/gym/role selectors. It locks the email identity with a transaction-scoped PostgreSQL advisory lock, locks the hash-bound challenge, checks purpose/expiry/replay, then creates the enabled OWNER/gym/primary-owner or invited member/trainer in the same transaction that consumes challenges. The JWT is signed only after commit. A valid enabled primary OWNER is never weakened to permit an unverified, disabled owner.

Successful completion consumes all outstanding challenges for the exact email and clears pending password hashes. Expired challenges are removed in bounded batches during subsequent issuance; a separately approved scheduled retention/purge policy remains an operational requirement. Global email matching preserves the existing exact-address policy (trimmed input); existing accounts/credentials are not silently case-folded or assigned to a gym.

POST `/api/auth/verification/request` also supports existing unverified accounts, returning generic202. Verification updates only the bound current account, checks enabled state/current gym/email/authzVersion and does not grant roles or move gyms. V7 leaves all historical `email_verified_at` values null. Changing an email clears its verification marker through a database trigger, covering administrator/trainer updates and controlled SQL, while V5 invalidates old sessions. A new address must verify independently.

Legacy POST `/auth/register-owner` and `/auth/register` retain the existing deliberately explicit demo contract and produce **unverified** accounts. They remain disabled when the legacy demo flag is off; secure-flow tests run with legacy signup off and prove it cannot bypass verification. Never enable the legacy demo flag for production. Production activation is not authorized or made possible by the unchanged startup prohibition.

### Password recovery

POST `/api/auth/forgot-password` now issues a15-minute RESET-purpose token for eligible current accounts when controlled security flows and delivery are enabled. Existing, missing, disabled and ineligible accounts receive the same generic202/body; delivery failure is not exposed as an account-existence response. With the security gate off it remains a generic no-send202. Token issuance does not invalidate a signed-in user's session.

POST `/api/auth/reset-password` validates the exact email, token purpose, unused/unexpired state, current UUID/gym/email/authzVersion, enabled account and active gym, and the existing BCrypt composition/length/72-byte policy. Consumption and password update are atomic. The existing V5 authorization version and keyed credential version invalidate earlier JWTs; no fresh session is silently issued. Competing challenges are consumed together. Reset cannot verify another purpose, enable an account, alter roles or gym ownership, or reactivate a suspended gym. The enabled primary-owner invariant remains enforced.

### Delivery remains disabled

Runtime `EmailDeliveryConfiguration` still implements only the disabled provider; selecting an actual provider fails startup until its implementation is separately approved. The test sender exists only in the test classpath, stores synthetic messages in process memory and has no HTTP inbox, log output or persistent file/database token storage. Issuance rolls back on sender failure while retaining generic responses; tests prove no account or pending token remains. No actual mail SDK/credentials/send is enabled.

This direct test-delivery path is not a production durable mail worker. An approved provider, sender domain, encrypted transactional outbox/retry/dead-letter design, retention, monitoring, owner recovery procedures and real delivery authorization are still required. Real-provider timing/enumeration behavior must be tested before activation; generic status/body tests are not a claim of end-to-end timing indistinguishability with a real provider.

## Shared abuse protection

Redis was evaluated as a natural atomic-counter/TTL store, but would add an unapproved mandatory service, credentials and outage boundary. This MVP instead uses the already-shared PostgreSQL database, with atomic `INSERT ... ON CONFLICT DO UPDATE` counters. All instances use database transaction time, not a client or application-host timestamp. Separate counters cover each security route's global budget500/5min and direct-peer budget50/5min, including legacy owner/invitation requests, pending signup, verification request/completion, recovery request/completion. Delivery to an email is additionally bounded to5 persisted challenges/5min under the shared identity lock.

Counter updates are separate committed transactions from account work so invalid requests still count. If either global/peer limit rejects, its counter transaction rolls back; rejected requests from a single peer cannot drain the accepted-work budget for fresh peers. Keys contain SHA256 scope digests, not raw tokens/emails/IP strings. Expiry cleanup is bounded100 rows/request and a10000-row guard fails closed; this is a fixed-window policy with boundary bursts, not a sliding-window claim. It needs production capacity and policy approval before activation.

PostgreSQL failures yield generic503 with Retry-After30; exceeded limits yield429 with Retry-After300. There is no PostgreSQL-to-process fallback. A process limiter is retained only for isolated H2 regression databases; H2 is test-scoped and absent from the application JAR. SQL lock/statement waits are bounded; production pool/connectivity and ingress budgets still require review.

Forwarded/X-Forwarded-For headers are ignored. Explicit native/framework forwarded-header processing is rejected at startup; `server.forward-headers-strategy=none` preserves direct-peer identity. Tests vary spoofed headers without escaping the peer limit. A real reverse proxy would aggregate peers until a separately reviewed trusted proxy/hop configuration is implemented; no infrastructure/header trust was guessed here.

The integration suite tests concurrent independent limiter objects using separate data sources and **two independent JVM processes** sharing only PostgreSQL. Across100 attempts, the accepted total remains50. This proves cross-process storage coordination; it is not a production load test or a claim that two complete deployed application servers were certified.

## Frontend integration

Added `/secure/create-gym`, `/secure/register`, `/verify-email` and `/reset-password`. Existing demo routes/styles/role navigation remain compatible. Secure signup reports a conditional inbox instruction and creates no frontend session. Verification may establish a session only from its committed backend response followed by authoritative `/auth/me`. Existing-account verification requires sign-in; reset requires fresh sign-in and preserves a newer unrelated session if one appeared during the request.

Secrets are captured before React StrictMode, scrubbed from the address bar and retained only in route-bound memory. Same-document replacement links are captured/scrubbed too. Leaving the route or logging out clears them; refresh requires reopening the original link. New API calls whitelist fields and are classified as public so they do not carry a prior account's bearer credential. Password confirmation/composition/byte checks, explicit gate-off/error/loading feedback and generic recovery responses are implemented. No secret goes into local/session storage, app logs or analytics.

A fresh review found and fixed a late-verification race: starting authentication only after the public request returned could replace a newer cross-tab login. The verification request now starts inside AuthContext's existing generation guard. The isolated browser regression pauses only the verification response; both gym identities and auth/me reads remain real PostgreSQL sessions. It reproduces the old failure and verifies the newer session survives. Other two-gym browser suites continue without API mocks.

## Migration and compatibility

Only new `V7__account_security_challenges.sql` is prepared: nullable users.email_verified_at, hash-only purpose/binding/expiry challenge records with indexes, shared abuse counters and expiry index, and an email-change verification-revocation trigger. V1/V2/V4/V5/V6 remain immutable. V3 is untouched and absent; no real ownership assignment or overlap resolution is implemented. New auth-table access belongs only to the auth boundary; ordinary tenant-scoped business repository rules are retained.

Readiness now requires V7, DML privileges on25 business/security tables, and updated exact schema-security fingerprints. PostgreSQL recovery tests still restore from V2/V4/V5/V6, then migrate the restored synthetic database through V7 and prove original IDs/credentials/historical periods/disabled accounts/relationships remain intact. The known PostgreSQL18 round-trip enum-cast representation still has two explicitly reviewed hashes; no constraint was exempted. Runtime schema certification is not production inventory certification.

## Verification

Final `./scripts/verify-release.sh` exited0: **912 backend tests, 0 failures/errors/skips;35 frontend tests;5 release-gate checker tests;41 distinct browser checks** (53 executed groups because8 transactional and4 security UI groups repeat in the gate-off run). Lint and production build passed. All891 existing backend cases remain present and passed. The18 account-security tests,1 store-outage test and2 proxy-configuration cases add21 backend cases. Source shell/whitespace checks also passed.

Browser breakdown:8 membership/booking,10 existing module/role,3 two-gym reports/CSV,13 owner/invitation/session/ADMIN,3 disabled-onboarding and4 new security UI/race checks. All original37 scenarios passed;3 of the4 new security UI cases use real disabled endpoints, and the isolated session-race case explicitly intercepts only the delayed verification response. The success/expiry/purpose/concurrency and actual verification/reset persistence are tested through the real backend/PG pipeline, not claimed as mail-delivered browser tests.

The final gate runs both owned PostgreSQL/browser invocations and confirms application PIDs stopped, ports free and containers removed. Real archive recovery/ACL/schema/privilege tests also pass after V7; old migrations are unchanged. Evidence is in sibling `evidence/security-closure-*.log`, JUnit results and ignored browser outputs; no tokens or private mapping inputs are published. Original891 regression cases remain present. Added backend coverage includes pending-owner creation, two independent gyms, wrong-email/purpose binding, hash-only storage, expiry/replay, concurrent verification/reset, reset JWT invalidation and other-gym session preservation, local trainer creation, foreign invitation denial/revocation, legacy verification, secure-only versus legacy gates, sender-failure rollback, address-change revocation, shared counters across objects/JVMs, rejected-peer accounting, outage503 and spoofed-proxy behavior. Frontend coverage includes secret capture, password policy, whitelisted public APIs, disabled flows and the cross-tab race.

## Production decision and remaining gates

**BLOCKED.** Production startup is still prohibited; public production onboarding and external email remain off. Required gates remain: approved private V3 mapping/inventory and operational runner; actual encrypted backup/roles/ACL restore certification and RPO/RTO; real mail provider/outbox/domain/credentials and timing/abuse validation; approved rate/cleanup/capacity/proxy configuration; browser transport/XSS/CSRF decision; secret/runtime privilege/logging/monitoring certification; remote CI execution and enforced checks on exact release artifacts; explicit migration/deployment/activation/recovery approvals. No source test substitutes for these approvals.

The separate [V3 read-only decision checklist](v3-private-ownership-decision-checklist.md) identifies exact data-owner/inventory evidence without collecting it. See the updated [release checklist](multi-tenant-release-checklist.md) and existing production migration/recovery runbook. Membership and booking services/rules were not changed.

Primary references: [PostgreSQL18 atomic ON CONFLICT behavior](https://www.postgresql.org/docs/18/sql-insert.html) and [OWASP password recovery guidance](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html). Store/policy choices and local test findings above are FitCore implementation decisions, not production certification.

## Files changed relative to the preserved Phase6 candidate

Created:

- `docs/security-blocker-closure-2026-11-01.md`
- `docs/superpowers/plans/2026-11-01-security-blocker-closure.md`
- `docs/v3-private-ownership-decision-checklist.md`
- `gym-backend/src/main/java/com/fitcore/gym/controller/AccountSecurityController.java`
- `gym-backend/src/main/java/com/fitcore/gym/security/SharedAbuseLimiter.java`
- `gym-backend/src/main/java/com/fitcore/gym/service/AccountSecurityService.java`
- `gym-backend/src/main/resources/db/migration/V7__account_security_challenges.sql`
- `gym-backend/src/test/java/com/fitcore/gym/security/SharedAbuseOutageTest.java`
- `gym-backend/src/test/java/com/fitcore/gym/tenancy/AccountSecurityTest.java`
- `gym-backend/src/test/java/com/fitcore/gym/tenancy/LimiterProcessProbe.java`
- `gym-frontend/scripts/security-integrated.pw.js`
- `gym-frontend/scripts/security-token.test.mjs`
- `gym-frontend/src/pages/SecurityFlow.jsx`
- `gym-frontend/src/utils/security-token.js`

Modified:

- `docs/multi-tenant-release-checklist.md`
- `docs/production-onboarding-security-design-2026-11-01.md`
- `gym-backend/scripts/verify-phase2-ui.sh`
- `gym-backend/src/main/java/com/fitcore/gym/config/FoundationSafetyConfiguration.java`
- `gym-backend/src/main/java/com/fitcore/gym/controller/AuthController.java`
- `gym-backend/src/main/java/com/fitcore/gym/dto/user/SessionUserDto.java`
- `gym-backend/src/main/java/com/fitcore/gym/entity/User.java`
- `gym-backend/src/main/java/com/fitcore/gym/operations/ReleaseReadiness.java`
- `gym-backend/src/main/java/com/fitcore/gym/operations/SchemaContract.java`
- `gym-backend/src/main/java/com/fitcore/gym/security/JwtAuthenticationFilter.java`
- `gym-backend/src/main/java/com/fitcore/gym/security/OnboardingRateLimit.java`
- `gym-backend/src/main/java/com/fitcore/gym/security/SecurityConfig.java`
- `gym-backend/src/main/java/com/fitcore/gym/security/TenantWriteGuard.java`
- `gym-backend/src/main/java/com/fitcore/gym/service/AuthService.java`
- `gym-backend/src/main/java/com/fitcore/gym/service/OnboardingService.java`
- `gym-backend/src/main/resources/application.properties`
- `gym-backend/src/test/java/com/fitcore/gym/config/FoundationSafetyTest.java`
- `gym-backend/src/test/java/com/fitcore/gym/tenancy/FoundationMigrationTest.java`
- `gym-backend/src/test/java/com/fitcore/gym/tenancy/TenantIsolationTest.java`
- `gym-backend/src/test/java/com/fitcore/gym/tenancy/TenantRecoveryTest.java`
- `gym-backend/src/test/java/com/fitcore/gym/tenancy/TenantRepositoryArchitectureTest.java`
- `gym-frontend/scripts/tenant-contract.test.mjs`
- `gym-frontend/src/App.jsx`
- `gym-frontend/src/context/AuthContext.jsx`
- `gym-frontend/src/main.jsx`
- `gym-frontend/src/pages/ForgotPassword.jsx`
- `gym-frontend/src/pages/Signup.jsx`
- `gym-frontend/src/services/api.js`
- `gym-frontend/src/utils/tenant-session.js`
- `scripts/check_release_results.py`
- `scripts/test_release_results.py`
- `scripts/verify-release.sh`

No prior source file was deleted. Earlier immutable migrations/private remediation/deployment-guard fingerprints all match.
