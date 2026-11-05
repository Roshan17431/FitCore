# Phase9D-1 Secure Browser Authentication Spike Implementation Plan

> Execute locally with scoped subagent-driven development; no commits, push, merge, persistent startup, provider calls or deployment. User explicitly approved the limited spike in the Phase9D-1 attachment.

**Goal:** Demonstrate fixed-upstream HTTPS BFF, digest-backed shared sessions, framework CSRF and genuinely fenced cookie transitions against disposable PostgreSQL.
**Architecture:** Existing approved direction; conditional browser spike off by default, existing FoundationSafetyConfiguration unchanged. Legacy bearer mode explicitly isolated. Cookie delivery acknowledgement and shared pending-family fence supplement Web Locks; uncertainty never auto-unlocks or falls back.
**Tech Stack:** Existing Java17/SpringBoot4.1/SpringSecurity/JJWT/PostgreSQL18; React/Vite/Node22/jose; existing Playwright CLI.
**Spec:** ../specs/2026-11-04-independent-saas-release-design.md plus latest user Phase9D-1 attachment (higher authority).

## Global constraints / frozen wire contract

- Working checkout already isolated at45782b8; preserve uncommitted Phase9D docs, private sibling work and running local demo. Root alone runs Maven/Docker/browser harnesses.
- Only loopback HTTPS BFF/backend and harness-owned marked PostgreSQL; freshly generated test RSA/TLS keys outside Git. No V1–V9 edits; any V10 contract independently reviewed/certified.
- Property fitcore.browser.spike-enabled=false by default; enabled requires exact HTTPS loopback fitcore.browser.public-origin and synthetic SPKI PEM fitcore.browser.gateway-public-key. Existing hard persistent guard always applies.
- Browser endpoints: POST /api/browser/bootstrap (initial preauth, exact Origin + same-origin FetchMetadata + application/json + X-FitCore-Bootstrap:1; narrowly CSRF-exempt only because no token exists yet); GET /api/browser/csrf (read existing context only); POST /api/browser/login; POST /api/browser/logout; POST /api/browser/ack; GET /api/browser/state (safe transition metadata). Normal auth/me remains authoritative. No business-changing GET. Other POST/PUT/PATCH/DELETE including login/logout require Spring CsrfFilter synchronizer token.
- Cookie names __Host-fitcore_session and __Host-fitcore_preauth; Secure/HttpOnly/SameSite=Lax/Path=/, no Domain. Handles32random bytes base64url43; SHA256 digests only stored. CSRF token is not an authentication handle; framework-backed token may be stored with context. Never body/storage/log user JWT/refresh/session handle in spike mode.
- Bootstrap JSON {csrfToken, authenticated:false, transitionState:'IDLE'}; returns preauth cookie only. Login JSON {user,csrfToken,transitionState:'PENDING'} plus auth cookie; ACK JSON {acknowledged:true} no cookie; logout JSON {csrfToken,transitionState:'PENDING'} expires auth cookie AND sets fresh preauth proof cookie in the same response. GETcsrf returns {csrfToken,transitionState,authenticated}; GETstate returns only {transitionState,authenticated}; no raw handles. Login body {email,password}. NoCookie except bootstrap/login/logout allowlisted contexts.
- A family has monotonic revision and nullable pending delivery digest/kind. Login/logout retire prior family sessions and set PENDING until ACK proves the actual newly issued auth/preauth cookie (logout also requires absence of auth cookie). Subsequent transitions/protected business requests fail423 while pending; ACK/CSRF/state may inspect only their bound context. No timeout-based pending reset. Stale nonce/revision fail409/423 with noSetCookie. Replayed gateway proofs fail401.
- JWT service assertion RS256 via vetted jose/JJWT; header X-FitCore-Gateway. Claims iss='fitcore-local-bff', aud='fitcore-browser-spike', iat/exp≤30s with skew5, jti randomUUID, method, target (raw path+query), bodySha256, cookieSha256 (exact raw Cookie or empty), clientIp. Shared atomic nonce digests with120s retention; compare all fields. Node reads actual loopback peer, never caller forwarded headers. No gym/role in proof.
- Fixed origin routing: backend only approved https://127.0.0.1:<port> with test CA; URL/method allowlist, bounds1MiB/20s, no redirects/retries. Header allowlist Cookie/Origin/Fetch Metadata/CSRF/content type/bootstrap; client Authorization/internal proof/forwarded headers never trusted; tenant selector body/query retained for backend rejection. NoStore auth/API/CSV, restrictive compatible CSP; BFF server secrets never VITE.
- Frontend VITE_AUTH_TRANSPORT=cookie-spike uses relative/api and new cookie module; legacy remains default. Web Locks serializes bootstrap/login/logout/ack, nonsecret uncertain-transition marker and BroadcastChannel notifications; no auth credentials stored. Persist uncertainty and failclosed across timeout/tab closure/refresh; no UI clear-marker recovery. Server pending+ACK proves delivery before later cookie writers, WebLocks alone never establishes safety. Unsupported browser failsclosed.

### Task 1: Node BFF and contract tests

**Owner/files:** agent owns gym-frontend/bff/**, gym-frontend/scripts/bff-gateway.test.mjs, package.json/lock only for vetted jose. No React/api or backend files.
**Consumes/produces:** frozen RS256 claim/cookie/API contract above; export createBff(options) returning Node HTTPS-compatible handler, options fixed upstream, publicOrigin, privateKeyPem,caPem, deadlines/body cap. Provide start-local.mjs explicit config-file entrypoint for harness; no env-file or deployment action.
- [x] RED real mock-TLS upstream contract tests before adapter; reject route/method/URL/header injection, sizes, missing/invalid config and no replay after timeout.
- [x] Implement signed fixed-upstream forwarding, raw bytes, exact multi-SetCookie forwarding only allowlisted routes, no-store/CSP, key redaction and bounded error classification.
- [x] Node test/lint/build relevant scope, report exact results and module interface; root owns real integration.

### Task 2: Frontend cookie adapter and race contract tests

**Owner/files:** agent owns frontend src AuthContext/api/session helpers and frontend Node tests except bff-gateway.test.mjs; no package/lock/backend/harness changes.
**Consumes/produces:** endpoints/cookie delivery ACK above; api mode branch preserving old mode tests. New helper exports cookieMode, cookieApiRequest(config), browserLogin(email,password), browserLogout(),browserMe(), subscribeSessionChange(callback); agree exact service wiring in own code and tell root.
- [x] RED pure browser coordination tests: no tokens, unsupported locks, in-flight marker, pending/uncertain, late reply and no auto retry.
- [x] Implement bootstrap/login/ACK/me and logout/preauth ACK; serialized nonce/CSRF memory, fresh DB-authoritative me; no stale verification/reset cookie writes. Disable direct public registration in spike. Keep user/training caches scoped and reject stale data on session revision change.
- [x] Adapt AuthContext/api explicit mode branches, receipt-only verification/reset behavior, notices for failclosed uncertainty/unsupported browser. No bearer fallback. Run all Node/lint/build once dependencies ready.

### Task 3: Backend session/gateway tests (tests first)

**Owner/files:** test agent owns new BrowserSessionSecurityTest.java and BrowserGatewaySecurityTest.java in tenancy; no other source/tests/harness/fixtures edits, no Maven/Docker execution.
**Consumes:** frozen wire protocol, real disposable runtime fixture and synthetic RSA keys. Endpoint tests do not import absent new production classes, so root can observe meaningful HTTP RED before implementation.
- [x] Build committed two-gym/all-role verified fixtures and restricted runtime schema. Sign requests with JJWT RS256; actual stored digests inspected; Cookie MockMvc header and Cookie objects consistent.
- [x] Cover complete bootstrap/login/ACK/business/logout, CSRF/origin/fetch/header/gateway missing-invalid-replay, session fixation/expiry/hash/noJWT, password/authz/disabled/gym revocation and role/nested-catalog denials.
- [x] Cover concurrent family transitions and pending delivery lock/no auto timeout unlock; ACK exact current cookie only, stale/no-cookie/logout remaining auth failure; no ordinary GET/wrong response SetCookie. Root browser suite covers actual delayed cookie delivery.

### Task 4: Root backend/domain/schema/integration

**Files:** conditional browser security package/config/controller, minimal SecurityConfig/JwtFilter mode gating, public credential-version validator, additive V10 shared families/session/nonce schema; current runtime fixture/history/readiness/cert tests and gate checker; port-isolated legacy harness and new disposable HTTPS browser harness/runner; report.
- [x] Record baseline1063 backend/46frontend before modifications; run HTTP RED fromTask3, migration RED before V10.
- [x] Implement shared transaction-backed families/digests/ACK fence, account+gym+DB-role+credential/authz/verified revalidation, vetted CsrfFilter/token repository, exact Origin/Fetch checks, signed-request verification and replay table. No HttpSession bearer in memory and no JWT filter fallback.
- [x] Independently review migration/constraints/hash/defaults/row preservation/rollback. Certify full fresh/staged/restored current descriptors and exact hashes only after independent capture; old V1–V9 unchanged. Runtime check expands known tables/history; do not weaken old negatives.
- [x] Isolate legacy verification ports from current demo. Run dedicated HTTPS tests with two real app/BFF/browser instances where required; TLS private keys temp, backend trust explicit. Cover supported browser matrix capability; network delay, abort/tabclose, pending fence and ambiguous cookie failclosed.
- [x] Exercise existing memberships/renewal/upgrade/booking conflicts/catalog/history/role/isolation through BFF; retain all existing bearer regressions as disposable-only.
- [x] Add security-critical result gates, execute full ./scripts/verify-release.sh; exact counts, independent final review, report limitations/remainingprodblocks. Leave all uncommitted and demo/data preserved.

## Rulings / review focus

- Ruling: POST bootstrap with exact-origin/Fetch Metadata/custom JSON header is the narrow pre-CSRF admission; all subsequent unsafe methods require framework CSRF. No GET creates accounts or consumes challenges.
- Ruling: cookie-delivery ACK + persistent pending family prevents a second response writer while first headers may be delayed; no time-based unlock. If a browser cannot prove safe progress, hold rather than invent recovery or bearer fallback.
- Ruling: new HTTPS spike uses distinct ports; current HTTP local demo remains alive. Legacy verifier gets strictly loopback configurable ports/copy-only runner URL adaptation, never arbitrary DB input.
- Ruling: current production account/recovery/provider/target certification remains upcoming; spike uses verified synthetic fixtures, no new publicprod activation.

## Completion evidence — 5 November2026

Local implementation/reviews/fullgate PASS:1117 backend,83 Node,84 distinct browser/96 executions,6 shared-backend,14 gate methods. Exact report: ../../phase9d1-secure-browser-auth-spike-2026-11-04.md. Browser support claims remain limited: native Chrome focus and actual back/reloadUI, controlled persisted-pageshow path; native BFCache/visible-return and other engines are unproven. Production, usable failed-login/expiry/uncertainty recovery, persistent startup and provider/onboarding activation remain BLOCKED. No commits/push/merge/deploy.
