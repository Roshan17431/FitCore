# Phase 9D — proposed implementation milestones

4 November2026. **Approval checklist, not permission to execute.** Depends on the [browser/startup design](superpowers/specs/2026-11-04-independent-saas-release-design.md), [email/abuse design](phase9d-email-and-abuse-architecture.md), [configuration](phase9d-infrastructure-configuration.md) and [release gates](phase9d-recovery-and-release-certification.md). Once approved, create component-sized implementation plans with exact interfaces/tests before coding. No source/test/infrastructure implementation occurs in this architecture phase.

## Fixed constraints for every milestone

- Start from verified45782b8 or a separately verified descendant in an isolated codex worktree; preserve other branches/private evidence. No unapproved commit/push/merge/deploy.
- No old production connection/data/resource/credential/hosting/DNS access. Only synthetic fixtures and genuinely harness-owned disposable PostgreSQL18/loopback mock services for local work; no spoofed markers or real recipients.
- Keep current persistent startup prohibition until an independently approved activation integration/release; no generic flag bypass. No legacyV3 or editsV1–V9. New schema changes are additive, reviewed and independently certified.
- Preserve current JWT credential/authz/disabled-account protections, tenant actor/roles, secure token consumption/outbox, V9 catalog semantics, membership/booking business/concurrency rules and both gyms' history.
- Any source/test change runs complete `./scripts/verify-release.sh`, including critical-suite result checks, frontend/lint/build/browser workflows. Record actual counts; never replace mandatory regressions with guessed targets. Revisit browser drivers that inspect storage because the intentional transport contract changes.

## D0 — approve boundary and testable contracts

Dependencies: owner architecture decisions G0; no provisioning. Deliverable: accepted A/B decision, session/CSRF/proxy topology, verified-login/trainer creation/password/invitation response policy, target trust/permit/restore policy and provider uncertainty budgets. Exact operators/resources may remain unassigned **only** while their operational gates remain closed.

- [ ] Review the current-source gaps and choose recommended A or document B risk acceptance.
- [ ] Approve production account creation/recovery behavior and no global ADMIN/OWNER bypass.
- [ ] Select authenticated PostgreSQL trust topology or keep persistent integration blocked; name approval/key custody design and recovery epoch authority.
- [ ] Accept/refine session/abuse/provider/RPO/RTO proposals; no implied acceptance of operational numbers.
- [ ] Write separate local plans for D1–D7; explicit scope and interfaces prevent one large “production-ready” change.

Risk: implementing a cookie header without full CSRF/session/gateway behavior, or calling a new environment variable approval. Acceptance: independent design review identifies no unresolved **local implementation** decision that could change authority boundaries. A documented operational gate can remain blocked without inventing a value.

## D1 — browser security seam and gateway, local only

Dependencies: D0 browser choice. Candidate code boundaries: frontend AuthContext/API/auth pages and route/session utilities; new `gym-frontend/api` BFF entrypoints and reusable gateway module; backend SecurityConfig/auth controllers plus a new browser-session security package. D1 also owns its additive session/gateway-nonce migration(s) afterV9, fixture/history updates, complete descriptor certification and corresponding web-runtime grants/readiness. Reuse TenantAccess/account validation; no business-service rewrites.

- [ ] First add failing cookie/CSRF/session/gateway/browser contracts, including the explicit cookie-writer allowlist, verification-for-A versus login-B receipt-only behavior, unknown/stale session and auth response without user bearer; keep explicit disposable bearer regression fixtures.
- [ ] Implement fixed-origin no-store BFF request/Set-Cookie/CSV forwarding, reviewed signed gateway assertion, shared nonce rejection and strict canonicalization/body/time limits. No open proxy/direct Render bypass.
- [ ] Implement opaque digest-backed shared sessions and framework CSRF, fixation protection, logout, expiry/account-version/disabled/gym/verified-state rechecks. Choose persistence/library through an isolated spike that proves digest-only, primitive attributes and no unsafe serialization; failing the contract requires review. Re-certify D1’s new schema independently and update exact version/role contracts and all current ORM/runtime fixtures before claiming the full gate passes.
- [ ] Adapt frontend same-origin API, CSRF bootstrap, authoritative me, non-secret cross-tab invalidation/generation, Web Locks authentication-transition serialization and no browser credential storage. Certify the supported-browser matrix; unsupported/indeterminate ordering fails closed without bearer fallback. Normal/bootstrap/verification/reset/stale replies cannot mutate the authenticated cookie; only serialized explicit login/logout may do so. Clear stale training identity; remove automatic persistence/remember until approved.
- [ ] Test Chrome and a second supported browser where available: login/logout/reset/role/gym changes, email-link GET with no consumption, CSRF from malicious/sibling origin, XSS-readable storage absence, cached-error/Set-Cookie isolation, no automatic re-login on verification, delayed replies after a newer login, concurrent login/logout, timeout/abort/tab-crash and unsupported-browser transition handling.
- [ ] Run GymA/B catalog/history, membership renewal/upgrade and booking race flows through BFF→API→disposablePG, including ambiguous timeout without automatic write replay. Enforce/test CSP/header compatibility with current charts/styles/images and all themes.

Risk: stale cookie response restores an old account, incomplete CSRF on public auth, spoofed proxy identity or caching user data. Acceptance: transport-specific negative matrix plus all existing business/isolation outcomes pass; production mode remains unavailable.

## D2 — inert certified-target policy and complete runtime certification

Dependencies: D0 signed permit/TLS contracts; D1’s accepted session/gateway/schema interfaces. Pure signature/configuration policy tests can proceed independently of UI, but integrated certification uses D1’s approved current schema, not a staleV9 fingerprint.

Candidate boundaries: a new immutable release/target policy package beside operations, configuration binding tests, ReleaseReadiness and separate web/worker privilege contracts. FoundationSafetyConfiguration remains active; pure policy tests use injected recording connectors/test signing keys and never make the application accept a persistent DB.

- [ ] Add preconnect tests for wrong/old/multi-host/alias targets, invalid signature/expiry/replay, changed secret version/artifact/config, every Spring/Hikari/Flyway/JNDI/profile/import/CLI/env override and late rebinding. Assert zero DB attempts for these failures.
- [ ] Model sealed descriptor and external workload/epoch/lease authority with synthetic receipts; forged approval cannot supply its own trust root. Wrong TLS peer test proves no DB credentials/SQL are released, rather than claiming no TCP attempt. Resolve workload bootstrap/delivery of the authority lease before integrated activation; provider environment labels alone are not proof.
- [ ] Exercise post-connect identity/history/checksum/type/script/ACL/owner/default ACL/sequence checks directly against disposable PG and genuine restricted logins, including a correct schema on the wrong synthetic target and pool schema/credential/reconnect drift.
- [ ] Test runtime lease expiry/revocation/renewal outage and separate worker roles; direct business work/claims fail by expiry, not merely the HTTP probe. Keep existing safe-mode/readiness checks and acceptedV9 contracts.

Risk: checking one connection while another pool uses a different target/user; trusted configuration controlled by the purported permit; restored copied identity revives authority. Acceptance: independently reviewed inert policy with all negative tests; current persistent prohibition still rejects a real/ordinary-local target. Later activation is G6, not an automatic result of passing D2.

## D3 — unified verified accounts and durable email envelope

Dependencies: D0 account policy; D1 session revocation seam and D2 schema contract interfaces.

Candidate boundaries: AccountSecurityService, OnboardingService, AdminService/TrainerUpsertRequest, email outbox/envelope/receipts and **new** migration(s) afterV9.

- [ ] Add failing verified-login and authentication-epoch/hold cases for manager-created trainers, email changes, disabled/gym states and every password-writing path; require common BCrypt-compatible policy and preserve profile/history/OWNER constraints.
- [ ] Implement production verified activation semantics, account authentication epoch/hold record and purpose-bound recovery receipt verification, and invitation receipt contract behind disabled, separately authorized production feature mode; leave demo flags off and manual secret exposure disposable-only.
- [ ] Add frozen sender/provider/account/body identity, durable first attempt/deadline/receipt/quarantine state and safe existing-row mapping for **synthetic**V8/V9 and latest-D1 fixtures. Assign the next unused migration version after D1; do not independently reserve the same version. Existing UUID/history rows remain intact; unknown previously attempted rows start held, never inferred unattempted.
- [ ] Re-certify fresh/staged/restored descriptor, migration checksum/history, new status/FKs/uniqueness and expanded web/worker/cleanup grants. Do not loosen the26-table web readiness merely to fit new roles.

Risk: unverified alternate account creation, losing invitation bindings, editing old migrations or fresh-key replay during schema transition. Acceptance: two-gym and atomic rollback tests pass, old records preserved and all new fingerprints independently reviewed before acceptance. No actual provider is configured.

## D4 — mock-tested provider adapter and webhook state machine

Dependencies: D3 envelope/receipt schema; approved candidate provider contract/DPA decision for implementation scope only.

- [ ] Build loopback TLS/mock provider fixtures for3s/15s finite budgets, stable frozen requests, valid acceptance, response bound,409 variants,429/5xx/config fault, cancellation/late response, crash-after-acceptance and stale lease.
- [ ] Implement adapter with no hidden retries/redirect/failover. Dedup deadline is immutable≤security expiry and approved provider horizon; beyond-horizon/unknown restore work is quarantined.
- [ ] Add raw-body webhook signature/time/key-rotation/durable unique-event inbox/correlation tests and out-of-order delivery/bounce/complaint/suppression transitions. Webhook never verifies an account or selects tenant authority.
- [ ] Test redacted logs, encrypted payload/version rotation, known/unknown recipient behavior and key-missing preflight with no destructive worker claim. Freeze From/Reply-To/template/account configuration per intent.

Risk: provider acceptance confused with delivery, signature verified after JSON mutation, duplicate sends after unknown outcome or receiving fake cross-gym events. Acceptance: mock provider/clock demonstrates exact bounds and durable idempotence **only inside** provider guarantee. Credentials/DNS/webhook registration/real egress still absent.

## D5 — shared proxy, login and outbound abuse enforcement

Dependencies: D1 authenticated client-identity seam; D3/D4 issuance and provider capacity interfaces.

- [ ] Add two-application-process tests for signed gateway identity and spoofed/multiple XFF/Forwarded/Origin/tenant/service headers; no global forwarding strategy change. Validate IPv4/IPv6/mapped/NAT behavior and gateway nonce sharing.
- [ ] Add atomic action/IP/address/account/issuer/gym/global/provider budgets, including current unthrottled login and manager invitation issuance. Commit admitted ingress/failed-auth attempt counters independently of handler success; test invalid credentials and invalid tokens cannot roll their counters back. Keep accepted invitation/intent reservations atomic with issuance and rejected-admission global accounting separate. Caller gym/role never controls scope. Unknown accounts traverse the same public budget path; timing/privacy results are measured rather than called perfectly constant-time.
- [ ] Exercise boundary times, rejected-peer budget rollback, HMAC key rotation overlap, concurrent reservations, hostile cardinality and fair recovery capacity. Run PG outage, pool exhaustion and lock contention; return bounded503/no local fallback.
- [ ] Load-test PostgreSQL hot counters/cleanup at approved estimated peak and burst across instances, with pool capacity/headroom and no effect on booking/membership concurrency. A failed load target reopens the store/capacity decision; Redis is not silently added or limits removed.

Risk: proxy-NAT lockout, forged IP/gym, noisy tenant starvation, CPU-heavy login attacks or unbounded counter storage. Acceptance: adverse budgets and latency/capacity evidence before any public registration/sending gate.

## D6 — supervised worker and independent maintenance, local only

Dependencies: D2 role/lease and D3–D5 receipt/keys/quotas.

- [ ] Add an explicit worker entrypoint/supervisor contract with mocked provider and disabled defaults; no web-service scheduler hidden behind an app startup flag.
- [ ] Test poll/concurrency caps, SIGTERM/drain/deadline, crash/reclaim, stale receipt fencing, lease/key/quota/circuit failure and no claims after expiry.
- [ ] Add separate bounded cleanup command/lease/schedule contract; test expiry/retention with delivery disabled, role boundaries and historical-key/backup retention protection.
- [ ] Validate new-only infrastructure definitions/staging inputs and secret-name registry statically; no tool creates a service or enables auto-deploy.

Risk: duplicate workers from scaled web instances, retention silently not running, missing-key ciphertext destruction or external quota overspend. Acceptance: disposable lifecycle tests and zero real egress; worker/maintenance defaults remain off.

## D7 — synthetic recovery/rollback and release evidence

Dependencies: D1–D6 completed/reviewed and full local gate.

- [ ] Backup/restore disposable multi-gym fixtures containing V9 archives, historical memberships/bookings, pending/consumed challenges, sessions, encrypted queue/receipts and out-of-order events. Reconcile rows/collections/sequence options+state/owners/ACLs/checksums and both role contracts.
- [ ] Test that a snapshot cannot revive pre-reset/pre-role-change/disabled sessions or consumed links, or authorize a new login using an old password/restored enabled/role/email/verified state. Rotate external recovery epoch and invalidate restored sessions/challenges/invitations before service. Apply the separate all-restored-account authentication hold; independently confirmed current-state/credential recovery and a signed per-account/current-epoch reactivation receipt are required for fresh login. Test that ordinary password reset, email changes and ADMIN role updates cannot clear this hold, and that valid release/gateway signatures cannot substitute for account-reactivation receipts. Missing external evidence keeps the account held; neither disabling primary OWNER nor ordinary ADMIN bypass is permitted.
- [ ] Model provider acceptance after backup and beyond TTL; restore stays quarantined until receipt/suppression/key reconciliation. Missing external evidence must never auto-resubmit. Distinguish recovery of old AES decryption keys from retirement of old JWT signing keys.
- [ ] Produce exact candidate hashes, migration/descriptor evidence, full actual test counts, local SLO/recovery timings, static config/secret/log review and independent security review. Rehearse compatible rollback without schema downgrade or redirect to old FitCore.

Risk: technical schemaReady treated as authority, restored wrong target accepted, old access/mail replay or irreversible data loss. Acceptance: complete synthetic evidence package; **actual** new-service RPO/RTO/backup certification and deployment remain BLOCKED.

## D8 — separately authorized operational work

This is not local implementation and is not authorized now. Follow G2–G8 in the recovery/release document: source/CI/protection changes if requested; provision new resources only; explicit empty-target migration; real isolated restore/key/ACL/RPO/RTO certificate; independent workload/startup permit; matched artifact deployment/restricted smoke; provider/domain/controlled send; verified first-owner cohort and public opening. Each decision states exact target/operator/artifact/time/scope and rollback condition.

The owner can approve local D1 implementation while operational identities remain unresolved, but only after the browser/account/gateway design is accepted. No “all phases approved” inference bypasses a later target, backup, email or deployment gate.
