# Phase 9D — transactional email, proxy and shared-abuse architecture

4 November2026. **Proposed controls for approval.** Baseline45782b8; no real provider, DNS, credentials, sending or networking changes. Existing provider/scheduler defaults remain disabled and persistent startup prohibited. See the [assessment](phase9d-independent-saas-assessment-2026-11-04.md) for verified source references and the [configuration registry](phase9d-infrastructure-configuration.md) for current versus proposed keys.

## Preserve what exists

Keep the TransactionalEmailSender/EmailProvider seam,32-byte hashed single-use challenges, tenant-bound invitations, challenge/intent atomicity, AES-256-GCM with external key IDs and authenticated row bindings, terminal secret wiping, leased claims/fencing and disabled local provider. Existing outbox payload is encrypted recipient/subject/text/HTML; current sender identity is not frozen, acceptance reference is discarded, retries have no elapsed-time horizon and no supervisor calls worker/cleanup. Do not describe5 attempts as exactly-once delivery.

Current120s lease and30/60/120/240s delays are verified source. Missing/corrupt key currently becomes DEAD and wipes the encrypted payload. This makes preflight key recovery and an independent worker hold mandatory before restored processing. Current expiry: reset15min, other security challenge24h, invitation48h; email eligibility is always rechecked and an already submitted message cannot be recalled after concurrent revocation.

## Conditional provider decision

Resend remains the preferred **adapter evaluation candidate**, not an approved provider. Official documentation checked4November2026 says idempotency is bounded to24h and webhook verification must use the original raw body/signature headers. Its documented default API budget is10requests/s across the team, not per key. Stored data remains in the US; ordinary email/log retention is30d. Domain sending region does not relocate stored data. [Idempotency](https://resend.com/docs/dashboard/emails/idempotency-keys), [webhook signatures](https://resend.com/docs/webhooks/verify-webhooks-requests), [usage limits](https://resend.com/docs/api-reference/rate-limit), [security/retention](https://resend.com/security).

Owner approval must cover DPA/subprocessors/transfers/retention, region, account/endpoint deduplication scope, actual quota/budget, permitted sender and support handling of uncertain acceptance. Do not reuse historical Phase8 prices as a live quote. If those conditions fail, select a different provider and revisit the uncertain-send design; do not silently fail over after an ambiguous submission. The provider can read submitted verification/reset links despite local outbox encryption; tracking must remain off.

### Sender/domain operational certificate

Select an owner-controlled **new SaaS** transactional sending domain/subdomain, independent of old FitCore. Domain verification, From/Reply-To and Return-Path approval, SPF/DKIM alignment, DMARC reporting/enforcement rollout and provider-account scope must be recorded before any controlled delivery. Use the provider's exact newly issued records only in a separately authorized DNS change; this phase supplies no DNS values or credentials and changes no old zone. Preserve authentication-header evidence from a later explicitly approved controlled-recipient test, with tracking off and bounce/complaint handling demonstrated. A provider dashboard's verified flag alone does not prove final DMARC alignment or mailbox placement. [Resend domain verification guidance](https://resend.com/docs/dashboard/domains/introduction).

## Adapter and durable state, proposed

Use a small reviewed HTTP adapter behind EmailProvider. No SDK hidden retry loop, arbitrary recipient/URL endpoint, automatic redirect or fallback provider. Fixed approved HTTPS endpoint; provider credential accessible to the worker only. Mock HTTP fixtures are sufficient for the initial implementation and every fault test; no external egress.

An additive migration afterV9 must introduce the following logical state, with explicit backfill rules for synthetic existingV8 rows. Final migration version/name is assigned only after design approval; V1–V9 stay immutable.

| Logical record | Required data / invariant |
| --- | --- |
| Frozen send envelope | Existing encrypted recipient/body plus exact From/Reply-To/headers, template revision, provider/account/endpoint scope and body digest. Same logical request on every retry, regardless of later template/config/key rotation. Persist sensitive fields encrypted; no raw token in new metadata. |
| Dispatch lifecycle | first-attempt timestamp reserved **before** network, security expiry, retry deadline, stable outbox UUID/provider key, attempt/lease state, provider scope and restore epoch. Timestamp/deadline cannot be reset by requeue. |
| Provider acceptance receipt | Provider/account + outbox UUID + request digest + opaque provider message reference + acceptance time; transactional receipt/status update fenced by lease. A stale worker cannot overwrite a newer lease, but its externally observed receipt must be reconciled safely. |
| Webhook inbox | Unique(provider account,event ID), verified signature key ID, received/provider timestamp, payload digest and minimal encrypted/bounded event data; event↔receipt correlation and applied state. No email/token in logs. |
| Delivery outcome | Separate from ACCEPTED: provider-reported delivered, delayed, bounced, complained, suppressed or unknown. Out-of-order delivered events cannot erase a complaint/suppression. |
| Suppression | Global new-instance recipient HMAC index plus encrypted address where operations require it, reason/source/time and operator audit. Cross-gym destinations cannot bypass suppression; managers cannot read another gym's event/contact records. |
| Restore/uncertainty hold | Explicit quarantine/hold reason, external reconciliation reference and signed resume scope. Do not label an unknown submission “not sent” or mint a fresh key. |

Current IDs, tenant ownership and AAD bindings remain. New state/indexes/FKs/status CHECKs must be independently certified along with expanded restricted-runtime/worker grants. No payload re-encryption, key retirement or terminal cleanup can orphan an in-flight send or invalidate a retained backup's required keys.

### Dispatch/retry contract

1. In the application transaction, validate account/invitation/gym and reserve issuance budgets; enqueue a frozen encrypted intent. Do not call the provider in that transaction. Public response means request accepted for processing, never delivered; retain generic recovery responses.
2. A supervised worker first verifies lease authorization, complete required key inventory, provider configuration, suppression, quotas and clock health. Claim one eligible row under the existing short lock transaction; perform network work outside the transaction. Initially one worker with one in-flight request, while concurrency tests still prove multiple workers cannot claim the same lease.
3. Stable UUID key remains tied to the same provider/account/envelope. Proposed hard retry deadline: `min(security expiry, first_attempt_at + 23h)`, with an additional clock-skew safety margin.23h is a FitCore proposal inside Resend's24h guarantee, not a guarantee after any restore. An unattempted valid invitation can be sent later, but once attempted its original deadline never resets.
4. HTTP budget: proposed3s connection and15s total including DNS/TLS/read/retries, bounded64KiB provider response, no redirects. Confirm the selected client actually cancels/closes a timed-out call.15s must remain safely below120s lease; shutdown and platform request budgets must be compatible. Timeout is **unknown acceptance**, not proof of non-delivery.
5. Keep maximum5 attempts initially; add bounded jitter and honor Retry-After only inside the hard deadline. Retry transient network/429/5xx/concurrent-key refusal; treat mismatched-key payload409 as an integrity/configuration fault, not reason to use a new key. Authentication/domain/configuration rejection pauses the account/worker and alerts an operator. Permanent address rejection produces a privacy-safe terminal reason and appropriate suppression.
6. Record reference and ACCEPTED only after a valid provider acceptance. Provider acceptance is not mailbox placement or human reading. A provider delivery webhook still does not prove email ownership; only the existing challenge consumer can verify the account. Terminal status wipes token-bearing ciphertext under retention policy; audit/receipt fields retain only required minimal metadata.
7. Any ambiguous work at/after deadline, missing trustworthy first-attempt time or restored queue epoch is quarantined for reconciliation. No automatic provider switch, fresh key, manual attempt-counter reset or silent historical replay. Fresh user-requested verification/recovery creates a new valid challenge and explicitly cancels obsolete intent; it does not retry an expired secret.

Recheck eligibility immediately before submission, but document the unavoidable race after that check: a concurrent revoke cannot recall a provider-accepted email. The link still fails at the authoritative challenge/invitation consumer. Tests must preserve that distinction.

### Webhooks and suppressions

- A dedicated exact raw-body endpoint is outside browser-cookie CSRF handling, using provider signature authentication instead. Bound initial raw request to a proposed1MiB, verify via reviewed Resend/Svix library, accepted key ring and5min timestamp tolerance, then parse. Reject wrong signature, future/old timestamp, unsupported provider account and oversized/malformed payload. Never infer authenticity from IP, Host or JSON fields.
- Commit inbox dedup entry and applicable receipt/delivery/suppression transition atomically; return2xx only after durable acceptance or already committed duplicate. If database work fails return retryable status. Match message reference to the frozen provider/account receipt, not a caller-provided gym/email. Unknown correlation stays held for review without revealing accounts.
- Bounce/complaint/suppression is monotonic until an independently approved correction; ordinary gym managers cannot override global suppression or relabel a complaint as delivered. Transient delivery delay is not permanent suppression. Separate status history makes retries and out-of-order webhook behavior explicit.
- Provider retry timestamp, signature-key rotation and supported event shapes require adapter contract fixtures; event ID dedup survives restarts/multiple instances. Dedup retention must cover the actual provider replay horizon and approved operator replay policy, not merely local queue retention. Restore must reconcile suppressions before outbound traffic.

### Worker, cleanup and keys

Use a **new Render background worker** for continuous polling; use a separate bounded cron/maintenance command for cleanup so retention works while sending is disabled. This is a design, not an existing entrypoint or deployed resource. The worker does not expose an HTTP service. Render workers initiate network requests; a Vercel function cannot address a worker's nonexistent private HTTP endpoint. [Render worker model](https://render.com/docs/background-workers).

Stop new claims on SIGTERM; finish a bounded in-flight call and persist receipt, or let the lease expire without inventing a result. Proposed30s shutdown budget contains15s HTTP budget; actual platform policy must be certified. Unfinished/uncertain outcomes still reuse the stable key inside the horizon. Poll interval1s with bounded empty/backoff jitter, one in-flight call initially, provider-wide budget2requests/s burst2 as an owner-reviewed starting cap. These are proposals, not current scheduler values.

Proposed cleanup runs every5min UTC, each run≤30s, batches≤100, no overlapping DB lease, and separate budget/role. It cancels expired secret-bearing intents and purges terminal metadata after a proposed7d queue retention, with separately approved longer security/receipt/dedup retention. Key metadata/backup retention constrains deletion: do not destroy a key while any live ciphertext or retained recoverable backup needs it. Provider30d retention is not reduced by FitCore deleting its queue.

AES-GCM root/data keys remain outside PostgreSQL/backups; secret manager holds immutable key versions and access audit. Worker receives only needed current/historical keys. Database restore is tested with missing, wrong and rotated key sets **before claims**; missing material pauses the worker without destructive row mutation. Securely recover older keys to reconcile retained ciphertext; key-compromise response may instead deliberately invalidate challenges and quarantine work. JWT/session/gateway keys and old outbox decryption keys have different recovery lifecycles.

## Shared limiter and trusted proxy design

Retain PostgreSQL as the MVP shared limiter. It already supplies atomic multi-instance budgets and fail-closed behavior, avoiding a new Redis dependency. Do not substitute the H2 process fallback in production. Redis/Valkey is an optional future capacity migration requiring equivalent atomic/multi-scope and outage tests, not a prerequisite inferred from old Phase6 text.

Verified current limits are500 global and50 direct-peer per path/300s; count rollback protects other peers' accepted-work budget; SQL lock/statement timeouts are2/3s, rows expire10min and cleanup≤100 per request. The>10000-row capacity check is not a strict concurrent row invariant. Those values are not yet an approved production policy. Login and manager invitation issuance require additional protection.

### Client identity boundary

Recommended path is browser→Vercel→signed BFF request→Render. Keep `server.forward-headers-strategy=none`; do not let a global forwarding filter rewrite remoteAddr. Resolve client identity only from an authenticated BFF assertion after its signature/audience/nonce/method/body/cookie binding is verified. Reject absent/malformed client identity for protected public operations; never use an attacker header or generic proxy peer as a silent replacement.

At Vercel, use the platform-established client IP, not a caller's first X-Forwarded-For value. Current docs describe overwritten forwarding headers; an additional CDN before Vercel needs a separately approved topology. Do not assume `x-vercel-signature` signs ordinary proxy API requests; documented usage for platform webhooks/drains is different. FitCore supplies its own service assertion. [Vercel request-header contract](https://vercel.com/docs/headers/request-headers).

Canonicalize single IPv4/IPv6 values, IPv4-mapped IPv6, ports and whitespace through a strict parser. Reject duplicate/ambiguous lists and invalid/private client values unless explicitly valid for the approved ingress. Grouping IPv6 /64 and NAT fairness require measured acceptance; IP never grants identity or tenant rights. Limit recipient scopes using a separate keyed HMAC of the existing normalized address, with privacy retention and key-rotation overlap; current SHA256 scope hashes are pseudonyms, not anonymous data.

### Initial budgets for owner review

All numbers below are **proposed**, not approved/default live policy. Public features remain disabled until an explicitly supplied complete budget policy is certified. Limit all instances together, include a provider/account-wide cap and do not reveal which dimension fired.

| Action | Proposed initial shared limits | Notes |
| --- | --- | --- |
| Login |20/IP/5min and5 failed/account/15min; deployment-wide500/5min ceiling | Generic denial; no permanent account lockout from unauthenticated traffic; unknown addresses take the same budget path. Test NAT fairness and targeted lockout denial-of-service. |
| Owner registration |5/IP/15min;3/address/hour;100/deployment/hour | Pending challenge, no gym activation until ownership verification. |
| Verification/reset requests |10/IP/15min;3/address/15min;500/deployment/5min | Apply scopes even for unknown/disabled/already-verified accounts; generic status/body and measured timing policy. |
| Challenge consumption |20/IP/5min;5/opaque challenge digest/5min;500/deployment/5min | Single-use lock semantics remain; never log the token/digest. |
| Invitation issuance |10/issuer/hour,20/gym/hour,100/gym/day,3/recipient/day | Actor-derived issuer/gym, atomic reservation with invitation+intent; no platform/admin bypass. |
| Provider submissions |2requests/s across workers, burst2; daily/monthly ceiling supplied from selected plan and owner budget | Retries count too; initial activation default budget0. Pause before exhausting approved spend/volume, alert at80%. Reserve recovery/security capacity without bypassing provider limits. |

Separate ingress-attempt counters from issuance/send reservations. Admitted ingress attempts commit in an independent transaction before handler work and remain counted when credentials, challenge validation or the business transaction fails; this preserves today’s separate limiter transaction. Failed-account budgets include unknown accounts and cannot be rolled back by authentication failure. A rejected admission rolls back that admission’s scope changes so a rejected peer cannot drain unrelated global budgets. Invitation/intent issuance reservations commit atomically with accepted local issuance; they do not replace ingress counters. Invalid clients still need a separate bounded ingress rejection budget, without triggering arbitrary database cardinality growth. Provider calls consume provider budget once attempted even when acceptance is unknown. Use fair priority between gyms and security recovery; a noisy owner must not starve every gym.

### Capacity and outages

Maintain a small separate limiter pool/bounded transaction budget so attacks cannot exhaust the normal application pool. Proposed connection/lock/statement/request budgets must be measured; initial target is503 within1s when the limiter cannot decide, not indefinite pool wait. Existing2/3s SQL timeouts alone do not bound pool acquisition. Do not return success or fall back locally on timeout, DB outage, capacity limit or corrupted counter state. Rate denial429 includes bounded Retry-After; unavailable store503 includes a generic retry hint.

Run scheduled expiry cleanup, cap new-scope admission, monitor row cardinality/contention, and test bucket boundaries/clock changes. Application + limiter + worker + cleanup + migration + operator pool totals must fit a reviewed DB capacity budget with≥30% headroom. Global counters create hot rows; load-test two application instances under projected peak/burst before accepting PostgreSQL throughput. If the result fails, pause launch and review the store design rather than weakening limits.

## Required local verification before activation

- Real mock-HTTP acceptance, bounded connect/read/total timeout, late response/crash, stable request/key across key/template/config change,409 variants,429,5xx, permanent rejection and no secret-bearing logs.
- Atomic enqueue rollback and sender-failure reset behavior; both gyms' invitation bindings/role/expiry/revocation remain independent. API/browser/UI never claim delivery from a202/queued state.
- Two worker instances, stale lease, providerTTL model,>24h outage, restored PROCESSING/PENDING and incomplete receipt; no unsafe requeue or new-key resubmission. Missing-key preflight must not delete retained ciphertext.
- Raw webhook tamper/old/future signature, key rotation, duplicate IDs across instances, durable rollback, out-of-order delivery/bounce/complaint and forged gym/provider references. Suppression survives restore reconciliation.
- Two actual application instances with signed proxy identity, direct backend bypass, spoofed XFF/Forwarded/service headers, IPv4/IPv6/NAT, issuer/gym/recipient/global budgets and key rotation; login/account enumeration, failed credentials/invalid tokens consuming budgets despite handler rollback, and parallel quota reservations included.
- DB/pool outage, counter hot-row saturation, cardinality exhaustion, bucket boundary and cleanup while sending off; fail-closed503 and no in-memory fallback. Preserve every existing membership/booking/catalog concurrency case.

All are future synthetic/disposable tests until architecture and implementation scope are approved. Actual provider selection, secret/DNS/webhook configuration and controlled real-recipient sending each have later separate approval gates.
