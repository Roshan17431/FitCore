# FitCore SaaS release readiness

## Current decision — 6 November 2026

**Release: BLOCKED. Option A is the approved scope: an independent SaaS instance on an empty database, with existing FitCore production unchanged.** This is the single current release checklist. Historical records below retain their original dates/counts and do not authorize launch. Local work is on `codex/release-readiness`, based on published `21a96292a292456444a4611071762109fa230dbe`; implementation was completed uncommitted. A subsequent owner instruction authorizes publication in at most 15 commits to the default branch; see the [source-publication scope and safeguards](release-readiness-source-publication-2026-11-06.md). No production/customer access, persistent database, real email, hosting change, merge or deployment is authorized by that instruction.

The strict empty-database path applies the existing migrations through V10 without an operational V3. Legacy inventory, ownership mapping and conflict resolution are not required for Option A; any future customer-data import needs separate approval. No legacy gym or ownership assignment is invented.

### COMPLETED

| Item | Verified scope and evidence |
| --- | --- |
| Existing SaaS domain, gym isolation and roles | Bidirectional Gym A/B repository/HTTP/security regression suites preserve OWNER/ADMIN restrictions, same-gym references, account/credential/authz versions and enabled/verified-account policy. Full final counts are recorded below. |
| Memberships, bookings, V9 catalogs and history | Original business implementations and migrations V1–V10 remain unchanged. Added real cookie UI coverage exercises both gyms; existing renewal/upgrade/conflict/history and migration/recovery tests remain mandatory. |
| Bounded cookie recovery | Explicit status action can reconcile only the recorded family’s exact next revision and actually received current cookie. PENDING requires existing cookie-bound ACK; an already-ACKed exact next revision can settle a lost ACK receipt. Fresh `/me` and status revalidation precede marker removal. Same revision, other family, old/missing proof and changed marker remain held. See [bounded technical design](cookie-authentication-completion-design.md). |
| Incorrect-password usability | Complete controlled credential denial is reconciled under the same Web Lock against unchanged IDLE context/CSRF, then the owned marker clears for a new explicit attempt. Previous identity is freshly confirmed; no cached user restores authority. Exact pre-handler 429 RATE_LIMITED uses the same proof checks and allows an explicit retry; unknown/incomplete 429, 5xx and timeout responses remain fenced. |
| Expired/revoked current session sign-out | A current, unretired IDLE context inside its unchanged family/absolute lifetime may provide non-authorizing status/CSRF and explicitly log out. Protected data stays denied. Fresh login is allowed only after logout/ACK and rechecks credentials, verification, role, gym and account versions. No silent renewal or retired-handle reset. |
| Cross-tab and old-view controls | Web Locks, non-secret marker/revision, account/gym identity and lifecycle generation checks prevent queued old-view writes/late UI acceptance. Lock acquisition is bounded; a held live lock is never stolen. No cookie-mode JWT or user DTO is stored in Web Storage. |
| Shared cookie login/bootstrap admission | Existing PostgreSQL limiter counts verified gateway ingress independently of handler rollback; local 500 global/50 peer per path per 300s budgets, bounded DB waits, 429 denial and fail-closed 503 store outages. Signed loopback peer only; forwarded IP/tenant headers supply no authority. These are test/local ceilings, not approved production quotas. |
| Bounded internal cleanup | Nonces: at most 100 expired rows, SKIP LOCKED, retained live replay window and independent transaction. Sessions: at most 100 retired/noncurrent rows under IDLE family locks; current, expired-current and PENDING proofs retained. No scheduler, external cleanup route or persistent command is enabled. |
| Clean/restored schema and restricted runtime certification | Synthetic PostgreSQL 18 fresh/staged/restored V10 and negative descriptor/history/constraint/trigger/privilege tests remain fail-closed. No schema fingerprint, migration or persistent-startup guard is weakened. |
| Local candidate verification | **COMPLETED locally.** The final `./scripts/verify-release.sh` exited 0 on 6 November 2026: 1,172 backend executions, 151 frontend tests and 166 distinct browser check IDs (206 executions), with all mandatory gates passing. Exact breakdown below. |
| Previously published baseline CI | [GitHub run 41945092777](https://github.com/Roshan17431/FitCore/actions/runs/41945092777) succeeded for exact baseline `21a9629`. It does not certify these uncommitted changes or establish repository protection. |

### BLOCKED

| Launch blocker | Exact remaining requirement / dependency |
| --- | --- |
| Unproven browser recovery and family rollover | Missing/retired/unknown cookies, an uncertain writer without received-cookie proof, bootstrap uncertainty, old unbound markers and absolute/family 12h expiry cannot be repaired by the current protocol. They intentionally remain fenced. A supported fresh isolated authentication-context restriction or a separately reviewed recovery/device protocol must be selected before implementing that workstream. No timer unlock, storage reset, late-header guess or JWT fallback is acceptable. |
| Cookie-mode onboarding and recovery UI | Cookie-mode create-gym/invitation acceptance/verification/reset activation remains unavailable; legacy token-bearing login/verification endpoints are blocked in this transport. Approve a tokenless receipt/activation flow integrated with the final session protocol, then implement/test it for both gyms. Existing bearer regression success does not prove a production cookie onboarding flow. Public production onboarding remains disabled. |
| Certified persistent clean-instance startup | The disposable-only startup prohibition is active. Approved target/artifact/config/role/TLS/epoch/workload authorization and its independently reviewed implementation/certification are absent. Local `schemaReady=true` is not permission to start a persistent service. |
| Separate runtime/migrator privilege operations | Disposable restricted-runtime evidence exists; actual independent database owner/migrator/runtime identities, complete effective ACLs/role inheritance/default grants/sequence/function/history protections and secret isolation require authorized target certification. Application runtime must not run Flyway/DDL. |
| Authenticated DB TLS and protected secrets | Exact new endpoint/certificate-host verification, trusted CA/rotation, connection-pool/network boundary and restricted credential/key custody must be approved and tested. No real connection or secret was inspected or changed. |
| Real email provider and signed webhooks | Provider selection/contract, API adapter with bounded deadlines and stable idempotency, authenticated/replay-safe webhook processing, accepted-vs-delivered status, suppression/bounce/complaint handling and operational recovery remain absent. External delivery is disabled; no real provider credentials/DNS/webhooks/sends. |
| Supervised encrypted outbox and cleanup | AES-GCM durable outbox/leases/retries exist locally. External key custody/rotation/backup, supervised workers/scheduling, cleanup privileges/retention, maximum retry/idempotency horizon and ambiguous-provider-result recovery need approval and implementation/certification. No worker scheduling or external send is enabled. |
| Production gateway, proxy and abuse policy | Fixed-loopback BFF/gateway is still a local boundary. Exact hosted origin/upstream trust, authenticated workload proof, IP canonicalization/trusted proxy chain, per-account/issuer/gym/outbound fairness/cardinality/capacity budgets and outage/alert policies remain unapproved. |
| Real backup and independent recovery | An actual independent target and backup have not been certified in this work. Encrypted backup/key/ACL integrity, isolated restore with full row/sequence/schema reconciliation, session revocation/restore epoch and operator holds, approved RPO/RTO and an actual timed drill need certification. Synthetic restore tests are not a real backup certificate. |
| Monitoring, audit and incident response | Approve minimal redacted audit events, alert thresholds/destinations/on-call, dependency and SQL/framework log filtering/retention, session compromise/revocation, provider outage/key loss and restoration incident procedures. No telemetry/customer data or external messages were sent. |
| Hosted CI and immutable artifacts for final source | Only the published baseline has hosted success. Source publication is now separately authorized; final reviewed source still needs successful hosted verification of its exact commit, artifact hashes/provenance and dependency/security review. Required checks/reviews, bypass restrictions and branch protection are not certified for this release; no repository settings were changed here. |
| Browser support certification | Current browser test results demonstrate bounded local cases only. Native BFCache/session restoration/background tab behavior and exact desktop/mobile engine/version support require a reviewed support policy and evidence. Controlled `pageshow.persisted` is not a native BFCache test. |
| Independent infrastructure and controlled deployment | Named new resources, isolation from old FitCore, spending, access, startup/deployment/routing/activation approvals and monitored rollback/recovery gates are absent. No old-production cutover/import is part of Option A. |

### OWNER APPROVAL REQUIRED

1. **Next actionable blocker: choose the supported browser recovery contract.** Approve either a documented fresh isolated authentication context when proof is lost, or authorize a reviewed protocol for unknown/retired/absolute-expired contexts. The second choice requires a concrete security design before code. Also approve the tokenless onboarding/verification/invitation/reset session integration. This task does not silently choose either policy.
2. Select exact supported desktop/mobile browser versions and acceptable native tab close/restart/BFCache behavior. Accept or reject the documented fail-closed restrictions and foreground revalidation that discards unsaved views; persisted server history is preserved. Local engine checks do not make them launch-ready.
3. Name security, database, recovery and release operators. Approve the exact independent resources/budget, target identities, TLS/CA, migrator/runtime grants, startup authorization/trust/restore-epoch design and secret/key custody. No resource provisioning or persistent connection is authorized yet.
4. Select and approve a transactional email provider/domain/privacy/retention/quotas; approve credential/DNS/SPF/DKIM/DMARC/webhook setup, bounded provider/idempotency recovery policy and supervised encrypted worker/key/cleanup operations. Actual sending and public onboarding activation require separate authorization after tests.
5. Approve gateway/proxy/client-IP trust, login/account/IP/gym/provider rate budgets, identity-keying/cardinality/fairness, fail-closed outage policy, session/nonce retention and authorized maintenance schedules.
6. Approve backup encryption/retention/access and key escrow, RPO/RTO thresholds and a separately authorized isolated new-target recovery drill, including restore revocation/holds. No legacy ownership decisions are required for an empty Option A database; future import requires separate inventory/V3 approval.
7. Source publication and its verification workflow are now authorized separately. Approve monitoring/audit/privacy/alerts/on-call, incident playbooks, branch protection/reviews, artifact signing/provenance and final security review; require successful hosted checks on the final exact commit. No repository or hosting setting is changed here.
8. Only after the above evidence passes: explicitly approve independent provisioning, startup, deployment, smoke tests, monitored opening and compatible rollback. These are separate gates, not permission inferred from a passing local test suite.

### Implementation decisions and review boundaries

| Decision | Reason and cost if it needs revision |
| --- | --- |
| Retain the existing isolated worktree and create only local `codex/release-readiness` from `21a9629` | Preserves all other branches and private work; later integration must review this uncommitted branch. |
| Keep one current release checklist; store the execution ledger outside published docs | Avoids another release roadmap; the bounded technical design remains reviewable and is not launch approval. |
| Reuse existing local shared limits; defer new production account/IP/keying budgets | No unapproved rate or identity policy is invented. Production limits still require a separate policy and tests. |
| Include complete exact pre-handler `429 RATE_LIMITED` in existing login denial reconciliation | Independent review confirmed the handler cannot run on that response. Every original proof/identity check remains, each explicit retry is throttled, and incomplete/unknown responses remain fenced. |
| Kept implementation uncommitted until separate publication authorization | The review package covers the verified working tree. The later publication instruction permits source commits/push only; private review evidence is retained and operational gates remain blocked. |

### Current verification and file inventory

The final `caffeinate -i ./scripts/verify-release.sh` invocation exited **0** on **6 November 2026**. The wrapper only prevented idle sleep during local verification; it did not change system settings or application safeguards. All databases used synthetic data in owned, marked disposable PostgreSQL 18.6 containers.

| Verification | Final result |
| --- | --- |
| Backend regression, migration/restore/schema, tenant/session/security and concurrency | **1,172 executions; 0 failures, errors or skips** |
| Frontend Node session/API/BFF/coordination/restart contracts | **151 passed; 0 failures, cancellations or skips** |
| Python result-gate regressions | **19 test methods passed**, including omission of every critical Node name in raw and Node 22 JUnit formats |
| Existing browser workflows | **53 distinct IDs / 65 executions**, with enabled and disabled onboarding configurations |
| Original HTTPS cookie suite | **31 passed** |
| Extended HTTPS recovery | **14 IDs × 3 actual engines = 42 passed executions** |
| Actual cookie-mode gym UI | **58 passed**, across Gym A/B OWNER/ADMIN/MEMBER/TRAINER workflows |
| Real expired/password/role/disabled policy UI | **8 passed**, four independently staged cases for each gym |
| Actual browser-process close/restart | **2 passed**; observed cookie loss retained the uncertainty fence |
| Browser total | **166 distinct named check IDs / 206 executions**; repeated modes/engines are counted separately only in executions |
| Two separate backend JVMs over authenticated test TLS | **8 shared-state checks passed**, including replay, pending/retirement and exhausted login budget |
| Additional actual PostgreSQL fixture-guard probes | **7 passed**: six invalid conditions rejected without mutation, one valid prepared expiry applied |
| Frontend lint and default/cookie builds | **PASS** |
| Source and resource preservation | **14 protected source files byte-identical to baseline; all 36 changed code/test/config hashes unchanged during the final run**; original demo PIDs/ports retained |
| Verification cleanup | Owned verifier servers, browser processes and disposable containers removed; ports 18088/5178/18443/18444/18445 free. Original synthetic demo 18082/5175 and its pre-existing container remain. |

Backend totals count JUnit executions, including inherited session checks repeated by the new suites; they are not a claim of 1,172 unique test methods. The 151 frontend tests retain the original 83-case baseline. Browser totals name check groups, not every assertion. Shared-backend and fixture-guard checks are additional and are not included in the 206 browser executions.

Observed browser provenance: **Chromium 156.0.8078.79, Firefox 156.0 and WebKit 26.6**. The 14 recovery IDs ran in each engine; the full gym/policy/native-process matrix ran in Chromium. This is not universal desktop/mobile support certification. Native Chromium focus and actual back navigation remain verified; controlled persisted-pageshow is not native BFCache evidence. After a real Chromium process restart, both AUTH and PREAUTH cookies were actually absent, exact context-required 401 was observed, and the surviving marker kept login blocked. That is a successful fail-closed test and an unresolved usability/recovery policy, not a production-readiness pass.

Security findings addressed locally:

- Complete credential and controlled pre-handler rate denials previously stranded the browser. Reconcile only unchanged IDLE context/CSRF, family/revisions and fresh identity under the same lock; allow a new explicit attempt. No automatic writer replay.
- Expired or revoked current handles previously could not explicitly sign out. Current, unretired control proof within the original absolute/family lifetime permits logout/ACK only; protected requests remain denied, and credentials/current account policy are checked afresh.
- An unlocked storage capability probe could overwrite another tab's new revision and strand a valid pending session. Pre-lock capability checks are now read-only; a separate non-secret writeability probe runs under the granted lock. A deterministic two-real-JS-agent regression proves recovery stays bound to the intact marker.
- Cookie login/bootstrap lacked shared ingress limits and nonce deletion could be unbounded or wait on locked expired rows. Existing shared PostgreSQL budgets and bounded independent transactions now protect admission, and cleanup preserves current/PENDING evidence.
- Earlier evidence gates could omit recovery tests, mislabel a Chrome report as another engine, or classify a restart outage as missing proof. Exact critical names, actual browser type/version, cookie presence, exact statuses and bound next-revision metadata are now mandatory. Node 22's double-escaped test-name quotes receive one bounded decoding pass; all 15 exact required names remain mandatory.
- The new fixture initially queried database comments through the wrong PostgreSQL catalog and used ineffective psql quit arguments. It now uses `shobj_description`, real SQL assertions and a transaction under `ON_ERROR_STOP`. Actual wrong-marker, missing-family, unknown-scenario, wrong-gym, retired-context and missing-donor probes all failed nonzero without changing data. The complete eight-case browser policy matrix then passed.

Gym A/B isolation, nested-ID rejection, OWNER/ADMIN boundaries, verified-account policy, changed-password/role/disabled-session revocation, V9 reference/history protections and V10 negative schema/security checks all remain protected by the passing suites. Membership and booking implementations, user/exercise entities, migrations and the hard startup guard are unchanged. No operational V3, ownership manifest, legacy reassignment, new provider, real send or persistent startup is introduced. Review and automated local evidence do not replace production security/operational approval.

Local evidence: checkout sibling `../evidence/release-final-verification-complete.log` and `release-final-results.json`; supplemental `release-policy-guard-results.json` and `release-final-gate-format-verification.log`; ignored `output/release/` and `output/playwright/browser-spike/`. The earlier failed local run is retained separately and is superseded by the successful complete rerun; its fixture failure was not waived. Code review/re-review evidence remains under ignored `.superpowers/sdd/release-readiness/`. The subsequent source-publication instruction is recorded separately; no new hosted-CI success is claimed here.

### Source file inventory

The implementation changed 38 repository files: 23 modified and 15 created. The separate publication record adds one documentation file; executable source remains identical to the successful full run. Migration files V1–V10, the persistent-startup prohibition, membership/booking services and existing historical entity definitions are unchanged. No V3 or other migration is added.

| Change | File |
| --- | --- |
| Modified | [.github/workflows/verify.yml](../.github/workflows/verify.yml) |
| Modified | [docs/multi-tenant-release-checklist.md](../docs/multi-tenant-release-checklist.md) |
| Modified | [gym-backend/scripts/verify-browser-spike.sh](../gym-backend/scripts/verify-browser-spike.sh) |
| Modified | [gym-backend/src/main/java/com/fitcore/gym/security/browser/BrowserAuthController.java](../gym-backend/src/main/java/com/fitcore/gym/security/browser/BrowserAuthController.java) |
| Modified | [gym-backend/src/main/java/com/fitcore/gym/security/browser/BrowserGatewayFilter.java](../gym-backend/src/main/java/com/fitcore/gym/security/browser/BrowserGatewayFilter.java) |
| Modified | [gym-backend/src/main/java/com/fitcore/gym/security/browser/BrowserSecurityConfiguration.java](../gym-backend/src/main/java/com/fitcore/gym/security/browser/BrowserSecurityConfiguration.java) |
| Modified | [gym-backend/src/main/java/com/fitcore/gym/security/browser/BrowserSessionService.java](../gym-backend/src/main/java/com/fitcore/gym/security/browser/BrowserSessionService.java) |
| Modified | [gym-backend/src/test/java/com/fitcore/gym/tenancy/BrowserGatewaySecurityTest.java](../gym-backend/src/test/java/com/fitcore/gym/tenancy/BrowserGatewaySecurityTest.java) |
| Modified | [gym-backend/src/test/java/com/fitcore/gym/tenancy/BrowserSessionSecurityTest.java](../gym-backend/src/test/java/com/fitcore/gym/tenancy/BrowserSessionSecurityTest.java) |
| Modified | [gym-backend/src/test/java/com/fitcore/gym/tenancy/BrowserSpikeFixture.java](../gym-backend/src/test/java/com/fitcore/gym/tenancy/BrowserSpikeFixture.java) |
| Modified | [gym-frontend/scripts/browser-session.test.mjs](../gym-frontend/scripts/browser-session.test.mjs) |
| Modified | [gym-frontend/scripts/browser-spike-server.mjs](../gym-frontend/scripts/browser-spike-server.mjs) |
| Modified | [gym-frontend/scripts/browser-spike-shared.mjs](../gym-frontend/scripts/browser-spike-shared.mjs) |
| Modified | [gym-frontend/scripts/cookie-api-contract.test.mjs](../gym-frontend/scripts/cookie-api-contract.test.mjs) |
| Modified | [gym-frontend/src/context/AuthContext.jsx](../gym-frontend/src/context/AuthContext.jsx) |
| Modified | [gym-frontend/src/pages/Login.jsx](../gym-frontend/src/pages/Login.jsx) |
| Modified | [gym-frontend/src/services/api.js](../gym-frontend/src/services/api.js) |
| Modified | [gym-frontend/src/services/browser-session.js](../gym-frontend/src/services/browser-session.js) |
| Modified | [scripts/check_browser_spike_results.py](../scripts/check_browser_spike_results.py) |
| Modified | [scripts/check_release_results.py](../scripts/check_release_results.py) |
| Modified | [scripts/test_browser_spike_results.py](../scripts/test_browser_spike_results.py) |
| Modified | [scripts/test_release_results.py](../scripts/test_release_results.py) |
| Modified | [scripts/verify-release.sh](../scripts/verify-release.sh) |
| Created | [docs/cookie-authentication-completion-design.md](../docs/cookie-authentication-completion-design.md) |
| Created | [gym-backend/src/main/java/com/fitcore/gym/security/browser/BrowserSecurityMaintenance.java](../gym-backend/src/main/java/com/fitcore/gym/security/browser/BrowserSecurityMaintenance.java) |
| Created | [gym-backend/src/test/java/com/fitcore/gym/tenancy/BrowserControlRecoveryTest.java](../gym-backend/src/test/java/com/fitcore/gym/tenancy/BrowserControlRecoveryTest.java) |
| Created | [gym-backend/src/test/java/com/fitcore/gym/tenancy/BrowserIngressMaintenanceTest.java](../gym-backend/src/test/java/com/fitcore/gym/tenancy/BrowserIngressMaintenanceTest.java) |
| Created | [gym-frontend/scripts/browser-recovery.test.mjs](../gym-frontend/scripts/browser-recovery.test.mjs) |
| Created | [gym-frontend/scripts/cookie-gym-ui.pw.js](../gym-frontend/scripts/cookie-gym-ui.pw.js) |
| Created | [gym-frontend/scripts/cookie-policy-ui.pw.js](../gym-frontend/scripts/cookie-policy-ui.pw.js) |
| Created | [gym-frontend/scripts/cookie-recovery.pw.js](../gym-frontend/scripts/cookie-recovery.pw.js) |
| Created | [gym-frontend/scripts/cookie-restart-proof.mjs](../gym-frontend/scripts/cookie-restart-proof.mjs) |
| Created | [gym-frontend/scripts/cookie-restart-proof.test.mjs](../gym-frontend/scripts/cookie-restart-proof.test.mjs) |
| Created | [gym-frontend/scripts/cookie-restart.pw.js](../gym-frontend/scripts/cookie-restart.pw.js) |
| Created | [gym-frontend/scripts/fixture-request-gate.mjs](../gym-frontend/scripts/fixture-request-gate.mjs) |
| Created | [gym-frontend/scripts/fixture-request-gate.test.mjs](../gym-frontend/scripts/fixture-request-gate.test.mjs) |
| Created | [scripts/check_cookie_completion_results.py](../scripts/check_cookie_completion_results.py) |
| Created | [scripts/test_cookie_completion_results.py](../scripts/test_cookie_completion_results.py) |

Ignored local logs, structured browser/JUnit reports and review evidence are retained separately; no database archive or real credential is published.

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
