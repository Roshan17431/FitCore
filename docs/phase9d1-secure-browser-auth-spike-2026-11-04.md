# Phase9D-1 — local secure browser authentication spike

**Local spike: PASS.** Work performed 4–5 November2026. Implementation, independent reviews and the complete post-lifecycle release gate are complete. Production remains **BLOCKED**. This document is updated after final verification, not a deployment approval.

A subsequent owner instruction authorizes source commits and publication only. See the [5 November source-publication record](phase9d1-source-publication-2026-11-05.md); the no-commit statements below describe the completed implementation stage. Production and deployment remain BLOCKED.

## Scope and preservation

The owner's Phase9D-1 request approves a limited local implementation of the previously proposed same-origin BFF/server-session direction. Work remains on `codex/phase9a-clean-instance`, based on `45782b8050580036c0c9423acce90befe089ce7a`. No commit, push, merge, hosted configuration, production database access, persistent migration, real email or external infrastructure provisioning is authorized or performed. Existing Phase9D architecture documents, private sibling work and the original HTTP demo are preserved. V1–V9 and FoundationSafetyConfiguration remain byte-identical.

The new mode is explicitly `fitcore.browser.spike-enabled=true` / `VITE_AUTH_TRANSPORT=cookie-spike` in the disposable harness only. Both default to existing behavior when absent. Existing bearer regressions run on separate owned disposable databases. Registration, verification and recovery endpoints are unavailable in cookie mode: the spike does not activate production onboarding or change their legacy security/business rules.

## Implemented local architecture

The browser uses relative `/api` at one HTTPS origin. The Node BFF signs requests with pinned `jose` RS256 primitives, forwards only an explicit controller method/path allowlist to a fixed loopback HTTPS backend with an explicit test CA, filters client headers, and never retries or follows redirects. Requests/responses are capped at1MiB and upstream exchanges at20seconds. Proof binds issuer/audience, issuance/expiry, nonce, exact method/raw target/body/cookie digest and actual loopback TCP peer. Backend JJWT verifies the assertion and atomically consumes its nonce digest in PostgreSQL independently of the handler transaction. Assertions contain no user gym/role authority.

Backend sessions use32-byte SecureRandom handles; only SHA-256 digests enter PostgreSQL. AUTH and PREAUTH cookies are host-only `__Host-` cookies, Secure, HttpOnly, SameSite=Lax, Path=/ and browser-session scoped. Individual server expiry is30minutes; family/absolute expiry is12hours, without silent refresh. Fresh DB account, enabled state, verified email, gym/activity, authz version and keyed BCrypt credential version determine each protected request's TenantActor. No browser JWT or refresh token is issued and there is no bearer fallback. Existing role/record authorization remains authoritative.

Spring's CsrfFilter and synchronizer-token comparison protect unsafe requests, including login/logout/ACK. A POST-only bootstrap exception requires exact Origin, same-origin Fetch Metadata, a custom header and exact JSON media type. CSRF/state GETs read existing context and never rotate cookies or consume account challenges. Anti-replay nonce bookkeeping is security bookkeeping, not a business-changing GET. No ordinary business/read/error/verification/reset response may set authentication cookies. The BFF separately enforces the cookie-writer allowlist. Static HTTPS test hosting applies a restrictive CSP without inline/eval script permission; inline styles remain permitted for existing React styling.

## Delivery ordering and cross-tab behavior

Web Locks alone do not prove Set-Cookie ordering. Login/logout lock a shared PostgreSQL family, insert a fresh digest, retire prior rows, increment revision and enter PENDING in one transaction. Business requests and subsequent writers cannot proceed while delivery is PENDING. LOGIN ACK must present the freshly issued AUTH cookie; LOGOUT expires AUTH and sets a fresh PREAUTH receipt in the same response, and ACK must present it with literal AUTH-cookie absence. ACK never sets a cookie. There is no timeout unlock.

The browser serializes complete exchanges with a shared Web Lock, records a non-secret persistent uncertainty marker before cookie writers, and removes it only after the successful expected exchange/ACK. Timeout, tab death, late response, unsupported coordination or lost ACK fail closed. BroadcastChannel/storage notifications clear prior user/training state and remount protected content. Accepted views also revalidate on focus, visibility return and persisted pageshow; local login/logout writers suppress competing lifecycle restores. Business requests capture identity/revision at invocation, so queued old-view work cannot inherit a later gym login. Fresh me before and after business work also rejects raw-cookie or DB identity changes; this is not a claim that already-admitted backend transactions are cancelled.

The real HTTPS harness holds actual response headers or flushes cookie headers while holding the body after the backend transaction commits. It does not use Playwright route.fetch as a substitute for delayed cookie delivery. A second Node/TLS fixture exercises two independent backend processes against the same owned PostgreSQL, including shared pending state, ACK, retirement, concurrent login and replay nonce use. The test-only response-control/static wrapper and source-module bridge are local harness facilities, not deployment entrypoints.

## Additive schema and recovery implications

V10 adds `browser_session_families`, `browser_sessions` and `browser_gateway_nonces`, their validated digest/identity/expiry/pending CHECKs, composite same-gym user FK, family FK/indexes and immutable-gym trigger. No legacy account, catalog, membership or booking is assigned or rewritten. It neither supplies a V3 nor changes V1–V9. Restricted runtime requires CRUD on the three new tables; Flyway history remains SELECT-only and schema/migration ownership/DDL/helper-function grants remain excluded.

Independent review compared all8,536 descriptor records across fresh V10, full V10 restore and V2/V4/V5/V6/V8/V9 archives upgraded throughV10:1,067 per path. All989 V9 fresh structural records are unchanged, with78 additions. Six exact SHA-256 fingerprints were independently reconstructed and accepted; no production fingerprint normalization or ignored CHECK was introduced:

- Fresh: `a21ab3b9248dcb45a7e85957bb2987b6a53b521db8233fa4a6fd3b413bc8c681`
- Full restore: `c14f022fb8ed56bec51719b37b7c2c5e3ea8d194282463638e84d3d42e1af37b`
- V2/V4/V5 archive thenV10: `cd7a0c70c55f7ea4f49b5e311f321a229e4b43756b412fa84a2b2c0ffc473fd9`
- V6 archive thenV10: `83b2451155f889ce68955f4bba17fa5bf9476ae1435e2ec0814da80c4912f76d`
- V8 archive thenV10: `083367038ace44ff0908fc59212102943e2aabad68ffe4d46c94c070dd944710`
- V9 archive thenV10: `185cbd2dcb0f21ce7d1626a4947078afc9eb4c898a55f064bf0ac2dde0bbc528`

Differences are exact reviewed PostgreSQL enum-cast/search-path renderings. Defaults, constraint validity, indexes, functions, triggers, sequences, FK protections and full ordered migration history remain certified; effective privilege checks are separate from the DDL hash. Unknown/mutated/default/trigger/FK/history/role/privilege contracts fail closed.

A failed V10 transaction rolls back atomically. A committed V10 cannot be hidden by deleting Flyway history or accepting old V9 fingerprints; old binaries' readiness contracts will reject it. Binary rollback needs a compatible schema/session protocol or a separately certified restoration. Restoring populated browser tables can revive old security state. External recovery epochs, session invalidation and account fresh-authentication holds/reactivation remain future work, not certified by schema restoration alone. Never restore/activate a persistent environment using these disposable fixtures.

## Verification and review

The final **`./scripts/verify-release.sh` exited0** on the reviewed post-lifecycle source. All counts are measured from completed JUnit/Node/CLI artifacts. Baseline before source changes was1,063 backend JUnit executions and46 frontend Node tests.

| Verification | Final result |
| --- | --- |
| Backend JUnit | **1,117 passed**, zero failures/errors/skips |
| Frontend Node (including BFF/coordinator/lifecycle) | **83 passed**, zero failures/cancelled/skips |
| Browser | **84 distinct checks**,96 executions |
| Legacy bearer browser portion |53 distinct checks,65 executions across enabled/disabled runs |
| New HTTPS cookie-browser portion |**31 passed**, including trusted focus, React history/back, delayed headers/body, tab closure, timeout and queued old-view write rejection |
| Two independent backend/TLS processes |**6 shared-state checks passed** |
| Release-result gate unit methods |**14 passed** (12 backend-result +2 browser/shared-result methods; loops contain further negative subcases) |
| Frontend lint/default build/cookie build |**PASS** |
| Fresh/staged/restored V10 schema/ACL/integrity certification |**PASS**, restricted runtime and negative drift checks |

The browser total separates the12 repeated legacy transactional/security groups from distinct coverage. JUnit counts are executions, including four repeated inherited admission cases. These are technical local checks, not production authorization.

Evidence: sibling `../evidence/phase9d1-full-release-complete.log` and machine summary `../evidence/phase9d1-final-summary.json`; ignored `output/release/frontend.xml`, `legacy-enabled.json`, `legacy-disabled.json`; ignored `output/playwright/browser-spike/results.json` and `shared-instances.json`. Full review, scoped re-reviews, exact RED/GREEN logs, descriptors and fix snapshots remain in the local ignored `.superpowers/sdd/2026-11-04-phase9d1-browser-spike/` workspace; schema capture is retained at `../evidence/phase9d1-schema-capture.json`. All final review findings are addressed. O1, the nonblocking BFF cookie-set completeness observation, remains explicitly limited to the allowlist guarantee; actual backend complete emissions and ACK are verified.

Ports18088/5178/18443/18444/18445 are free; verification containers and processes are removed. The only remaining labelled FitCore container is the preserved original manual demo; its backend health and frontend respond. HEAD is unchanged at45782b8050580036c0c9423acce90befe089ce7a. All six pre-existing Phase9D architecture documents, V1–V9 and FoundationSafetyConfiguration match their preserved bytes. No commit/push/merge/deployment or production operation occurred.

Focused evidence includes initial absent-table/admission REDs; migration4+admission2 GREEN; independent schema review; restricted readiness10+recovery5 GREEN; BFF14 real mock-HTTPS contracts GREEN and reviewed. Frontend review found and corrected queued-request identity rebinding and admission invalidation with five RED/GREEN regressions. Backend raw-cookie, mandatory-claim and media-type fixes reproduced50 executions/8 failures/2 errors, then passed50/0/0/0 and independent re-review. Counts include four repeated inherited admission executions. The final lifecycle wave added four behavioral regressions (83 Node tests pass), and actual old-provider RED proved Gym2 cookies plus a trusted focus while React still showed Gym1. Fixed source passed31 cookie-browser and6 shared-backend checks, lint and mandatory-result validation, followed by independent scoped re-review. The complete final gate above independently confirms the reviewed final source; failed preliminary harness attempts are not counted as successful evidence.

The new release checker requires all new PostgreSQL security suites and actual Node JUnit evidence plus structured browser/shared-instance results. Missing, skipped, failed, empty, duplicate or incomplete critical evidence blocks it. Legacy browser results are captured per enabled/disabled invocation before logs are overwritten; repeated cases must not inflate the distinct count.

## Limits and remaining production blockers

- Only the available HeadlessChrome156 engine is locally exercised by this run. Trusted native focus and real reload/back UI are verified. No trusted persisted pageshow was observed; the persisted-pageshow-to-real-HTTPS-to-React test uses a controlled untrusted event. Native BFCache restoration and visibility-to-visible remain unproven; behavioral EventTarget tests cover those branches. Playwright focus emulation is disabled only on owned fixture pages and trusted focus is asserted. This does not establish universal native browser lifecycle certification.
- Firefox/WebKit/Safari and mobile-engine behavior are not claimed; no additional browser engines are installed by this task. Unsupported Web Locks/storage fail closed. An approved browser support matrix and real-engine runs remain required before production.
- Foreground revalidation clears protected/training state before checking authority. Even when the account is unchanged, selected workout plans and unsaved form state can be lost; this conservative spike behavior needs a reviewed usability policy before launch. Committed backend records are unaffected.
- Many cookie business checks use browser-originated API calls rather than the corresponding forms. Cookie login/owner foreground/back UI and the existing bearer UI suites are verified; all cookie-mode business forms/themes/assets are not independently certified.
- Every writer failure, including a wrong password, conservatively preserves uncertainty. Expired/retired/uncertain families have no automatic usable recovery path. A fresh isolated test browser context is an operator workaround, not a production account-recovery design. Clearing local storage alone cannot prove old cookie responses are harmless. Review safe definitive-failure and expiry recovery separately.
- Session/family/nonce retention/cleanup is not a production scheduled-worker policy. Bootstrap/login abuse budgets, external revocation/recovery authority, restored-account holds and operational monitoring need the later security milestones.
- The scoped readiness probe is not production permission. The hard persistent-database startup prohibition is unchanged. Target identity, verified DB TLS, least-privilege production roles, migration job/startup receipts/epochs, real backup/restore certification, RPO/RTO and incident recovery remain BLOCKED.
- Email provider, domain/DNS, keys/custody, webhooks, bounce/complaint handling, actual email delivery and public onboarding remain disabled/unapproved. Production proxy/client-IP/rate-limiting policy, artifact-bound gateway identity/key rotation and deployment deadlines need certification on the chosen hosting topology.
- Remote CI on the exact future commit, branch protection, dependency/security approval, signed release artifacts, deployment/smoke/rollback approvals remain outstanding. Existing dependency audit findings are not silently considered fixed by adding `jose`.

Next independently approved milestone: turn the verified fail-closed spike into a usable session lifecycle with reviewed definitive-failure/expiry/uncertainty recovery and an explicit browser support matrix, then implement the separate certified clean-instance startup mechanism. Neither milestone authorizes activation of a persistent environment or onboarding.

## Rulings I made

1. Existing linked worktree remains the isolated baseline; preserve the running HTTP demo and all existing documents. If wrong, integration/testing could target the wrong checkout; branch/HEAD/preservation checks guard that risk.
2. PENDING plus actual-cookie ACK supplements Web Locks; ambiguity never time-unlocks. If wrong, local browser authentication must remain blocked; no bearer fallback is permitted.
3. Bootstrap is POST-only with narrowly admitted pre-CSRF JSON/custom-header/origin checks; every subsequent unsafe request uses Spring CSRF. If wrong, bootstrap must remain disabled pending redesign.
4. Public registration/verification/recovery stay unavailable in the spike. Cost: no public onboarding demonstration; no accidental unreviewed cookie writer or production activation.
5. Legacy verification ports are configurable only as strict loopback ports; the full gate uses18088/5178 to preserve the running demo. Cost if wrong: verification fails; unknown processes are never killed.
6. User prohibition on commits overrides skill commit/integration steps; all changes and review/RED/GREEN evidence remain local. Agent interruption did not restart completed work; the root completed the bounded backend tests and resumed scoped review. Cost: final review examines working-tree artifacts rather than a new commit range.

7. Test-only focus-emulation corrections require a trusted Chromium event; native visibility/BFCache and other engines remain explicitly unverified. If the fixture model is wrong, browser certification must stay blocked rather than silently weakening app security.

8. Keep the local review workspace and evidence because commits are prohibited and existing work must be preserved. Cost: local disk retention; deleting it would remove the uncommitted audit record.

## Exact file inventory

Measured against the preserved pre-spike content hashes, excluding pre-existing Phase9D document changes and ignored runtime/review evidence. 52 source/test/config/document files created or modified:

- modified: `.gitignore`
- modified: `docs/multi-tenant-release-checklist.md`
- created: `docs/phase9d1-secure-browser-auth-spike-2026-11-04.md`
- created: `docs/superpowers/plans/2026-11-04-phase9d1-browser-spike.md`
- created: `gym-backend/scripts/verify-browser-spike.sh`
- modified: `gym-backend/scripts/verify-phase2-ui.sh`
- modified: `gym-backend/src/main/java/com/fitcore/gym/operations/ReleaseReadiness.java`
- modified: `gym-backend/src/main/java/com/fitcore/gym/operations/SchemaContract.java`
- modified: `gym-backend/src/main/java/com/fitcore/gym/security/JwtAuthenticationFilter.java`
- modified: `gym-backend/src/main/java/com/fitcore/gym/security/JwtService.java`
- modified: `gym-backend/src/main/java/com/fitcore/gym/security/SecurityConfig.java`
- created: `gym-backend/src/main/java/com/fitcore/gym/security/browser/BrowserAuthController.java`
- created: `gym-backend/src/main/java/com/fitcore/gym/security/browser/BrowserGatewayFilter.java`
- created: `gym-backend/src/main/java/com/fitcore/gym/security/browser/BrowserSecurityConfiguration.java`
- created: `gym-backend/src/main/java/com/fitcore/gym/security/browser/BrowserSessionService.java`
- created: `gym-backend/src/main/resources/db/migration/V10__browser_session_spike.sql`
- created: `gym-backend/src/test/java/com/fitcore/gym/tenancy/BrowserAdmissionTest.java`
- created: `gym-backend/src/test/java/com/fitcore/gym/tenancy/BrowserGatewaySecurityTest.java`
- created: `gym-backend/src/test/java/com/fitcore/gym/tenancy/BrowserSessionMigrationTest.java`
- created: `gym-backend/src/test/java/com/fitcore/gym/tenancy/BrowserSessionSecurityTest.java`
- created: `gym-backend/src/test/java/com/fitcore/gym/tenancy/BrowserSpikeFixture.java`
- modified: `gym-backend/src/test/java/com/fitcore/gym/tenancy/CatalogMigrationTest.java`
- modified: `gym-backend/src/test/java/com/fitcore/gym/tenancy/CatalogSchemaCertificationTest.java`
- modified: `gym-backend/src/test/java/com/fitcore/gym/tenancy/CleanInstanceHttpTest.java`
- modified: `gym-backend/src/test/java/com/fitcore/gym/tenancy/CleanInstanceRecoveryTest.java`
- modified: `gym-backend/src/test/java/com/fitcore/gym/tenancy/SchemaReadinessCertificationTest.java`
- modified: `gym-backend/src/test/java/com/fitcore/gym/tenancy/TenantRecoveryTest.java`
- created: `gym-frontend/bff/gateway.mjs`
- created: `gym-frontend/bff/start-local.mjs`
- modified: `gym-frontend/package-lock.json`
- modified: `gym-frontend/package.json`
- modified: `gym-frontend/scripts/api-contract.test.mjs`
- created: `gym-frontend/scripts/bff-gateway.test.mjs`
- created: `gym-frontend/scripts/browser-session.test.mjs`
- created: `gym-frontend/scripts/browser-spike-server.mjs`
- created: `gym-frontend/scripts/browser-spike-shared.mjs`
- created: `gym-frontend/scripts/browser-spike.pw.js`
- created: `gym-frontend/scripts/cookie-api-contract.test.mjs`
- modified: `gym-frontend/scripts/tenant-contract.test.mjs`
- modified: `gym-frontend/src/App.jsx`
- modified: `gym-frontend/src/context/AuthContext.jsx`
- modified: `gym-frontend/src/pages/Login.jsx`
- modified: `gym-frontend/src/pages/SecurityFlow.jsx`
- modified: `gym-frontend/src/pages/Signup.jsx`
- modified: `gym-frontend/src/services/api.js`
- created: `gym-frontend/src/services/browser-session.js`
- created: `scripts/check_browser_spike_results.py`
- modified: `scripts/check_release_results.py`
- created: `scripts/record_legacy_browser_results.py`
- created: `scripts/test_browser_spike_results.py`
- modified: `scripts/test_release_results.py`
- modified: `scripts/verify-release.sh`
