# Phase 4: owner onboarding and invitations — 2026-11-01

Local development on `codex/multi-tenant-saas`, base `d162287fad3e0f63b82b40ce53fbbbc56f47e4b9`. At implementation completion no commits, pushes, merges, deployments, customer-account changes, production configuration changes or persistent migrations had occurred. Subsequent source-publication authorization is recorded in [the publication report](owner-onboarding-publication-2026-11-01.md). Existing checkout work, earlier test evidence and private remediation files are preserved.

## Implemented behavior

Owner signup validates name/email/gym/slug and a 12–64 character composed password, with an additional 72 UTF-8 byte BCrypt limit. It creates the gym, enabled OWNER and primary-owner designation in a single transaction. Global email and slug uniqueness remain database-enforced. The transaction commits before a schema-2 tenant JWT is generated or returned; duplicates roll back the new gym/account. No templates, demo customers, default gym, payments or ownership transfers are created.

Invitations permit MEMBER or TRAINER only, as explicitly requested in Phase 4. Authorized local OWNER/ADMIN issuers receive a 256-bit random URL-safe token once; only its SHA-256 digest is stored. Ordinary invitation lists expose IDs, email, role, expiry and accepted/revoked timestamps, never the token or digest. Expiry is 48 hours in UTC. Acceptance binds the exact invited email, stored gym and stored role; existing accounts cannot join another gym. Trainer acceptance creates its local trainer profile atomically. Account creation, profile creation and consumption commit together; failures leave the invitation unused.

Acceptance first resolves only a scalar gym UUID by token digest, locks that gym, then first loads the invitation under its pessimistic row lock. Revocation uses the same gym-first order. This avoids stale cached acceptance/revocation state and makes replay, concurrent consumption and revocation safe. Invalid, revoked, expired, reused and suspended-gym invitations receive a generic refusal.

Current gym information includes local member, trainer and administrator counts. The approved GET contract permits authenticated users to view their current gym; name editing requires OWNER. Unknown owner/gym-management fields are rejected. Existing local user/trainer management and protected OWNER policies remain unchanged. OWNER can manage local ADMIN accounts through existing management APIs; ADMIN cannot target ADMIN/OWNER or grant privileged roles.

## API contracts

| Method and endpoint | Authorization and behavior |
| --- | --- |
| POST `/api/auth/register-owner` | Public, development gate required; fullName, email, password, gymName, slug; fixed initial OWNER; returns committed session |
| POST `/api/auth/register` | Public but requires invitationToken, bound email, fullName/password and optional fitnessGoal; no role or gym selection; returns committed session |
| GET `/api/gym` | Valid active-gym session; current gym metadata and local counts |
| PATCH `/api/gym` | Local OWNER; body `{name}` only |
| POST `/api/gym/invitations` | Local OWNER/ADMIN, development gate required; `{email,role}` with role MEMBER/TRAINER; one-shot invitationToken |
| GET `/api/gym/invitations` | Local OWNER/ADMIN; safe status list |
| DELETE `/api/gym/invitations/{id}` | Local OWNER/ADMIN; scoped pending invitation revocation; foreign/missing ID gives404 |

All prior JWT credential/version/disabled/suspended checks and server-derived TenantActor behavior remain. No ordinary route can replace the primary owner or disable the required OWNER. No full frontend onboarding screens are introduced.

## Development/demo and email ownership

`fitcore.onboarding.demo-enabled` defaults to false. It is enabled only explicitly by the disposable PostgreSQL tests or an operator running an owned local demo. The foundation startup prohibition still refuses persistent customer databases, regardless of this flag. No email delivery or verification is operational, and no response claims that an email is verified.

For a controlled local demo, an authorized issuer copies the one-shot token into an acceptance request body. A future local frontend can construct `/register#invitationToken=<token>` so the secret stays out of server URL logs; parsing that link and full owner-facing screens belong to Phase5. There is no automatic message sending or public invitation directory. Do not log, store in analytics, or repeatedly display the plaintext token.

Before production, require a separately designed verified email ownership/activation and recovery workflow, invitation delivery, distributed abuse controls, full release review and deployment authorization. Current demo-created enabled accounts must never be represented as verified customer identities.

## Abuse protection

A bounded single-process fixed-window limiter protects public owner signup and invitation acceptance: 50 attempts per remote address and 500 globally per five minutes, returning429 and Retry-After. Forwarding headers are ignored. Invalid requests count toward the limit before expensive password hashing. Storage is bounded and resets each window. This is suitable for the deliberately isolated demo; multi-instance/shared-counter limits and trusted-proxy policy are production gates, not claimed operational protection across servers.

## Database changes

V6 `V6__permit_trainer_invitations.sql` replaces the invitation-role check with MEMBER/TRAINER. OWNER/ADMIN remain rejected. No existing accounts, ownership mappings or historical memberships/bookings are rewritten. V1–V5 are unchanged; deferred operational V3 remains unimplemented. The complete persistent migration/certification/recovery sequence is still a future authorized task.

## Verification and review

The latest run includes **490 PostgreSQL HTTP/isolation cases**, covering the existing Phase3 matrix and new onboarding tests, plus deterministic revocation-versus-acceptance, privilege rejection and HTTP rate-limit tests. **Final full suite: 865 tests, zero failures/errors/skips; BUILD SUCCESS** (`phase4-release-check.log`). This includes all prior membership/booking, security, migration and concurrency regressions. Controller coverage now includes all six new routes.

PostgreSQL cases exercise committed signup and acceptance, two gyms independently, duplicate email/slug rollback, trainer/member binding, safe lists, foreign revocation denial, expiry/revocation/replay, existing-email collisions without consumption, concurrent signup/acceptance, protected management and existing tenant isolation. Database writes/migrations use only owned marked disposable containers with synthetic fixtures and DML-only application credentials. Frontend20 Node tests, lint and build passed; frontend application code is unchanged in this phase.

Fresh read-only review found a stale invitation race and a timezone-dependent expiry fixture. The deterministic regression reproduced revocation losing under the old code (200 instead400); the scalar lookup fixes that cache race. The expiry fixture now binds an unequivocal UTC past timestamp. The deterministic revocation test passes with the fix, and the final full suite verifies both corrections. A separate HTTP rate-limit regression reproduced a servlet-path mismatch (400 instead429 on attempt51); using the normalized request URI corrected it and the focused HTTP case passed.

Deferred nonblocking review item: service entry points rely on validated controller DTOs for complete password composition and input shape. Internal password checks additionally enforce minimum length and BCrypt byte limit. Any future caller outside these controllers must perform DTO validation; independent service-boundary Bean Validation remains hardening work.

Deferred JWT `iat` hardening was reviewed: the existing factory emits issued-at; expiry, signature, UUID/gym, current enabled/status, authorization version and credential version remain mandatory. No external issuer is supported. Requiring issued-at shape/time bounds remains deferred until clock-skew policy is specified; this is not a known tenant bypass, and no new exception to schema2 authorization was added.

## Readiness and remaining gates

Phase5 can integrate the backend contracts following the green final tests and review fixes. Full owner screens and invitation-link handling are intentionally deferred. Production remains blocked pending email verification/delivery, distributed rate limiting, operational V3 and actual schema/backup certification, complete frontend readiness and separate release approval. No production state was inspected or certified.

## Files changed

- Modified: `gym-backend/src/main/java/com/fitcore/gym/controller/AuthController.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/dto/auth/RegisterRequest.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/repository/GymInvitationRepository.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/security/JwtAuthenticationFilter.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/security/SecurityConfig.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/security/TenantWriteGuard.java`
- Modified: `gym-backend/src/main/java/com/fitcore/gym/service/AuthService.java`
- Modified: `gym-backend/src/test/java/com/fitcore/gym/security/TenantSessionTest.java`
- Modified: `gym-backend/src/test/java/com/fitcore/gym/tenancy/TenantIsolationTest.java`
- Created: `gym-backend/src/main/java/com/fitcore/gym/controller/GymController.java`
- Created: `gym-backend/src/main/java/com/fitcore/gym/dto/gym/GymUpdateRequest.java`
- Created: `gym-backend/src/main/java/com/fitcore/gym/dto/gym/InvitationRequest.java`
- Created: `gym-backend/src/main/java/com/fitcore/gym/dto/gym/OwnerRegistrationRequest.java`
- Created: `gym-backend/src/main/java/com/fitcore/gym/security/OnboardingRateLimit.java`
- Created: `gym-backend/src/main/java/com/fitcore/gym/service/OnboardingService.java`
- Created: `gym-backend/src/main/resources/db/migration/V6__permit_trainer_invitations.sql`
- Created: `gym-backend/src/test/java/com/fitcore/gym/security/OnboardingRateLimitTest.java`
- Created: `docs/owner-onboarding-2026-11-01.md`
