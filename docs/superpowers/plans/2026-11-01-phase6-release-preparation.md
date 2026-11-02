# Phase 6 release preparation implementation plan

> Execute inline with superpowers:executing-plans and test-driven-development. No commits or external mutations. One fresh-context security/integration review at completion.

**Goal:** evidence-backed local preparation and a truthful PASS/BLOCKED release decision.
**Spec:** supplied Phase 6 brief and approved `2026-10-30-multi-tenant-mvp.md` Task 8; the latest user restrictions control scope.
**Architecture:** preserve shared PostgreSQL, server-derived tenant identity and every existing business rule. Harden local auth configuration, keep all delivery disabled, rehearse existing explicit synthetic mappings and restore actual PostgreSQL archives into separately owned disposable containers. CI runs verification only.

## Constraints and rulings

- Baseline cf407f0 on codex/multi-tenant-saas; preserve all earlier files/evidence. Never access customer data, commit/push/merge/deploy, enable public onboarding, or relax startup safety.
- Current brief requests review/preparation of the deferred V3, not an executable production ownership decision. V3 remains deliberately unshipped and release-blocking until the inventory, manifest contract and operator approvals are validated. Synthetic mapping SQL stays test-only; do not quietly claim the full operational Task 8 is complete.
- Implement a configurable disabled email-provider boundary, not unaudited public verification/recovery endpoints. Design token/delivery/distributed throttling requirements and retain these as production blockers.
- JWT issued-at tolerance: proposed bounded local default30s, configurable0..120s; expiry remains strict. Require numeric iat, iat before expiry, bounded future issue time. Production clock policy approval remains required.
- Browser bearer-token transport remains unchanged; cookie/BFF/CSRF transition requires separate architecture/rollout approval.

## Steps and evidence

1. [x] Inventory/review domain, repositories/endpoints, auth, onboarding, catalogs, configuration and migration sequence. Capture preservation fingerprints and baseline tests.
2. [x] Add failing HTTP JWT issued-at and CORS configuration regressions; run RED. Harden validation and exact origin allowlist; run GREEN and existing security suites.
3. [x] Add failing disabled email-provider configuration tests; implement fail-closed provider abstraction without credentials or real sending.
4. [x] Add bounded protected readiness checks and regressions; retain liveness and startup prohibition. Readiness must fail closed on incompatible schema/configuration or privileged runtime credentials; do not equate readiness with ownership/release approval.
5. [x] Add owned synthetic migration/recovery tests using actual pg_dump/pg_restore into fresh disposable PostgreSQL. Check complete row fingerprints, history, constraints, multiple gyms, disabled admin/overlap preservation and transaction failure. Reuse existing mapping fixture only; never load a real manifest.
6. [x] Add read-only-permission, SHA-pinned GitHub verification workflow and deterministic local gate with no deploy/secrets. Fail on skipped PostgreSQL, security/migration failures, frontend failures or critical browser failures.
7. [x] Write inventory/manifest specification, private approval checklist, email security design, production backup/recovery and cutover/rollback runbook. Mark real-data/provider/remote CI controls BLOCKED without evidence.
8. [x] Run complete backend, recovery, frontend and two-gym browser suites, including gate-off. Fresh whole-change review; fix actionable issues with regressions. Report exact counts/evidence/remaining gates.

## Review focus

Missing/malformed/future JWT iat; wildcard/credentialed CORS; accidentally enabled email; migration partial commits and shared-catalog clones; schema-only or unverifiable backup; runtime DDL/privilege access; skipped CI PostgreSQL; loss of private evidence; schema-incompatible rollback.
