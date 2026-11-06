# Release-readiness source publication — 6 November 2026

The owner subsequently authorized splitting the reviewed local work into at most 15 commits and pushing it to the default branch. This changes the earlier no-commit/no-push restriction for source publication only. Existing FitCore production, persistent startup, public onboarding, real email, hosting settings, deployment and production migration remain unapproved and BLOCKED. The [current release checklist](multi-tenant-release-checklist.md) remains authoritative for those gates.

Remote symbolic HEAD and a fresh fetch identify **master** at `21a96292a292456444a4611071762109fa230dbe`. The local development branch starts at that same commit. Prepare 14 focused commits and publish a normal fast-forward update to master. Do not force-push, change the default branch, publish unrelated branches, delete worktrees or include private remediation/ownership files or ignored test/review artifacts. A byte-for-byte backup of all 38 original pending files and the original diff is retained outside Git before staging.

## Commit groups

1. Bounded cookie recovery design and stopped approval workstreams.
2. Non-authorizing session metadata and explicit expired/revoked sign-out.
3. Shared gateway admission and bounded nonce/session maintenance.
4. Backend control, abuse, rollback, revocation and cleanup regressions.
5. Browser coordination, proof-bound recovery and storage-race regressions.
6. Explicit status/sign-out UI and cookie API integration.
7. Disposable held-request fixture and single-forwarding tests.
8. Strict native restart proof classifier and negative tests.
9. Actual HTTPS recovery and browser-process restart runners.
10. Cookie gym/current-account UI and synthetic donor fixtures.
11. Two-JVM abuse verification and owned disposable browser harness.
12. Mandatory backend/Node/browser/engine/policy result gates.
13. Pinned CI installation of all three browser engines.
14. Current production-blocked checklist and this publication record.

## Verified scope and publication controls

The completed `./scripts/verify-release.sh` passed on the reviewed executable tree: **1,172 backend executions, 151 frontend tests, 166 distinct browser check IDs across 206 executions, 8 shared-backend checks and 19 Python gate-test methods**, plus lint/default and cookie builds. Seven additional real PostgreSQL fixture-guard probes passed. All 14 protected migration/startup/business/entity source files match baseline bytes. The 36 changed code/test/config hashes are checked against the successful final run; publication adds or edits documentation only beyond that tree. Fresh publication checks repeat 151 Node tests and 19 gate methods and validate the retained complete backend/browser results. They do not claim a second PostgreSQL/browser run or a hosted CI result.

Every commit contains **[skip render]**, the documented [Render auto-deploy skip phrase](https://render.com/docs/deploys#skipping-an-auto-deploy). Existing root and frontend Vercel settings preserve `git.deploymentEnabled.master=false` and `main=false` in every intermediate tree, using the documented [Vercel Git deployment controls](https://vercel.com/docs/project-configuration/git-configuration#git.deploymentenabled). No live hosting setting, deploy hook, provider credential, service restart or production target is accessed or changed. The GitHub workflow performs verification only; no deployment job is added. A passing workflow does not authorize release.

No migration or runtime/business implementation outside the reviewed security work is changed. Operational V3 mapping remains absent for the empty-database Option A path. Lost/retired/unbound/absolute-expired proof recovery, tokenless cookie onboarding, browser support certification and the independent target/email/backup/CI/security/operational approvals remain blocked. No release gate is waived by publication.

This records the prepared source-publication scope. The resulting commit count, exact remote SHA, fast-forward result, tree equality, worktree state and any observed CI status are verified after the push and reported separately. No unperformed remote success is asserted here. Review evidence and private files remain local, and the development worktree is retained.
