# FitCore production readiness — Phase 6 and security closure

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
| External delivery, provider/outbox and public production activation | BLOCKED | Disabled runtime sender; only an in-memory test sender exists; provider/domain/credentials/outbox/timing/activation approvals remain |
| Shared abuse protection | PASS locally | Atomic PostgreSQL counters, independent JVM coordination, fail-closed outage tests and rejected-peer accounting |
| Production proxy/rate/capacity policy | BLOCKED | Forwarded headers ignored/unapproved processing rejected; actual proxy topology, capacity and policy require approval |
| Browser bearer storage and CSRF/transport decision | BLOCKED | Existing local/session bearer transport preserved; XSS exposure and cookie/BFF decision remain |
| Secrets, TLS, production logging/retention/alerts | BLOCKED | App exception payload logs hardened; real secret/proxy/CORS/driver-log and monitoring evidence absent |
| Local automated candidate checks and CI definition | PASS for local fixtures | Read-only SHA-pinned GitHub workflow, fail-closed critical-test gate, no deploy step |
| Remote GitHub CI run and enforced required checks | BLOCKED | Workflow is uncommitted/unpublished; remote Linux execution and branch protection are not claimed |
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
| Full backend regression, security, PostgreSQL migration/isolation/concurrency and recovery | **912 passed, 0 failures/errors/skips** |
| Frontend Node contracts/session/theme/contrast | **35 passed** |
| Release-result checker regressions (security failure, skip, absence, zero-test suite) | **5 passed** |
| Critical Playwright scenarios | **41 distinct passed** (53 executed groups, including12 repeated transactional/security UI groups) |
| Frontend lint / production build | **PASS / PASS** |
| Workflow YAML, full action SHA pins and read-only permissions | **PASS locally**; no remote hosted run claimed |
| Shell syntax, diff whitespace, preserved-source fingerprints | **PASS** |

Browser breakdown:8 transactional membership/booking,10 existing role/module workflows,3 two-gym analytics/real CSV,13 owner/invitation/session/ADMIN,3 gate-off and4 new security UI/session-race scenarios. See the security-closure report for the isolated delayed-response interception scope. Runtime-error/network collectors in the owner/module/isolation suites reported zero uncaught errors and zero external requests. Both owned harness invocations verified process/port/container cleanup. Browser captures are under ignored `output/playwright/phase6-2026-11-01/` (desktop light/dark, mobile owner/invitations and unavailable onboarding).

Recovery ran four real custom-archive round trips (V2,V4,V5,V6) into separate disposable containers plus one corrupt-archive rejection case. It compared all table-row/sequence fingerprints, preserved historical membership overlaps/disabled administrator state/UUIDs/credentials, advanced the restored schema throughV7, checked scoped SQL reads, restored restricted ACLs and checked runtime grants and representative constraint/trigger/privilege drift. Full HTTP/browser workflows use separate fresh two-gym fixtures; production backups and application recovery against a real restored database remain untested and BLOCKED.

The latest security-closure gate passed912 backend/35frontend/41browser checks; the Phase6 baseline was891/31/37 and was preserved. The final verification uses local uncommitted source based on cf407f0; there is no final release commit or remotely verified artifact. A future release must re-run required checks on its exact approved commit/artifacts.

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
