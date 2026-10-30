# Multi-tenant foundation source publication — 2026-10-30

The owner authorized splitting the completed foundation into at most 15 commits and pushing to the remote default branch. Remote symbolic HEAD confirms **master**, initially at `930d5b0b964eeba69708ea07f8f5cad7300c5134`. This publication contains **eight new commits**, including this record.

## History and preserved work

The implementation began at main `4ad96321f584dbfaaa2b3a9885bbf04f9cbe1d0f` on the isolated `codex/multi-tenant-saas` branch. Before committing, that checkout was fast-forwarded to existing origin/master. The additional default-branch files were already published design/checklist documents and deployment guards; application code was unchanged. No existing commits were rewritten, and no cherry-pick or conflict resolution was needed.

The original Desktop checkout, its unrelated uncommitted work, other branches and private remediation files remain in place. Source changes were backed up before integration. Evidence, browser artifacts, .env files, private inputs and the backup remain outside the published changes.

| Commit | Focus |
| --- | --- |
| `2dae847` | chore(safety): confine foundation execution to disposable databases |
| `bfe6761` | build(db): introduce Flyway and the existing-schema baseline |
| `2887c30` | feat(tenancy): add gym domain and ownership mappings |
| `00a6aa1` | feat(db): enforce tenant integrity and rehearse legacy mappings |
| `7bb9b2a` | fix(tenancy): preserve write ownership and restrict owner grants |
| `ade1ea8` | test(ui): exercise migrated gym fixtures through existing workflows |
| `5e2f323` | docs(saas): record foundation implementation and verification |
| This commit | Publication verification and remaining release gates |

## Verification on the integrated tree

All checks were rerun in the isolated checkout after bringing in origin/master. Committing did not alter the tested source contents.

| Check | Result |
| --- | --- |
| `gym-backend/scripts/verify-tenancy.sh` | **347 tests, zero failures/errors/skips; BUILD SUCCESS** |
| Actual Flyway/PostgreSQL migration and integrity cases | **44 passed**, included in the backend total |
| Startup/connection safety cases | **15 passed**, included in the backend total |
| Frontend `node --test` | **19 passed**, zero failures/skips |
| Frontend lint and production build | Passed |
| Phase 2 real browser → API → migrated PostgreSQL | **8 scenarios passed**, including renewal race 200/409, preserved membership history and booking conflicts |
| Phase 3 real browser → API → migrated PostgreSQL | **10 scenarios passed**, including member/trainer/admin workflows and persisted writes |
| Browser runtime/network checks | Zero uncaught browser errors and zero external requests |
| Cleanup | Owned processes stopped, ports 18082/5175 free, owned disposable PostgreSQL container removed |
| Whitespace and index review | `git diff --check` clean; only explicit foundation/documentation paths staged |

All database initialization, migrations and writes used harness-created synthetic PostgreSQL containers or in-memory H2 fixtures. No customer database, hosting dashboard, production service or private ownership manifest was accessed.

## Publication controls and remaining gates

All eight commit subjects contain `[skip render]`, preserving the repository's established automatic-deployment hold. Both existing Vercel configurations retain `git.deploymentEnabled.main=false` and `git.deploymentEnabled.master=false`. No Render manifest, frontend application source, live hosting setting, deploy hook, restart or production migration is changed by publication.

The push uses a normal fast-forward update of master. It does not force-push, delete branches or change the default branch. The exact resulting remote commit and clean local state are checked after pushing and reported in the chat; this document records the verified pre-push tree rather than claiming an unperformed deployment.

The [implementation report](multi-tenant-foundation-2026-10-30.md) contains the full file inventory, domain relationships and migration details. This is a foundation checkpoint: tenant-aware HTTP/JWT/service/repository authorization, onboarding and the operational V3 mapping runner remain deferred. The foundation startup guard continues to block customer database use. Separate ownership decisions, schema/backup certification, complete tenant isolation and explicit release authorization remain required before serving customer traffic.
