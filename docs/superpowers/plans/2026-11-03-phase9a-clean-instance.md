# Phase 9A Clean Instance Implementation Plan

> Execute inline using superpowers:executing-plans. No commits, publication, production changes or persistent resources.

**Goal:** Prove clean initialization and synthetic two-gym recovery; prepare independently approved release design.
**Architecture:** Existing V1,V2,V4–V8 and guards stay unchanged. Add integration evidence, not an alternative production startup path.
**Tech stack:** Java17, PostgreSQL18 disposable containers, existing Spring/MockMvc, Playwright release harness.
**Spec:** User's Phase9A attachment; Option A selected. Earlier Phase8/9 documentation remains in original checkout untouched.

## Constraints and review focus

Never read credentials/customer databases; tests must verify harness URL/marker. No fakeV3, checksum/schema bypass, guard changes, real sender or hosting calls. Empty catalogs may expose product gaps: report/propose them rather than inventing catalog APIs/business rules. Recovery must preserve encrypted retry payloads and independent key material; cannot certify real backups. A green local test does not certify hostedCI.

## Tasks

- [x] Run baseline full release script in new worktree from ddf71b6; record exact result outside Git.
- [x] Add CleanInstanceHttpTest: empty V8 schema/history; secure queued test-only owner verification for two gyms; own member/trainer invitation acceptance, scoped sessions/lists/catalog creation, foreign IDs and disabled account. Reuse existing broader business/browser regressions. Do not change application behavior for missing catalog features; record scope precisely.
- [x] Add CleanInstanceRecoveryTest: new synthetic V8 source, representative two gyms/membership/booking, custom archive and independent owned restore, all-row/sequence reconciliation, encrypted outbox decrypt/key-loss checks, actual restored HTTP isolation and runtime privileges. Reuse TenantRecoveryTest helpers without inheriting/re-running old tests.
- [x] Register new suites in fail-closed release-result gate; demonstrate missing-suite RED then update checker and fixtures GREEN.
- [x] Document minimum future production target guard/readiness design, independent Render/Vercel/Postgres resources, variable names only, unresolved catalogs/security, ordered operator approvals.
- [x] Run focused tests then full release script; review diff/safeguards and original workspace preservation; leave undeployed/uncommitted.

No application fix is assumed necessary. If source behavior is deficient, first pin it with a test and propose minimal separately reviewed implementation where scope/architecture is unresolved.

## Execution rulings / final state

- Baseline full gate passed941/35/41. No main application/migration fix was assumed or made.
- Clean and fullV8 restore exposed unaccepted schema fingerprints. Keep fail-closed checks, pin rejection in tests and propose complete descriptor certification rather than accepting hashes without review. Production readiness remains BLOCKED.
- Only membership catalogs support owner writes. Test existing behavior and report missing catalog configuration/empty-state features; no APIs or default catalogs invented.
- Outbox recovery fixture uses database localtimestamp to avoid host/JDBC timezone assumptions. Existing application issuance already uses DB time.
- Temporary schema diagnostic removed after evidence capture. Missing-suite result checker RED→GREEN observed.
- Focused3 and final944backend/35frontend/44distinct browser/7checker tests passed; lint/build passed. Exact evidence and independent review are in the report. No commit/push/deploy/persistent database/production access.
