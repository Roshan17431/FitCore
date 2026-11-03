# Phase 9B Schema Readiness Certification Plan

Execute inline. User authorizes local-only correction after independent descriptor verification. Source ddf71b6 plus preserved Phase9A changes on codex/phase9a-clean-instance.

- [x] Preserve Phase9A evidence and inspect complete freshV8, fullV8 archive restore and accepted staged-restore descriptors on owned disposable PostgreSQL18.
- [x] Add test-only snapshot collection keyed by schema object identity (including table for repeated trigger names), covering columns/defaults/types, validated/deferred constraints, indexes, enabled triggers, function definitions/config, relation/RLS flags and sequence definitions. Check ownership/ACLs and effective runtime rights separately; validate migration checksums with Flyway.
- [x] Independently review exact descriptor differences and enum semantics. If differences are substantive stop allowlist changes and propose additive migration. No normalization or omitted CHECK constraints in production.
- [x] Positive clean/restored runtime readiness tests must fail before correction. Add transactional drift/enum/FK negative cases and make the suite required by the release gate.
- [x] Add only certified exact fingerprints to SchemaContract, retaining prior contracts and untouched descriptor algorithm/ReleaseReadiness/startup guard/V1–V8.
- [x] Update Phase9A expectations to readiness true/HTTP200; preserve every workflow/recovery assertion.
- [x] Focused checks then ./scripts/verify-release.sh. Record exact counts and limitations; review diff and resource cleanup, leave uncommitted/undeployed.

No existing production access, credentials, external provisioning/sending, persistent migrations or business-rule change. Schema readiness is not permission to run on a persistent DB. Historical Phase9A report remains historical; Phase9B report records closure.

## Final execution record

- Preserved Phase9A source/evidence; current structural inventory982 records/path plus explicit least-privilege runtime checks.
- Independent review reproduced exact hashes and verified nine enum CHECKs, partial-index cast and three raw search-path formatting differences only. All other captured metadata and14 sequence ownership dependencies match; policies absent.
- Descriptor/SQL semantics and all-three Flyway validation passed before allowlist mutation. Positive readiness RED observed; only two hashes/comment then changed in production.
- Focused13 passed; full release gate954backend/35frontend/44distinct browser/8checker tests, lint/build passed with zero backend skips/errors/failures. Source algorithm/ReleaseReadiness/guards/V1–V8/business logic unchanged.
- Temporary evidence output moved to ignored target JSON and copied to sibling evidence. Review ACL feedback fixed to individually ANDed DML permissions.
- No commits, publication, persistent target, real credentials/email, infrastructure or deployment. Schema certification PASS locally; persistent startup/release BLOCKED.
