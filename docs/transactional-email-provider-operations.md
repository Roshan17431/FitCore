# Transactional email provider operations — Phase 7 design and runbook

**Status: local preparation only; production and real delivery remain BLOCKED.** This document specifies operating requirements and approval gates. It is not evidence that a provider, worker scheduler, webhook receiver, monitoring system, KMS integration or production deployment exists, or that any test has passed. The Phase 7 implementation report must identify the capabilities actually implemented and their verification separately. No provider is selected here. No credentials, sender domain, infrastructure, production access or real message sending is authorized by this document.

The approved local direction is an AES-256-GCM encrypted transactional outbox with an externally supplied keyring, a stable provider idempotency key per message, and PostgreSQL `FOR UPDATE SKIP LOCKED` claims with expiring worker leases. Provider delivery and workers default to disabled. Preserve the existing production startup prohibition and independent onboarding/security-flow gates. A provider acceptance response is not proof of mailbox delivery, inbox placement, human receipt or token redemption.

Read alongside the [security closure report](security-blocker-closure-2026-11-01.md), [original Phase 6 onboarding design](production-onboarding-security-design-2026-11-01.md), [migration and recovery runbook](production-migration-and-recovery-runbook-2026-11-01.md), [V3 decision checklist](v3-private-ownership-decision-checklist.md) and [release checklist](multi-tenant-release-checklist.md). Historical documents describe earlier states; they do not authorize activation.

## Provider selection and adapter acceptance

An operator must approve an identified provider, account, region, contract, data processing terms, subprocessors, retention, support/recovery procedure and restricted sending policy before integration with real credentials. Do not assume provider idempotency, webhook authenticity or recipient suppression behavior from a generic email API. Record the selected API/version and independently review its documented guarantees.

The provider decision must specify:

- The idempotency key field, scope (account, endpoint or region), allowed syntax, retention/TTL, concurrent duplicate behavior, response on payload mismatch and whether a status query resolves uncertain acceptance. The same logical message must retain its original key across retries, crashes, lease changes and manual recovery. Never generate a new key simply because an attempt timed out.
- A bounded connection timeout, request/response deadline, maximum response size and total attempt budget. The provider request deadline must fit inside the worker lease with margin for a fenced result update. Retries use bounded backoff with jitter and honor bounded provider retry hints; retry limits and elapsed-message age must fit the approved idempotency window and security-token expiry.
- Sending/recipient quotas, rate limits, sandbox behavior, accepted sender identities, template/header restrictions, message-ID format and a safe way to test only synthetic recipients. Set explicit outbound destination restrictions; arbitrary provider URLs supplied by users are forbidden.
- Which status codes and structured provider error codes mean accepted, temporary refusal, permanent refusal or uncertain outcome. Response bodies and exceptions may contain personal data or secrets and must not be copied to logs or outbox error fields.

The proposed failure taxonomy is deliberately provider-neutral:

| Category | Required operator/adapter behavior |
| --- | --- |
| Accepted | Store only approved receipt metadata; stop ordinary submission retries. Await delivery events where supported. |
| Retryable refusal | Retry only documented temporary failures, such as a confirmed rate-limit refusal, under the same key and bounded policy. |
| Permanent recipient/message refusal | Stop retries, record a fixed reason category and apply the approved suppression or correction process. Do not alter account/tenant state. |
| Authentication, permission or sender configuration failure | Pause affected sending and alert; do not repeatedly retry a broken credential/domain or expose provider details to public callers. |
| Timeout, disconnect, ambiguous server failure | Treat acceptance as unknown. Reconcile by status lookup or retry with the same key only within a verified idempotency guarantee. Escalate uncertainty when that guarantee cannot be relied on. |
| Local key, ciphertext or payload validation failure | Fail closed before network submission, quarantine with a fixed safe category and investigate. Do not fall back to plaintext or another key/provider. |
| Expired/revoked challenge or expired message budget | Stop submission. An authorized fresh request creates a new challenge/message; never extend the old security token to drain a queue. |

An HTTP success alone must not be interpreted as acceptance unless the provider's contract establishes it. An idempotency TTL shorter than the possible retry, outage and restore horizon leaves a duplicate-send risk. After that horizon, quarantine uncertain messages for an approved reconciliation decision; automatic replay with either the same expired key or a fresh key is not safe deduplication. A second provider does not share the first provider's idempotency store; failover of uncertain sends needs a separate decision.

## Sender identity and content

Approve the exact sender domain/subdomain, visible From name/address, envelope/bounce domain and monitored Reply-To. The domain owner must approve DNS changes and verify the provider's exact SPF and DKIM records. Review DMARC alignment and an approved monitoring/enforcement rollout; do not publish guessed DNS values or claim verification from configuration alone. Confirm DNS propagation, alignment, provider verification and a controlled receipt test before activation. Keep sender/provider control separate from ordinary gym administrators.

Use fixed reviewed templates and the configured approved HTTPS frontend origin. Recipient input must not choose headers, a sender, template code, link origin or redirects. Never include a password, JWT or tenant-wide data in email. Security links expire and remain purpose/account-bound even if mail arrives late. Tracking pixels, link rewriting, open/click analytics and marketing use require separate privacy/security approval; do not enable them by default for recovery links.

## Encryption, keys and recovery

The local design uses authenticated AES-256-GCM encryption for the sensitive outbox payload with a fresh cryptographically random nonce per encryption. Store the key identifier, nonce, ciphertext and authentication tag; authenticate the immutable message identity and agreed metadata as associated data. A key identifier is not key material. Retain only minimal nonsecret routing/state metadata outside the ciphertext. The authentication challenge continues to retain a token digest; encrypted retry payload storage does not justify plaintext token storage anywhere else.

The keyring is supplied externally. No usable default key, committed key, plaintext key file, key in a database row or fallback key is permitted. Keep encryption keys separate from provider API credentials, JWT signing keys, webhook signing secrets and database/archive encryption keys. Restrict access to the smallest runtime/worker identities that need it. Validate key IDs, decoded key length, active-key availability and required historical-key availability before enabling delivery. Missing/unknown keys, corrupt ciphertext, tag failure or associated-data mismatch fail closed without sending or logging payloads.

Production secret injection, auditing and managed KMS/envelope encryption remain future work requiring approval. A possible future envelope scheme wraps per-message data keys with a managed key and records the wrap version/context; that scheme must be designed and tested explicitly. This document does not claim that a KMS, wrapping, automatic rotation or key-access audit trail is implemented by the local keyring.

Rotation procedure for approval:

1. Inventory key IDs used by live, retrying and quarantined messages and every retained backup; record the encryption context/version needed for recovery without copying keys into the inventory.
2. Provision a new key through the approved secret channel, verify isolated synthetic decrypt/encrypt behavior and retain the old keys for reads. Designate the new key for new writes only after all relevant instances can read it.
3. Drain or explicitly re-encrypt existing payloads using fresh nonces and the correct associated data under a separately reviewed process. Never overwrite ciphertext with a key ID change alone. Keep delivery disabled during an unverified migration.
4. Verify recovery of the actual encrypted archive with its independently retained key set in an isolated environment with provider egress disabled. Backing up ciphertext without recoverable keys is not a usable backup; bundling keys in the same unrestricted archive defeats separation.
5. Retire an old key only when no live/quarantined payload or retained recoverable backup needs it, retention/legal obligations permit retirement and the authorized recovery owner signs off. If a compromise requires earlier destruction, explicitly approve the resulting unreadable messages/backups and recovery consequences.

Retain approved key versions through the full retry/quarantine and backup retention horizons. Do not purge old keys based only on successful new writes. Key loss and key compromise require separate incident procedures; neither is solved by regenerating a key with the same identifier.

## Leases, crashes and retry operations

Claims should use a short database transaction, `FOR UPDATE SKIP LOCKED`, a bounded batch and database-time lease expiry. Commit a claim before external I/O; do not hold a row lock or database transaction across a provider request. Result updates must be fenced by the current claim/lease token so a worker that lost ownership cannot overwrite a newer worker's result. A lease is coordination, not cancellation of an in-flight provider request.

The operator must approve concurrency, batch size, lease duration, heartbeat policy if any, provider deadline, attempt limit, backoff, maximum message age and scheduler topology together. Recover expired claims in bounded work. On shutdown, stop claiming, finish only within the allowed deadline and leave unresolved claims to expire. Stop submission when the token is no longer valid or the delivery budget is exhausted. Public issuance responses remain generic and must not disclose queue/provider/recipient eligibility.

Crash windows remain material:

- A crash before submission can leave a claim to expire and be retried.
- A crash after provider acceptance but before recording a receipt leaves an uncertain message that may be submitted again.
- A stalled worker may complete its provider request after its lease expires while another worker retries. Fenced database updates prevent stale state writes; provider idempotency is needed to bound duplicate submissions.
- Restoring an older database can resurrect messages already sent and lose newer receipts. Keep workers and egress off during restore; reconcile provider records and idempotency horizons before any replay.

This is at-least-once attempt processing with bounded provider deduplication where explicitly guaranteed. It is not exactly-once mailbox delivery. PostgreSQL claims alone cannot provide that guarantee. Manual retry, dead-letter recovery and provider failover must preserve the original identity/key and follow the same expiry and uncertainty rules. A dead-letter state is an investigation boundary, not permission to resend indefinitely.

## Webhook authentication and event contract

Webhook operation remains separately blocked until a receiver and its tests are approved. Require the chosen provider's documented signature over the exact raw request bytes, validate the timestamp within an approved bounded tolerance using a trusted clock, compare signatures safely and reject unsigned, altered, expired or future-dated requests. Keep signing-secret rotation distinct from API/keyring rotation and define any bounded overlap of old and new signing secrets. IP allowlists may supplement signatures; they do not replace them. Bound request size and parsing time before processing.

Durably deduplicate authenticated events with a unique `(provider_account_scope, provider_event_id)` key and an approved retention horizon at least as long as the provider replay/redelivery window. A timestamp check alone does not prevent replay within that window. A duplicate must have no second effect. Provider events can arrive late or out of order; delivery transitions need an explicit precedence policy and cannot blindly overwrite newer state by arrival order. Unknown event types, schema versions or message correlations receive no business-state mutation and go through a privacy-safe review path. Return success only after durable accepted processing/deduplication; define provider-specific retry responses separately.

Proposed fixed normalized event schema (a design contract, not an implemented endpoint):

| Field | Allowed content/purpose |
| --- | --- |
| `schema_version` | Fixed supported integer version; reject unsupported versions. |
| `provider_account_scope` | Configured opaque provider/account identifier, never request-selected routing. |
| `provider_event_id` | Bounded validated provider event identifier, used for durable deduplication. |
| `provider_message_id` | Bounded opaque receipt identifier, matched to the recorded provider submission. |
| `outbox_message_id` | Validated internal UUID, resolved through trusted stored correlation; never trusted from arbitrary metadata alone. |
| `event_type` | Fixed enum: `ACCEPTED`, `DELIVERED`, `DEFERRED`, `BOUNCED`, `COMPLAINED`, `SUPPRESSED`. |
| `occurred_at`, `received_at` | Validated provider event time and server receipt time, with an approved clock/order policy. |
| `reason_category` | Fixed sanitized enum, such as temporary/permanent recipient, policy or unknown; no free-form diagnostic. |
| `correlation_id` | Server-generated opaque operation identifier for restricted investigation. |

Reject or ignore extra provider fields according to an explicit versioned parser policy; never persist arbitrary webhook JSON, recipient address, subject, body, link, token, authorization header or diagnostic text in the normalized event record. Validate field lengths and enum values. Keep the authenticated raw body in memory only for signature verification unless a separately approved tightly restricted short-lived forensic policy requires otherwise. Correlation IDs are not credentials and must carry no token, email or tenant name. Verify the provider message/account correlation before any message-state effect; do not let webhook metadata select a gym/user or perform account verification/password reset.

## Delivery outcomes, suppression and retention

Provider acceptance means only that the provider accepted responsibility under its API contract. A delivery event generally records a downstream server outcome defined by that provider; it still does not prove inbox placement or human receipt. Verification/reset success remains controlled by the application's purpose-bound single-use challenge, independent of webhook status. Do not present “delivered” to users solely from an accepted receipt.

Approve hard/soft bounce classification, complaint handling, suppression scope, retry policy and appeal/removal process. Permanent bounces and complaints must stop inappropriate retries; transient delivery deferrals must not extend token validity. Check approved suppressions before submission, including retries, while preserving generic public responses. Suppression must not silently disable accounts, move gyms or grant recovery rights. Decide whether suppressions are account-wide or scoped; prevent cross-tenant data disclosure and avoid a per-gym bypass of a provider-wide complaint block. Any email digest used for private lookup needs an approved keyed scheme and rotation policy; do not claim a plain email hash is anonymous.

Approve separate retention schedules for decryptable queued payloads, terminal payloads, quarantined messages, minimal receipt metadata, webhook deduplication records, suppressions, logs and encrypted backups. Delete sensitive payloads when no longer needed for the approved delivery/recovery purpose, using bounded cleanup; terminal state alone does not prove physical deletion from replicas/backups. Retain only the minimal audit metadata needed for deduplication and investigation. Backup expiry/key-retirement rules must account for deleted payloads, replay risks and any legal hold. Do not invent fixed retention periods without provider, privacy and recovery-owner decisions.

Monitor only safe aggregate state, age, attempts, lease recovery, fixed error category, key ID availability, deduplication count, provider quota and opaque correlation IDs. Alert on rising age, unknown acceptance, stale leases, decryption failures, bounce/complaint anomalies and webhook signature failures. Restrict incident lookup access. Do not log recipient/token/ciphertext/body, arbitrary response text or secret-bearing exceptions. Thresholds, escalation owners and on-call coverage remain approval items.

## Exact activation approvals — all remain BLOCKED

Each approval must name the responsible human/operator, exact environment, immutable candidate/artifacts, configuration/schema versions, evidence and scope. A local feature flag or approval string is not authorization. These gates are cumulative; mail readiness cannot override existing release blockers.

| Gate | Evidence and authorization required before proceeding |
| --- | --- |
| Private V3 ownership | Approved actual inventory/mapping, operational V3 runner, reconciliations and explicit production migration authority. No inferred/default gym assignments. |
| Real backup and recovery | Actual encrypted target backup, recoverable historical outbox keys, roles/ACL restore certification, measured RPO/RTO and approved replay/reconciliation decision. Synthetic restore evidence is insufficient. |
| Provider | Named account/region/API contract, idempotency TTL/uncertainty/failure policy, quotas, data handling and sandbox evidence. |
| Sender domain | Domain-owner approval, exact SPF/DKIM/DMARC changes and verified alignment/controlled recipient receipt evidence. |
| Credentials and egress | Approved secret storage/injection/access/rotation plus explicit permission for real provider credentials, network destinations and real recipient sends. No secrets belong in this repository. |
| Encrypted outbox keys | Approved externally supplied keyring and custody, nonce/context contract, missing-key behavior, rotation, historical-key retention, archive recovery and future KMS decision. |
| Worker scheduler | Explicit scheduler/runtime provisioning, concurrency/leases/deadlines/retries, shutdown and lost-lease/crash evidence, monitoring ownership and manual-replay policy. |
| Webhooks | Approved endpoint exposure, signature/timestamp/replay tests, provider event deduplication, fixed schema/correlation, ordering, retention and secret rotation. |
| Abuse and proxy | Approved shared-limit capacity/cleanup/failure policy, real provider timing/enumeration tests and reviewed trusted proxy/hop configuration. Spoofed forwarding headers never establish trust. |
| CI and release evidence | Actual remote checks on the exact frontend/backend/schema artifacts, enforced required checks and independent review. Local verification is not remote CI certification. |
| Production activation | Explicit infrastructure, migration, deployment, startup-guard replacement, public onboarding/security flows, email/worker enablement and rollback/recovery approvals; browser transport, secrets/runtime privilege/logging and monitoring decisions remain required. |

Until every applicable gate is satisfied, keep provider/worker disabled, public production onboarding off, production startup blocked and no real credential/recipient/egress test. Do not apply DNS, provision services, run migrations, register webhooks, schedule workers, change secrets or send mail from this runbook without the corresponding explicit authorization. No such operational action or production check is claimed by this document.

Implementation boundary: the Phase7 local implementation now supplies the encrypted outbox, templates, explicit polling worker and tests described in its report. This operations document remains a design/approval runbook; no external provider, webhook, DNS, production scheduler or key custody service is operationally certified. Per-issuer/per-gym invitation quotas, recipient suppressions and provider-capacity/rate policies require approval and implementation before real delivery; the existing public shared limiter alone is not a complete outbound-spam control.
