# FitCore source consolidation and SaaS design publication

30 October 2026. The owner authorized splitting the pending work into **at most 13 new commits** and pushing all outstanding branch work to the repository's default branch. The remote symbolic HEAD identifies that branch as **master**. This authorizes source integration/publication; it does not approve multi-tenant implementation, production database operations, membership remediation, or deployment.

## Branch audit and integration

| Preflight branch | Commit | Result |
|---|---|---|
| origin/master | 8ef78e693d9da2e470193d3ec7acd5ac6a195d09 | Default branch; Phase 1 security baseline |
| origin/main | 4ad96321f584dbfaaa2b3a9885bbf04f9cbe1d0f | Contains all 17 Phase 2 and 12 Phase 3 commits; default branch fast-forwards through this history |
| origin/codex/fix-functional-flows | 7da226a977330b977a9cec9acc3c702770207ec3 | Its complete tree is identical to 755ea4b, an ancestor already included in main; no independent file changes remain |

Both the older functional branch and 755ea4b have tree b82289c5f163c4200b4665eb6f5c6c77941a72ba. An ancestry-only merge records that already integrated branch while retaining the newer application tree, including subsequent security, booking, membership and UI improvements. No conflict resolution replaces newer code with the older branch's files.

The Phase 2 integration point 806792fadd67fbe2220ecac5436d03c2e6f5c0c1 is already an ancestor of main; the previously inspected local Phase 3 branch ends at main's 4ad9632. There is no missing phase to cherry-pick. Other remote branches are preserved, not deleted or force-rewritten.

The publication creates five focused commits: the equivalent-history merge, deployment guards for master, the technical design, the implementation checklist, and this verification/publication record. The 29 improvements previously published on main retain their original commits; they are not rewritten merely to change the number of historical commits reachable from master.

## Published scope

- [Multi-tenant architecture and migration design](superpowers/specs/2026-10-30-multi-tenant-phase1-design.md).
- [Dependency-ordered implementation checklist](superpowers/plans/2026-10-30-multi-tenant-mvp.md).
- The already published Phase 2/3 work, now reachable from the default branch.
- Vercel branch-deployment guards for master in both the root and frontend configurations, retaining the existing main guards.

No new application business/security implementation or executable tenant migration is introduced. Relative to origin/main, application source, dependencies, tests, database initialization and the Render manifest are unchanged. The SaaS design remains proposed for architecture/coding approval.

## Deployment hold

Every newly created commit includes **[skip render]**. Render documents this phrase as skipping an automatic deployment. Both vercel.json and gym-frontend/vercel.json set git.deploymentEnabled.main=false and git.deploymentEnabled.master=false, covering either supported project-root selection. Frontend build/routing settings and other branch behavior remain unchanged.

These are source-publication safeguards, not changes to a live hosting dashboard. No deploy hook, deploy API, restart, credential rotation or production database operation is invoked. Do not remove the guards or manually release these changes until the existing data/recovery gates and a separate deployment approval are satisfied. See [Render skip phrases](https://render.com/docs/deploys#skipping-an-auto-deploy) and [Vercel branch deployment configuration](https://vercel.com/docs/project-configuration/git-configuration#git.deploymentenabled).

## Fresh verification

All checks below ran against the application tree being published, in a new isolated clone without application .env files. PostgreSQL tests and browser writes used only harness-owned disposable loopback containers.

| Check | Result |
|---|---|
| Backend disposable PostgreSQL runner / clean verify | **281 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS** |
| Frontend API/theme/contrast Node tests | **19 passed, 0 failed/skipped** |
| Frontend lint | Passed |
| Frontend production build | Passed |
| Real Phase 2 browser -> API -> PostgreSQL checks | **8 passed**, including renewal race 200/409, preserved upgrade history and booking conflicts; zero external requests |
| Real Phase 3 browser -> API -> PostgreSQL checks | **10 passed**, including MEMBER/TRAINER/ADMIN reads and authorized writes; zero uncaught errors/external requests |
| Browser harness cleanup | Owned servers stopped, ports 18082/5175 free, disposable PostgreSQL container removed |
| Design coverage | All 44 controller endpoints match the matrix; eight implementation tasks and local document links validated |
| Application-tree preservation | No application-source/dependency/test/Render-manifest differences from origin/main |

Verification artifacts, browser state and synthetic fixture data are not committed. The full 444-page visual matrix was not repeated for this source-publication task; the existing Phase 3 report retains that historical evidence.

## Workspace and remaining limits

The original Desktop checkout has cloud-offloaded Git objects/refs and unrelated untracked work. Publication uses a fresh temporary clone instead of resetting, restoring, cleaning or repairing that checkout. Only the two newly authored design documents are copied from it; .DS_Store, .superdesign, interview/analysis notes, duplicate verification notes, application .env files, and private remediation files are excluded.

Existing private ownership decisions, overlapping-membership remediation, usable administrator/owner access, actual schema inventory, and verified backup/restore remain separate requirements. No production data was read or mutated by this task. Successful local checks do not certify those production conditions.

The push uses a normal fast-forward update of master, without force or default-branch changes. The exact pushed HEAD and remote ancestry are verified after publication and reported in the chat; this document records scope and the pre-push evidence, rather than claiming an unperformed deployment or database migration.
