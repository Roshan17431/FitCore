# Phase 7 — transactional email infrastructure (local only)

Date:2026-11-02. Baseline6d204ee, isolated `codex/multi-tenant-saas`. User approved local AES256GCM encryption with external keys, PostgreSQL leases and disabled provider/worker defaults. **Production remains BLOCKED.** No production access, V3 mapping, external mail, credentials, provider/domain/webhook provisioning, Git publication or deployment occurred.

## Implemented architecture

`TransactionalEmailSender` now has a structured intent boundary. With `fitcore.email.outbox.enabled=false` (default), the existing disabled sender remains and no retry key is generated automatically. With the local outbox enabled and an explicitly supplied valid key ring, the boundary encrypts/inserts delivery intent in the caller's application transaction. It does not contact a provider. Verification/reset challenge insertion and queue insertion share the transaction; invitation creation flushes its JPA row before the same-transaction JDBC/FK insert. Queue failure rolls back application state. Existing direct in-memory test senders remain test-only and preserve prior security regressions.

New `EmailProvider.accept(stableUuid, message)` is an adapter contract, not a shipped network provider. The only runtime implementation refuses external sending. Unknown actual provider configuration still fails startup. An acceptance response must contain a nonblank reference; the row is marked **ACCEPTED**, never DELIVERED. No mailbox-delivery webhook implementation is claimed. A future approved adapter must enforce bounded network deadlines, stable idempotency across retry/crash, recipient/header validation, and outbox-UUID correlation for authenticated webhooks.

### Covered messages

- Pending owner email verification.
- Existing-account and pending-invitation email verification.
- Gym-scoped MEMBER and TRAINER invitation emails, linked to the stored invitation/gym.
- Password-reset email.
- Credential-free password-change notification, enqueued in the password-change transaction when queueing is enabled.

Responsive FitCore-branded HTML and plain-text templates have static subjects, escaped display text, security/expiry instructions, and strict permitted token paths/fragments. Callers supply a fixed validated HTTPS origin (loopback permitted for tests), never request Host or forwarding headers. No JWT/password goes into a template; retry tokens appear only inside the encrypted message until decryption for delivery. The invitation UI makes no delivery claim.

## Durable state and worker semantics

V8 adds `email_outbox`: UUID/idempotency identity; optional gym/challenge/invitation bindings; kind; encrypted payload and key ID; status/attempts/due time; lease token/deadline; expiry, acceptance/completion timestamps and redacted failure category. A composite gym/invitation FK prevents foreign-gym intent references. Pending-owner challenges legitimately have no gym yet; no default gym or ownership assignment is invented.

States: PENDING -> PROCESSING -> ACCEPTED, retry PENDING, DEAD or CANCELLED. Claim uses PostgreSQL `FOR UPDATE SKIP LOCKED`, a short separate transaction and a fresh lease UUID. Provider calls occur outside the database transaction. Completion/retry writes compare the lease token; an older worker cannot overwrite a newer claim. A worker crash leaves a reclaimable two-minute lease. Attempt count is bounded to5, with exponential30s/60s/120s/240s retry delays and a30-minute upper cap. Expired, consumed, revoked, missing or stale-account-bound tokens are not sent. Permanent rejection/decryption failure terminates with a generic code; retryable or ambiguous provider failures retry, then dead-letter with acceptance uncertainty recorded.

The stable outbox UUID survives every attempt. If a process dies after provider acceptance but before database acknowledgement, another worker may submit again. The fake provider test proves one acceptance when that provider honors idempotency. **No universal exactly-once or exactly-once-mailbox guarantee is claimed.** A provider without appropriate idempotency/retention requires an explicit duplicate-risk decision before selection. Lost leases/cancellation cannot unsend a call already in progress; token redemption remains protected by the existing expiry/revocation/purpose checks.

Workers have no automatic scheduler in this change. `fitcore.email.outbox.worker-enabled` defaults false; `processOne()` must be invoked explicitly by an approved local test/worker. Worker-on with outbox/encryption-off is rejected at startup, preventing existing ciphertext from being drained as undecryptable. A production scheduler/runtime, bounded concurrency/network deadlines, shutdown handling and monitoring need separate approval.

## Confidentiality, keys and retention

`OutboxCipher` permits a later KMS envelope implementation. The approved local implementation uses AES256GCM, fresh96-bit nonces and128-bit tags. Recipient, subject, text and HTML are encrypted together; AAD binds outbox UUID, message kind, challenge/invitation IDs and gym. Swapping ciphertext between rows or changing bindings fails authentication. Only the nonsecret key ID is stored alongside ciphertext.

Configuration: `fitcore.email.outbox.keys` is an externally supplied semicolon-separated `keyId=base64Key` ring; `fitcore.email.outbox.active-key` selects the32-byte active key. No runtime fallback key, real key or provider credential is committed. Tests use explicit synthetic keys. Rotation writes with the new active ID and can decrypt queued messages with retained older keys. Keep historical keys through pending-message and backup retention; losing a required key dead-letters/wipes that payload, so recovery requires an approved new challenge/resend, not guessing/regenerating its old token. Separate encryption keys from JWT/provider credentials; enforce custody, backup and rotation policy before production.

ACCEPTED/DEAD/CANCELLED rows clear ciphertext/key ID immediately on the state transition. Challenge completion and invitation acceptance/revocation clear queued payloads, including invitation-linked verification challenges, even when new issuance is switched off. Expired payloads are cleared when worker/cleanup observes expiry; **physical deletion is not guaranteed while all polling/cleanup is stopped**. Explicit bounded cleanup handles100 rows per call and removes terminal metadata after7days. Before enabling issuance operationally, approve and run cleanup scheduling, key/backup retention and privacy controls. No token or message body is stored in logs/analytics; provider exceptions are reduced to fixed categories without their messages/stack. Envelope/message/template objects have redacted string representations.

## Security and compatibility findings

The fresh review identified twoP2 issues, both reproduced before fixes: revocation missed challenge-linked invitation verification messages, and worker-on/encryption-off could destroy pending payloads. Cancellation now traverses the stored invitation digest with the same gym binding; invalid configuration fails before workers can claim. A related regression checks completion still scrubs competing messages after issuance is disabled. JPA invitation flush is explicit so delivery intent cannot violate its FK before the domain row exists.

Existing account/gym ownership, JWT schema2, issued-at/credential/authz checks, BCrypt limits, challenge expiry/replay/concurrency, shared abuse limits and primary-owner invariant remain. Membership/booking services and rules are unchanged. New delivery work does not verify an email or create an account merely because a provider accepted a message; the existing verification transaction remains required. S3, public production onboarding and startup prohibition remain unchanged.

## Migration and verification

Only additive `V8__transactional_email_outbox.sql` is new: composite invitation uniqueness/FK, outbox table, status/lease/payload checks and due/binding/retention indexes. V1–V7 are unchanged; V3 remains unimplemented. Runtime readiness requires V8, the outbox grant and updated schema-security fingerprints. Synthetic restore tests still start from V2/V4/V5/V6 and migrate through V8; no production schema/backup is certified.

The final `./scripts/verify-release.sh` exited0: **941 backend tests (zero failures/errors/skips),35 frontend tests,41 distinct browser checks and5 release-gate checker tests**; lint and production build passed. This preserves all912 prior backend cases and adds29 cases:15 outbox worker/state tests,3 queued-account integration tests,2 cipher tests,6 template tests and3 configuration-safety tests. Browser checks executed53 groups across enabled/disabled invocations, covering41 distinct cases; both runs verified owned PID/port/container cleanup. Shell syntax and diff-whitespace checks pass. Remote CI execution is not claimed because no push occurred.

Baseline912/35/41 was reverified first. The review's cancellation and invalid-worker-configuration regressions were observed failing before fixes, then passed in focused and full runs. Source V1–V7, deferred V3, membership/booking logic, startup prohibition and deployment guards were not changed. Focused cases cover encrypted intent/state rollback; tamper/AAD/key rotation; concurrent workers; retry delays/permanent failure/dead letters; crash-after-acceptance with idempotent provider; stale-lease fencing; consumed/expired/revoked/wrong-gym tokens; ciphertext swapping; payload/metadata cleanup; disabled defaults; real queued verification/reset/notification; issuer-gym binding; revocation and disabled-issuance scrubbing. Existing backend/frontend/browser regressions remain required. CI's critical-test checker now requires outbox, queued-account, cipher, template and configuration suites so skips/absence/failures block its gate.

Baseline912 backend/35frontend/41browser checks were freshly rerun and passed before implementation. All database writes use owned marked disposable PostgreSQL with synthetic fixtures. Test providers operate only in process memory. The local baseline and final evidence remain outside Git under sibling `evidence/phase7-*.log`; temporary archives/containers are removed by the harness.

## Exact approvals and blockers

See [provider operations and approvals](transactional-email-provider-operations.md) for provider capabilities, SPF/DKIM/DMARC, secrets, key custody/rotation, signed/replay-protected/deduplicated webhooks, bounce/suppression handling, recovery, worker operations and abuse controls. These are prepared requirements, not configured production services.

Required approvals remain: actual provider and idempotency/deadline contract; sender/domain/DNS; external secret/KMS/encryption-key custody and recovery; durable worker/cleanup scheduling and metrics; webhook endpoint/signature/event model and retention; real delivery/suppression/abuse/timing tests; proxy/rate policies; V3 private ownership/inventory decisions and operational runner; real encrypted backup/ACL restore/RPO/RTO; browser transport and runtime/security/monitoring approvals; exact remote CI/artifacts and required checks; explicit migration/startup-guard replacement/deployment/public-onboarding/mail activation and rollback authorization. No real email may be sent until separately approved.

## File inventory

Created:

- `docs/superpowers/specs/2026-11-02-transactional-email-design.md`
- `docs/transactional-email-phase7-2026-11-02.md`
- `docs/transactional-email-provider-operations.md`
- `gym-backend/src/main/java/com/fitcore/gym/config/OutboxConfiguration.java`
- `gym-backend/src/main/java/com/fitcore/gym/email/AesGcmOutboxCipher.java`
- `gym-backend/src/main/java/com/fitcore/gym/email/EmailIntent.java`
- `gym-backend/src/main/java/com/fitcore/gym/email/EmailOutbox.java`
- `gym-backend/src/main/java/com/fitcore/gym/email/EmailProvider.java`
- `gym-backend/src/main/java/com/fitcore/gym/email/EmailTemplates.java`
- `gym-backend/src/main/java/com/fitcore/gym/email/OutboxCipher.java`
- `gym-backend/src/main/resources/db/migration/V8__transactional_email_outbox.sql`
- `gym-backend/src/test/java/com/fitcore/gym/config/OutboxSafetyTest.java`
- `gym-backend/src/test/java/com/fitcore/gym/email/OutboxCipherTest.java`
- `gym-backend/src/test/java/com/fitcore/gym/security/EmailTemplatesTest.java`
- `gym-backend/src/test/java/com/fitcore/gym/tenancy/EmailOutboxTest.java`
- `gym-backend/src/test/java/com/fitcore/gym/tenancy/QueuedAccountEmailTest.java`

Modified:

- `docs/multi-tenant-release-checklist.md`
- `gym-backend/src/main/java/com/fitcore/gym/config/EmailDeliveryConfiguration.java`
- `gym-backend/src/main/java/com/fitcore/gym/email/TransactionalEmailSender.java`
- `gym-backend/src/main/java/com/fitcore/gym/operations/ReleaseReadiness.java`
- `gym-backend/src/main/java/com/fitcore/gym/operations/SchemaContract.java`
- `gym-backend/src/main/java/com/fitcore/gym/service/AccountSecurityService.java`
- `gym-backend/src/main/java/com/fitcore/gym/service/OnboardingService.java`
- `gym-backend/src/test/java/com/fitcore/gym/tenancy/AccountSecurityTest.java`
- `gym-backend/src/test/java/com/fitcore/gym/tenancy/TenantRecoveryTest.java`
- `gym-frontend/src/pages/Invitations.jsx`
- `scripts/check_release_results.py`
- `scripts/test_release_results.py`

No source files were deleted. Original migrations/business rules/startup/deployment guards are unchanged. Test artifacts and private evidence are not published.
