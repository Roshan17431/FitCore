# Phase9D-1 source publication — 5 November2026

A subsequent owner instruction authorizes splitting the completed work into at most15 commits and publishing to the default branch. This supersedes the implementation-stage prohibition on commits/publication only; production deployment, persistent database operations, public onboarding and external sending remain **BLOCKED**. The [implementation report](phase9d1-secure-browser-auth-spike-2026-11-04.md) records the preceding local-only phase and its known browser/recovery limitations.

Authenticated repository metadata and remote symbolic HEAD confirm **master**, starting at `45782b8050580036c0c9423acce90befe089ce7a`. The selected worktree matches that baseline. Publish13 focused commits with a normal fast-forward update of master; do not force-push, rename/delete branches or publish development branches. Preserve the linked worktrees, private remediation/ownership documents and local review/test evidence. The six pending Phase9D architecture documents are technical design deliverables and accompany this implementation; private sibling files and runtime artifacts are excluded. A byte-for-byte backup of every pending source/document file and the pre-commit diff remains outside Git before staging.

## Commit groups

1. Independent SaaS release architecture and assessment.
2. Infrastructure, recovery, email/abuse decisions and implementation milestones.
3. Additive V10 session schema, exact readiness contracts and migration/restore certification.
4. Digest-backed session families and actual-cookie delivery acknowledgement.
5. Signed gateway, framework CSRF and conditional browser authentication endpoints.
6. Session, gateway, role/version/revocation and concurrency regression tests.
7. Fixed-upstream Node HTTPS BFF and signed-request/cookie contract tests.
8. Browser cookie coordinator, identity binding, lifecycle and uncertainty tests.
9. Explicit cookie-mode frontend integration and protected-view invalidation.
10. Disposable HTTPS, two-backend and real-browser race/lifecycle harnesses.
11. Mandatory release-result gates, isolated legacy ports and artifact hygiene.
12. Implementation report, completed plan and updated production-blocked checklist.
13. This source-publication record.

## Verification and publication safeguards

The complete local `./scripts/verify-release.sh` passed on the reviewed executable source before publication: **1,117 backend executions,83 frontend Node tests,84 distinct browser checks (96 executions),6 shared-backend checks and14 release-gate unit methods**, plus lint/default and cookie builds, schema certification, restoration, tenant isolation and cleanup. Counts distinguish inherited/repeated test executions from distinct scenarios. Retained source hashes are checked before staging; publication changes only documentation beyond the verified source. Fresh publication checks repeat83 Node tests, lint/build,14 gate-test methods and validate retained complete PostgreSQL/browser result artifacts. No extra persistent database or production connection is used.

Every commit includes **[skip render]**. Existing root and frontend Vercel configurations retain `git.deploymentEnabled.master=false` and `main=false`, including every intermediate tree. These are documented source-trigger controls, preserved without modifying live hosting settings: [Render skip phrases](https://render.com/docs/deploys#skipping-an-auto-deploy), [Vercel branch deployment controls](https://vercel.com/docs/project-configuration/git-configuration#git.deploymentenabled). The GitHub workflow contains no deployment job; its verification may run after publication. A green CI run is not release authorization.

V1–V9, FoundationSafetyConfiguration, render.yaml, production credentials and hosting configuration remain unchanged. No deploy hook/API, restart, production migration, real email or public onboarding activation is invoked. Browser mode remains opt-in and usable failed-login/expiry/uncertainty recovery, native BFCache/visibility and wider engine certification, external revocation/recovery authority, target/startup/DB-TLS certification, real backup/restore, production operations and final approvals remain unresolved.

This record states the prepared publication scope and pre-push evidence. The exact resulting remote SHA, number of new commits, source equality, worktree state and observed CI outcome are verified after the push and reported in the chat; no unperformed remote success is assumed here. No claim of production readiness or a hosted security approval is made.
