# FitCore release readiness

## Current decision — AWS portfolio demo

FitCore is a personal portfolio project. The approved product scope is a separate public demo with fictional data. The owner has a $100 AWS credit and has reserved approval of the account, region, domain, resources and spend. The former commercial release assessment remains in the historical record. [AWS portfolio deployment plan](aws-portfolio-deployment.md)

| Item | Status and next requirement |
| --- | --- |
| Frontend preview | **Locally verified.** A separate React/Vite build serves two read-only fictional gym workspaces through Caddy. It has no API, login or browser session state. Preview-only deployment commands are in the plan. |
| Two-gym fixture | **Locally verified.** The shared fixture describes owners, trainers, members, plans, bookings and exercises. The local test recreated both gyms in fresh disposable schemas, compared their aggregates and rejected a cross-gym trainer assignment. |
| Portfolio backend startup | **Blocked.** The disposable-only guard remains active. A local policy model accepts a bounded synthetic authorization and rejects wrong targets, stale generations, durable storage and exposed services. The guarded application still rejects portfolio settings. Approve and implement the dedicated demo launcher and external host witness before backend startup. |
| Hosted cookie gateway | **Blocked for full stack.** Review the exact public origin, private TLS peers, trusted proxy and signed client address. Retain HttpOnly cookie ordering, CSRF, nonce replay checks, tenant scope and server-side aggregation. Keep the BFF, backend and PostgreSQL ports internal. |
| Demo access and reset | **Blocked for full stack.** Implement operator-issued, short-lived member access and enforce limits. The reset must verify host, stack and generation, fence writers, replace only the synthetic store and invalidate old sessions. Approval covers exact commands and the test rehearsal before exposure. |
| AWS eligibility and cost | **Owner approval required.** Public pricing is $7/month for the 1 GB preview and about $24–$28/month for a 4 GB demo with the documented storage assumptions. Verify account-specific credits, region, bundle, tax and expiry. Configure spend alerts and an explicit deletion date. |
| Public deployment | **Awaiting owner approval.** Confirm the independent AWS account or profile, regional price, new subdomain, SSH allowlist and cutoff date before using any provisioning commands. Public signup, external email, webhooks and production data are outside the demo scope. Review monitoring, key custody, and browser support on the actual demo before full-stack exposure. |

The next decision is to approve the **static preview's $7/month Lightsail scope**, including the AWS profile, region, new subdomain, SSH CIDR, alerts and deletion date. Full-stack startup needs its own approval of the synthetic-only launcher, gateway and reset contract.

### Portfolio verification

The preview archive served from a fixed, read-only mount over local HTTPS. Its six browser checks passed, covering both gyms, mobile width, selection reset on reload and absence of API requests or browser session state. The HTTPS API probe returned 404. The focused backend run passed 66 tests, including 17 portfolio tests, 17 guard tests and 32 startup contract tests. [Local test and deployment details](aws-portfolio-deployment.md)

`./scripts/verify-release.sh` exited 0 on 11 November 2026. It passed **1,229 backend tests** with zero failures, errors or skips, **194 frontend tests**, **204 distinct browser checks across 248 executions**, **12 shared-backend checks** and **20 report-gate tests**, plus lint and both application builds. The test infrastructure was disposable and has been removed. The independent AWS account, regional credit and hosted configuration still require owner verification.

### Prior local verification for the SaaS candidate

The certified startup contract passed its complete local verifier on 11 November 2026. The run passed **1,212 backend tests**, including 32 startup contract cases and 17 existing guard tests, with zero failures, errors or skips. It also passed **194 frontend tests, 204 distinct browser checks across 248 executions, 12 shared-backend checks and 20 report-gate tests**, plus lint and both builds. The fixture exercised real PostgreSQL TLS, checksum and privilege drift, and a copied archive in owned disposable databases. The packaged application excludes the fixture. The production trust authority, runtime integration and real-target certification remain open. [Contract and test cases](certified-startup-contract.md#local-acceptance-evidence)

The final `./scripts/verify-release.sh` run exited 0 on 11 November 2026. It used disposable PostgreSQL and synthetic email. Before these changes, 1,172 backend tests and 151 frontend tests passed.

| Verification | Final evidence |
| --- | --- |
| Backend | 1,180 passed with zero failures, errors or skips. The eight new PostgreSQL tests cover tokenless activation, admission gates, wrong-email, expired and replayed challenges, concurrent consumption, session revocation and gym-bound invitations. |
| Frontend and report gates | 194 frontend tests and 19 report gate tests passed. Lint and both default and cookie-mode builds passed. |
| Browser | 204 distinct checks passed across 248 executions. This includes 65 legacy executions, 31 original cookie checks, 58 existing gym workflow checks, 36 checks across two new gyms, eight account-policy checks, two browser process restart checks and 48 recovery executions. |
| Recovery engines | 16 checks each passed on Chromium 156.0.8078.79, Firefox 156.0 and WebKit 26.6. Each verified that a successful sign-in from an isolated context leaves the original context fenced. The Chromium process restart test observed both session cookies absent and verified that the fence persisted. |
| Shared backend | 12 checks passed across two JVMs over authenticated loopback TLS, including single-use challenges, session revocation and shared abuse limits. |
| Integrity and cleanup | All 37 code, test and script hashes matched at the end of the full run. Before publication, a trailing blank line was removed from `CookieSessionNotice.jsx`. All 194 frontend tests and lint passed again. All 14 protected files matched the baseline. The verifier stopped its servers, closed its browser sessions and removed its disposable databases. The pre-existing local demo is still running. |

The first full run caught a race in the browser test. The test reloaded the login page before logout had settled, and the application correctly kept the browser fenced. The corrected test waits for the logout response, an acknowledged anonymous state and the absence of the authentication cookie before attempting explicit login. The full rerun passed. Browser reports are under `output/playwright/browser-spike` and the frontend and legacy reports are under `output/release`.

- Backend: `BrowserAccountController`, `BrowserSessionService`, `BrowserGatewayFilter`, `AccountSecurityService`, `GymController` and the disposable PostgreSQL security tests. No database migration changes.
- Frontend: cookie coordinator, account API, auth provider, signup/verification/recovery/login/invitation forms, accessible session guidance and exact BFF receipt routes.
- Verification: synthetic-mail test launcher, HTTPS browser harness and runner, restart/recovery checks, Node contracts and mandatory backend/browser/result gates.

The previous local candidate passed 1,172 backend executions, 151 Node tests and 166 distinct browser IDs (206 executions) on 6 November 2026, including actual browser restart with cookie loss. Those results apply to the earlier source, not this working tree. See the [bounded technical design](cookie-authentication-completion-design.md) and [prior source-publication record](release-readiness-source-publication-2026-11-06.md) for their original scope.

## Historical records — not current launch authorization

## Historical Phase 7 readiness record

Date: 2026-11-01 (Asia/Kolkata). Baseline: `cf407f0e1f9cd0c8a46054066713c2318b77dd53`, isolated `codex/multi-tenant-saas` checkout. **Overall production release: BLOCKED.** This is local preparation and verification, not release approval.

## Readiness decision

| Requirement | Status | Evidence / remaining work |
| --- | --- | --- |
| Gym/primary enabled OWNER/domain integrity | PASS for synthetic fixtures | Versioned constraints, ownership immutability and enabled-owner tests retained |
| Schema2 tenant JWT and authorization | PASS for local candidate | Server account/gym/authz/credential checks; issued-at hardening and strict expiry |
| Repository/endpoint cross-gym isolation | PASS for local candidate | Existing full bidirectional HTTP/role/reference matrix, fingerprints, repository architecture checks |
| OWNER/ADMIN management restrictions | PASS for local candidate | Protected owner and ordinary role-assignment tests; invitations only MEMBER/TRAINER |
| Frontend authoritative session and cache isolation | PASS for local candidate | Auth-me restoration, stale requests, cross-tab changes, fragment replacement and storage regressions |
| Existing business workflows | PASS for local candidate | Membership/booking concurrency, historical periods, attendance/workout/nutrition/progress/reports/CSV tests |
| Actual production schema inventory | BLOCKED | No production metadata/data access authorized or performed; prepared read-only inventory only |
| Complete approved private ownership/catalog/admin mapping | BLOCKED | No customer mapping inferred; no legacy gym invented; ownership and historical conflicts need data-owner decisions |
| Operational versioned V3 runner and target certification | BLOCKED | Still deliberately absent; current phase prepares its contract/order and synthetic rehearsal, not a production mapping implementation |
| Synthetic archive restore and reconciliation | PASS for local fixtures | Real pg_dump/pg_restore into separate owned containers at V2,V4,V5,V6, complete table/sequence fingerprints and candidate migration |
| Verified real backup, roles/ACLs, RPO/RTO | BLOCKED | No production backup inspected/restored; synthetic evidence is not production backup readiness |
| Runtime privilege/readiness contract | PASS for local fixtures; BLOCKED for production | Required DML/sequence grants, forbidden privilege/helper grants, history and DDL-contract checks; real roles/schema unverified |
| Onboarding demo gate and disabled mail boundary | PASS | Defaults remain off; real provider selection fails closed; no actual send is implemented |
| Verification and reset security implementation | PASS locally | Pending registration, hashed expiring single-use tokens, email-change revocation, reset JWT invalidation, actual PostgreSQL concurrency tests |
| Transactional outbox, encryption/templates and retries | PASS locally | V8 atomic intents, AES-GCM external key ring, leased workers, idempotent-provider crash tests, cancellation/retention controls |
| External delivery and production worker/public activation | BLOCKED | No network provider; provider/domain/credentials/key custody/outbox operations/timing/activation approvals remain |
| Shared abuse protection | PASS locally | Atomic PostgreSQL counters, independent JVM coordination, fail-closed outage tests and rejected-peer accounting |
| Production proxy/rate/capacity policy | BLOCKED | Forwarded headers ignored/unapproved processing rejected; actual proxy topology, capacity and policy require approval |
| Browser bearer storage and CSRF/transport decision | BLOCKED | Existing local/session bearer transport preserved; XSS exposure and cookie/BFF decision remain |
| Secrets, TLS, production logging/retention/alerts | BLOCKED | App exception payload logs hardened; real secret/proxy/CORS/driver-log and monitoring evidence absent |
| Local automated candidate checks and CI definition | PASS for local fixtures | Read-only SHA-pinned GitHub workflow, fail-closed critical-test gate, no deploy step |
| Remote GitHub CI run and enforced required checks | Historical BLOCKED; see current Phase9D table | Workflow was later published;45782b8 hosted verification succeeded. Required protection remains blocked and is not established by that run. |
| Production startup / release authorization | BLOCKED | Startup prohibition unchanged; no production deployment or automatic deployment configured |
| S3 ownership integration | BLOCKED / disabled | Foundation rejects S3 activation; no unowned asset access |

PASS means the described local synthetic check passed, not that a production environment was inspected. The final verification section below records the exact counts and limitations.

## Changes and findings

1. **JWT issue time:** validly signed schema2 claims with missing or future issued-at previously established sessions. Tests reproduced200 instead401. Require integer issue time, issue-before-expiry and future tolerance bounded to0..120s (default30s); negative/malformed timestamps fail, expiry remains strict. Proposed clock policy still needs production acceptance.
2. **CORS:** replace wildcard-capable origin patterns with validated exact HTTP(S) origins and disable cookie credentials for the current bearer transport. Default known origins are unchanged; production must supply approved exact HTTPS origins and remove local origins. A future cookie design must also add CSRF protection.
3. **Email boundary:** configurable `TransactionalEmailSender`, disabled-only implementation, no credentials, outbox or actual sender. Selecting a real provider prevents bean initialization until a separate implementation exists.
4. **Recovery ACL:** no-privileges archives omit REVOKEs as well as GRANTs. Reapply reviewed owner/runtime ACLs after restore. A regression proves the catalog-scoping helper is initially executable through PUBLIC after this restore mode and must be revoked before readiness succeeds.
5. **Readiness review fixes:** the initial probe missed required application grants and some tenant-integrity drift. Require the full runtime business-table/sequence contract plus a canonical PostgreSQL18 schema-security fingerprint covering columns/nullability, constraints/validation, indexes, relations, triggers/enabled state and function definitions. Tests remove a composite FK, disable immutability/owner-preservation triggers, drop ownership NOT NULL, revoke sequence access and grant schema CREATE; each must fail readiness.
6. **Canonical restore representation:** PostgreSQL18's dump/restore re-parses one V6 enum-check array cast. Exactly two reviewed schema-security hashes are accepted (fresh/upgraded and round-tripped representation), with no broad check-constraint exemption. Future versions/DDL changes require explicit contract update and inventory/rehearsal; unknown contracts fail closed.
7. **Application logging:** a regression reproduced arbitrary exception text/stack in the generic500 handler. That handler now logs exception type and matched endpoint, not the payload/throwable. Framework/driver SQL warnings may still expose row values; production log filtering/levels/retention remain a separate blocker.
8. **Health:** `/api/health` remains liveness; new minimal public GET `/api/health/ready` returns READY/NOT_READY, checks safe config/runtime/schema, and rejects tenant selectors. It returns no schema/role/customer details. Technical readiness does not waive V3, backup, onboarding or operational approvals. In current demo/migrator contexts,503 is expected.

Fresh read-only security/integration review found no new Critical/High tenant-access bypass in the examined implementation. Its two Important/P2 readiness findings were fixed with regressions. This is not a penetration test or a certification of production controls. The unresolved onboarding, migration and recovery requirements are release-critical blockers even when no local tenant bypass is found.

### Unresolved release risks

These are risks if the current gates are bypassed, not claims of an observed production incident.

| Severity | Risk | Required closure |
| --- | --- | --- |
| Critical | Inferred/incomplete ownership or an unscoped rollback could expose another gym's data | Approved inventory/V3/manifest and compatible rollback, with actual reconciliation |
| High | Enabling demo signup without verified email, secure reset/delivery and distributed throttling | Implement/test production onboarding, recovery and abuse controls before activation |
| High | An unverified real backup cannot support a reliable migration recovery | Independent actual restore/ACL/reconciliation and approved RPO/RTO |
| High | Browser-stored bearer tokens remain stealable by XSS | Approved transport/CSP/XSS mitigation or cookie/BFF/CSRF design and rollout |
| High | Actual runtime privileges/secrets/proxy/logging/monitoring are unverified | Approved environment certification; preserve startup prohibition until then |

## Verification record

Baseline: **865 backend tests passed** before changes. The complete local `./scripts/verify-release.sh` gate then exited0:

| Verification | Result |
| --- | --- |
| Full backend regression, security, PostgreSQL migration/isolation/concurrency and recovery | **941 passed, 0 failures/errors/skips** |
| Frontend Node contracts/session/theme/contrast | **35 passed** |
| Release-result checker regressions (security failure, skip, absence, zero-test suite) | **5 passed** |
| Critical Playwright scenarios | **41 distinct passed** (53 executed groups, including12 repeated transactional/security UI groups) |
| Frontend lint / production build | **PASS / PASS** |
| Workflow YAML, full action SHA pins and read-only permissions | **PASS locally**; no remote hosted run claimed |
| Shell syntax, diff whitespace, preserved-source fingerprints | **PASS** |

Browser breakdown:8 transactional membership/booking,10 existing role/module workflows,3 two-gym analytics/real CSV,13 owner/invitation/session/ADMIN,3 gate-off and4 new security UI/session-race scenarios. See the security-closure report for the isolated delayed-response interception scope. Runtime-error/network collectors in the owner/module/isolation suites reported zero uncaught errors and zero external requests. Both owned harness invocations verified process/port/container cleanup. Browser captures are under ignored `output/playwright/phase6-2026-11-01/` (desktop light/dark, mobile owner/invitations and unavailable onboarding).

Recovery ran four real custom-archive round trips (V2,V4,V5,V6) into separate disposable containers plus one corrupt-archive rejection case. It compared all table-row/sequence fingerprints, preserved historical membership overlaps/disabled administrator state/UUIDs/credentials, advanced the restored schema throughV8, checked scoped SQL reads, restored restricted ACLs and checked runtime grants and representative constraint/trigger/privilege drift. Full HTTP/browser workflows use separate fresh two-gym fixtures; production backups and application recovery against a real restored database remain untested and BLOCKED.

The latest Phase7 gate passed941 backend/35frontend/41browser checks; the security-closure gate passed912/35/41; the Phase6 baseline was891/31/37 and was preserved. The final verification uses local uncommitted source based on cf407f0; there is no final release commit or remotely verified artifact. A future release must re-run required checks on its exact approved commit/artifacts.

Commands prepared/run locally: `./scripts/verify-release.sh`, `./gym-backend/scripts/rehearse-tenant-migration.sh`, frontend Node/lint/build, Python release-result checker tests, shell syntax and workflow YAML checks. All databases are synthetic, marked, harness-owned and loopback-only. Archive files are temporary and removed; test inputs and artifacts do not contain customer data or private ownership manifests. Evidence stays in the checkout's sibling `evidence/phase6-*.log` and ignored Playwright output; source and migration fingerprints are recorded privately.

The schema-security descriptor is deliberately scoped: production inventory must also certify defaults, sequence options, RLS settings, extensions and deployment-specific ACLs; the probe is not a substitute for that inventory. The gate checker rejects skipped/failed/empty/absent critical PostgreSQL/security suites. CI uses pinned actions/CLI, read-only repository permission, no persisted checkout credential, no deployment/environment/production secret, and sequential owned verification. A failed job blocks its workflow; enforcing that job as a required branch/release check needs an authorized repository setting change. Workflow syntax/local command success is not evidence of a remote hosted runner execution.

## Exact remaining manual approvals

1. Named operator/data-owner/security/recovery decision makers and separate read-only access approval to the exact production endpoint/schema.
2. Signed schema/baseline certification and approved target engine/version; reviewed operational V3 artifact, manifest format/revision/checksum, snapshot and independently confirmed ownership/primary-owner/admin/catalog/FK decisions for every record. A disputed membership or cross-gym booking needs a specific authorized resolution.
3. Backup encryption/storage/access/retention, RPO/RTO and independent real restore/reconciliation sign-off, including owners/ACLs/roles/sequences and compatible application smoke tests.
4. Production verification/delivery/reset flows, provider/domain/credentials and encrypted outbox choice, distributed limiter/store/outage/proxy policy, owner recovery and activation plan. Actual sending and public registration require explicit authorization after implementation/tests.
5. Browser token transport/XSS mitigations or cookie/BFF/CSRF architecture; JWT clock/skew/key-rotation/legacy-token invalidation policy; TLS/exact CORS/secret management/runtime-role grants/logging/monitoring approvals.
6. Authorization to publish CI and configure required checks; successful remote verification on the exact candidate, dependency/security review and final artifact hashes.
7. Explicit production migration/maintenance/write-freeze approval, reviewed replacement of startup prohibition, exact coordinated frontend/backend deployment, smoke-test/monitoring and rollback/recovery approval. No such action is authorized by this report.

## Detailed companion documents

- [Phase7 implementation, migration, file inventory and verification](transactional-email-phase7-2026-11-02.md)
- [Provider operations and exact approvals](transactional-email-provider-operations.md)
- [Implemented security controls, workflow details and current evidence](security-blocker-closure-2026-11-01.md)
- [Read-only private V3 decision checklist](v3-private-ownership-decision-checklist.md)
- [Deployment, migration and rollback runbook](production-migration-and-recovery-runbook-2026-11-01.md)
- [Onboarding security and provider design](production-onboarding-security-design-2026-11-01.md)
- [Prepared read-only schema inventory](sql/phase6-schema-inventory.sql)
- [Local preparation plan and scope rulings](superpowers/plans/2026-11-01-phase6-release-preparation.md)

No customer records, credentials, earlier business rules, original V1/V2/V4/V5/V6 files, private remediation documents, deployment guards or other branches are changed. No commit, push, merge, production migration, service restart or deployment is part of this phase.

## File inventory

Modified: `gym-backend/src/main/java/com/fitcore/gym/controller/HealthController.java`; `exception/GlobalExceptionHandler.java`; `security/{JwtAuthenticationFilter,JwtService,SecurityConfig}.java`; `gym-backend/src/test/java/com/fitcore/gym/security/TenantSessionTest.java`; `tenancy/TenantIsolationTest.java`.

Created: `.github/workflows/verify.yml`; `scripts/{verify-release.sh,check_release_results.py,test_release_results.py}`; `gym-backend/scripts/rehearse-tenant-migration.sh`; `gym-backend/src/main/java/com/fitcore/gym/config/EmailDeliveryConfiguration.java`; `email/TransactionalEmailSender.java`; `operations/{ReleaseReadiness,SchemaContract}.java`; `gym-backend/src/test/java/com/fitcore/gym/config/EmailDeliverySafetyTest.java`; `security/{CorsPolicyTest,JwtClockPolicyTest,LoggingSafetyTest}.java`; `tenancy/TenantRecoveryTest.java`; this checklist and its four companion inventory/runbook/design/plan files linked above. Abbreviated Java paths continue under the preceding main/test `com/fitcore/gym` root.

Original SQL migrations are unchanged. Phase6 required no frontend source/business-service change; the subsequent security phase adds the verification/reset frontend and auth-only services described in its report. Membership/booking rules remain unchanged. Prior source-preservation checks covered13 migration/remediation/deployment-guard files and all matched. Evidence, synthetic archives (deleted after rehearsal), private inputs and screenshots are not added to Git.

## Phase 9B local clean-schema certification — 3 November2026

[Phase9B certification report](phase9b-schema-readiness-certification-2026-11-03.md) supersedes the historical Phase9A clean-fingerprint blocker. Fresh and fullV8-restored descriptors were independently compared and exactly certified; restricted runtime/HTTP readiness now pass with negative drift/privilege/enum/FK coverage. No migration, startup prohibition or business logic was changed. Persistent/hosted demonstration and production remain BLOCKED by their separate access/security/recovery/infrastructure/CI/deployment approvals. Local schema certification grants no persistent startup authority.

## Phase 9C gym catalogs — 4 November2026

[Phase9C implementation and verification report](phase9c-gym-catalog-management-2026-11-04.md) adds gym-derived OWNER/ADMIN catalog management for exercises, workouts and nutrition; safe archival, history/name protections, draft/publication, role-aware empty actions and responsive management UI. V9 adds only archival/publication lifecycle fields and the status CHECK; V1–V8, membership/booking services and the startup prohibition remain unchanged. The current schema contract includes defaults and requiresV9, with independently certified fresh/staged/restored shapes. Existing accounts/catalogs are not assigned or seeded.

The final `./scripts/verify-release.sh` exited0: **1063 backend,46 frontend,53 distinct browser groups (65 executed),11 release-gate checks**, lint/build, restricted-runtime readiness, migration/recovery and tenant-security regressions passed. Nine catalog screenshots were inspected. All work remains local and uncommitted. Production and persistent/hosted demonstration remain **BLOCKED**. Catalog implementation does not authorize a persistent target, public production onboarding, real mail, legacy V3 mapping, hosting changes or deployment. Phase9D can prepare the outstanding security/infrastructure decisions; their exact operator approvals and real backup/restore/hosted-CI evidence remain required.

## Phase9D-1 — local browser-session spike

The owner separately approved a **local-only** BFF/Secure HttpOnly cookie/server-session spike. See [implementation, exact fingerprints, evidence, limitations and decisions](phase9d1-secure-browser-auth-spike-2026-11-04.md). This limited approval does not approve persistent startup, hosted configuration, public onboarding or email sending. FoundationSafetyConfiguration remains unchanged; all databases are genuine owned disposable PostgreSQL18.

**Local Phase9D-1 PASS, finalized5 November2026.** `./scripts/verify-release.sh` exited0 after all scoped/final review fixes:1,117 backend executions,83 Node tests,84 distinct browser checks (96 executions:65 legacy +31 HTTPS cookie),6 shared-backend TLS checks and14 release-gate unit methods; lint and both builds pass. Required fresh/staged/restored V10 schema and restricted-runtime negative cases pass. New critical backend suites, Node coordination/BFF results and structured lifecycle/browser/shared-instance results are mandatory gates. The complete report records exact fingerprints, files, evidence and count distinctions. V1–V9 and the hard startup guard are unchanged; the original demo remains running. Native Chromium focus and actual back UI are verified; native BFCache and visibility-to-visible, other engines and all cookie-mode business forms are not universally certified.

Production remains **BLOCKED**: usable failed-login/expiry/uncertainty recovery, the approved browser matrix, external recovery epochs/account holds, clean-instance target/startup/DB-TLS certification, real backup/restore, rate-limit/proxy/key/worker operations, provider and public-onboarding approvals, remote CI/branch/security approval and release/rollback authorization are still required. The spike must not be exposed to persistent production traffic.

Source publication was subsequently authorized separately: see the [Phase9D-1 publication scope and safeguards](phase9d1-source-publication-2026-11-05.md). That instruction does not approve deployment, persistent startup or removal of any production block.
