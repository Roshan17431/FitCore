# FitCore Phase 2 source publication — 20 October 2026

The owner requested splitting the completed work into **at most 17 commits** and pushing it to GitHub **`main`**. This authorizes source publication. The outstanding membership decisions, private production administrator test and usable backup/isolated restore requirements remain open; no production data change, restart or rollout is authorized.

Repository: [FitCore repository](https://github.com/Roshan17431/FitCore). The preflight remote has **`master`** as its default branch and no `main` branch. The requested push creates `main` from the verified Phase 1 base and publishes the Phase 2 commits there. It does not rename the default branch or update `master`. Render's authenticated service inspection confirms automatic deployment follows **`master`**.

The Vercel project-settings request returned **403 forbidden**, no local Vercel CLI was installed, and the dashboard requires sign-in. Live project settings could not be certified. To preserve the deployment hold independently of its production-branch selection and root-directory setting, both the repository-root `vercel.json` and `gym-frontend/vercel.json` disable automatic Git deployment specifically for **`main`**. Build commands, output, routing and other branches retain their existing configuration. Vercel documents this branch rule, and it was checked against the current official configuration schema. [Vercel Git configuration](https://vercel.com/docs/project-configuration/git-configuration#git.deploymentenabled).

Remove or alter that deployment guard only as part of a separately approved release. No Vercel/Render dashboard setting, database connection, credential or JWT secret is changed by source publication. The Git push is not a database-remediation operation.

## Verification for this publication

- Disposable PostgreSQL runner executes **`./mvnw clean verify`**: **281 passed, zero failures/errors/skips**, including the original 172 baseline tests and 22 PostgreSQL-specific tests.
- Network-isolated PostgreSQL 18.6 remediation rehearsal: **25 checks passed**, using synthetic records only; no production connection.
- Frontend API contract tests: **7 passed**. Frontend lint and production build pass.
- Fresh real frontend/API/PostgreSQL Playwright verification: **8 checks passed**, zero external requests; cross-tab renewal produced exactly one 200 and one 409. Owned browser/server/container resources were removed and test ports verified free.
- Vercel's `git.deploymentEnabled.main=false` rule is supported by the official schema and preserves the existing frontend build/routing fields.

Verification evidence is kept locally under `output/playwright/phase2-push-2026-10-20/` and the existing browser artifact directory. Evidence, test outputs, private owner files, decision manifests and customer records are not committed.

## Commit grouping

1. UTC business clock and membership date predicate.
2. Membership request/response contracts and member-row lock.
3. Transactional purchase, renewal and upgrade endpoints.
4. Consistent dashboard and administrator membership summaries.
5. Membership and midnight-boundary regressions.
6. Trainer availability parser and persistence conflict queries.
7. Transactional booking rules and updated trainer unit fixtures.
8. Booking regression coverage.
9. PostgreSQL concurrency verification and disposable runner.
10. Frontend membership API mapping and contract tests.
11. Membership page transitions, submission and feedback handling.
12. Trainer page validation and conflict handling.
13. Fixture and integrated Playwright scenarios with isolated harness.
14. Aggregate and private-output read-only audit query templates.
15. Guarded remediation/rollback templates, postchecks, rehearsal and remediation review.
16. Phase 2 implementation and source-publication documentation.
17. Main-branch Vercel automatic-deployment guard.

Only these task-related files are included. Existing unrelated `.DS_Store`, interview notes, earlier untracked readiness report and duplicate verification document remain local. Private owner evidence stays outside the repository with its existing restricted permissions.
