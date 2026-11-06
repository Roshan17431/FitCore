# Cookie authentication completion — local design boundary

5 November 2026; local verification continued on 6 November 2026. The owner requested completion of existing FitCore SaaS with one release checklist, using existing approved architecture and owned disposable infrastructure only. This document defines bounded repairs under that request; it does not approve a new cookie protocol, production configuration, resources, provider or deployment. No numbered development phase is introduced. Baseline `21a96292a292456444a4611071762109fa230dbe`; its complete hosted verification succeeded at GitHub run 41945092777. Preserve all existing work and safeguards.

## Selected approach and alternatives

Retain fixed-upstream BFF, hashed V10 session handles, shared family locks, monotonic revisions, actual-cookie ACK, Spring CSRF, verified DB identities, Web Locks and persistent uncertainty. Repair only cases where these existing primitives establish safety. This follows the owner's existing-architecture/fail-closed constraint. It is not permission to remove pending fences.

Leaving all errors permanently blocked preserves safety but fails ordinary incorrect-password usability. Replacing the cookie/device protocol might recover unknown contexts, but adds authority/order assumptions and requires a new owner/security decision; that workstream is stopped. No localStorage reset, timeout unlock, bearer fallback, forced Web Lock release or automatic replay of a cookie/business writer is acceptable.

## Bounded recoveries

### Definitive login denial

A complete bounded BFF response 401 with exact INVALID_CREDENTIALS (or controlled 403 GYM_UNAVAILABLE, or exact pre-handler 429 RATE_LIMITED before login mutation) is eligible for reconciliation, not automatic clearance. The BFF already rejects Set-Cookie on any error. While still holding the same exclusive Web Lock, read existing /browser/csrf with no bootstrap. Require IDLE, the identical pre-attempt CSRF token, unchanged authenticated flag, and unchanged local marker/revision ownership. Only then remove that owned local marker and surface the original generic denial; allow a new explicit password attempt. Any mismatch, unknown/error body, timeout, abort, parse failure, PENDING, storage failure or changed generation retains uncertainty. No cookie writer is retried and no 5xx or unknown/incomplete 429 is treated as definitive. The shared limiter must apply anew to each explicit submission; no automatic retry or precise reopening promise is introduced. Independent review confirmed the existing same-proof denial reconciliation covers this pre-handler 429 without a new protocol.

### Lost response with a received cookie

The builder check identified a necessary correlation fix: ACK of an unrelated raw-switched cookie family cannot settle the original writer. Use the existing monotonic family state as non-authorizing correlation. Extend same-origin CSRF/status metadata with familyId, row contextRevision, current familyRevision, contextKind, currentContext (unretired matching revision), and pendingKind. Never expose session/pending hashes. None of these fields authenticates a request; the opaque HttpOnly cookie, framework CSRF and backend validation remain mandatory.

Before LOGIN/LOGOUT send, store the already returned familyId and current revision together with the marker's non-secret local revision/kind. Store no CSRF, handle, password, email or user identity. Bootstrap and old markers lacking this binding remain unrecoverable after uncertainty. This is an engineering choice to bind the already approved V10 revision/ACK proof, not a new cookie/device authority or schema.

Expose an explicit Check sign-in status action. It acquires the same Web Lock with bounded queued-acquisition cancellation (never steals a live lock), then reads metadata with no bootstrap. Require exact familyId, both row/family revision equal recorded pre-attempt revision+1, currentContext, expected kind (LOGIN→AUTH, LOGOUT→PREAUTH) and valid lifetime. If PENDING, require matching pendingKind and POST existing ACK with current Cookie/CSRF; backend alone validates exact current pending digest and literal AUTH absence for logout. After complete ACK, re-read identical bound IDLE and establish fresh me/confirmed anonymous state before removing only the owned local marker.

If already IDLE, recovery is allowed only with the same exact next-revision/current-cookie proof above. This handles a lost ACK receipt: the original request's old context revision is now unambiguously retired, so it cannot write a cookie later. A same-revision IDLE read does NOT prove the old handler finished and never clears uncertainty. A different family, missing binding, unexpected revision/kind, retired/expired/old/missing cookie, changed local marker or failed proof keeps the fence. No timer or cached identity supplies authority. An old-format marker is never upgraded by guessing.

Browser restart with lost session cookies or lost bootstrap proof therefore remains blocked; a fresh isolated context is a test workaround, not an implemented production recovery protocol. Late rejected/fulfilled promises cannot ACK, clear a newer marker or install stale identity. Recovery does not retry a login/logout/business writer or issue Set-Cookie.

### Expired current handle within its family lifetime

A current-revision, unretired, IDLE handle with expired 30-minute session time but an unexpired 12-hour family/absolute lifetime can supply only non-authorizing status/CSRF. It must not authenticate or reveal a cached user. Similarly, a current IDLE handle whose account is revoked/unverified/disabled or gym unavailable may return non-authorizing reauthentication-required status/CSRF, never user data or an Authentication. Existing account() still rejects all protected requests and LOGIN-ACK for those accounts. Explicit logout may rotate it to PREAUTH using the existing lock/revision/pending/ACK protocol; it checks exact current revision and unretired status but does not require business authentication or an enabled account to revoke its own handle. A new explicit credential login then uses that preauth context. No silent renewal of AUTH or absolute lifetime occurs. LOGIN never accepts an expired handle directly.

PENDING, used/retired, unknown or absolute/family-expired handles remain barred from cookie writers. Extending/rolling a family past 12 hours or recovering noncurrent handles requires a separately reviewed owner decision; no new-family rollover is implemented here. Revoked/disabled/unverified/gym-unavailable accounts remain denied by fresh account validation. This does not waive enabled/verified/gym/role policy: only explicit sign-out is allowed from a still-current bound control context, and subsequent credentials are validated afresh. No OWNER/global-admin exception is introduced.

## Existing-architecture hardening

Use the existing shared PostgreSQL abuse component for browser bootstrap/login ingress after authenticated gateway proof. Reuse existing local500-global/50-peer-per-path/300-second ceilings, separate transaction counting,2/3-second lock/statement timeouts and fail-closed503. Do not claim these are approved production quotas. Only verified signed loopback peer is used; forwarding headers cannot supply identity. Production per-account/keyed identity/fairness/provider-capacity budgets remain owner-policy decisions. Existing verified account/gym/role/credential/authz checks stay authoritative; add transport regressions for them.

Bound gateway nonce deletion to 100 expired rows, use SKIP LOCKED and no live nonce deletion. Consumed nonces remain independent of handler rollback. Prepare a bounded internal maintenance method for used session rows only when the family is IDLE, never the pending digest or current revision; empty IDLE families may be removed. No HTTP administration endpoint, scheduler, persistent command or external worker is enabled. Retain current/expired/PENDING session-family proofs; their retention and eventual tombstone disposal depend on the unresolved recovery/restore policy. A cleanup method is not an approved production maintenance schedule.

## Browser and gym feature verification

Keep all existing 31 cookie scenarios and six shared-backend checks, extending actual same-origin HTTPS cases for wrong-password retry, received-cookie ACK reconciliation, old/missing-cookie uncertainty, expired-current logout/explicit reauthentication, tab restart and stale responses. Retain trusted native Chromium focus and honest lifecycle evidence; do not relabel controlled pageshow as native BFCache. Explore installed/locally obtainable Firefox/WebKit using test-only browser artifacts; architecture changes or unsupported behavior are reported, never bearer-fallback. No unsupported production browser matrix is silently approved.

Exercise existing catalog management, membership renewal/upgrade, booking/conflicts, nutrition, progress and attendance through actual cookie-mode UI with Gym A/B roles and data, not only raw fetch. Existing business services/data formats are not rewritten. Gate new security/UI results so they cannot be skipped. Run the complete ./scripts/verify-release.sh after implementation, all owned infrastructure cleaned and original demo preserved.

## Unified release decision

Update only docs/multi-tenant-release-checklist.md as the authoritative current checklist, retaining historical evidence below. Use COMPLETED, BLOCKED and OWNER APPROVAL REQUIRED, exact test results/files/security findings and the next actionable blocker. Include clean-target startup authority, migrator/runtime separation, DB TLS/secrets, provider/signed webhooks, supervised encrypted outbox/idempotency/recovery, proxy/abuse policy, real backup/isolated recovery, monitoring/audit/incidents, hosted CI/protection/immutable artifacts and independent infrastructure/deployment approvals. The verified published CI is completed for21a9629 only; uncommitted new work has no hosted-CI claim.

## Exact approvals still required

1. Security-reviewed protocol/UX for no-proof uncertainty, missing/retired cookies and absolute-expired family rollover. Decide whether a proven fresh isolated authentication context is a supported restriction or approve a reviewed browser/device-cookie redesign. No timeout or metadata guess can establish no late header arrival.
2. Production browser engine/version support, native BFCache/visibility behavior and acceptable lifecycle form/cache invalidation UX.
3. Production login/account/IP/keying/cardinality and fairness budgets, immutable nonce/family evidence retention, independent restore/revocation authority and supervised cleanup role/schedule.
4. Named operational authorities, exact independent targets, DB TLS/keys, provider/domain/DPA/credentials/webhooks/budgets, backup/RPO/RTO/restore sign-off, repository protection/artifact approvals and explicit deployment/opening authorization. Existing FitCore production and customer ownership/import remain excluded.
