# FitCore Security Fixes Implementation Plan

> Implementation was completed using the systematic-debugging and test-driven-development workflows. The original request prohibited publication until tests passed; a subsequent request authorized splitting the changes into at most 13 commits and pushing to the default branch.

**Goal:** Resolve the three security vulnerabilities in the 19 October audit, with automated read/write regression coverage.

**Architecture:** Enforce owner-or-admin access before personal service operations. Load current account state for each JWT request and reject disabled accounts. Reject the published administrator password, including existing database accounts, and make demo SQL explicitly opt-in.

**Tech Stack:** Java 17, Spring Boot/Security, JPA, JUnit, MockMvc, test-only H2.

**Spec:** User request and `output/playwright/audit-2026-10-19/audit-report.md`, issues 1–3.

## Constraints and review focus

- No unrelated feature changes or production database changes. Publication is authorized only after tests pass.
- Keep shared catalog reads and authorized personal/admin operations working.
- Check every personal read/write route, including attendance checkout by record ID.
- Denied writes must leave the database unchanged; request bodies must not override ownership.
- Test existing tokens after disabling or changing the administrator to the public password.
- Stop implicit demo loading even when a legacy environment requests SQL initialization.
- Preserve the identical untracked duplicate test outside the compiler source tree.

## Tasks

- [x] Add real-JWT HTTP regression tests with an isolated H2 database; run and observe the three vulnerabilities fail.
- [x] Add shared owner/admin authorization to personal service methods and enforce account state during JWT validation; rerun the authorization and disabled-token tests.
- [x] Reject compromised administrator credentials, isolate demo SQL, update safe configuration and operations documentation; rerun authentication and seed-isolation tests.
- [x] Run the full Maven suite and package build, review the diff and endpoint matrix, and report evidence without committing or deploying.

## Decisions

- Work in the existing checkout, preserving unrelated changes; all test data stays in H2.
- An account with the public administrator password is rejected at authentication lookup even if its database row already exists; rotating it to a private password restores access. No new bootstrap/admin feature is introduced.
- Personal access is owner or administrator. No trainer-to-member assignment policy exists in this project, so trainers do not receive blanket access to member records.

## Results

- Initial security suite: 142 cases, 47 expected assertion failures, no test errors; all three vulnerabilities reproduced.
- Initial full fix: 146 tests pass.
- Independent source review found that rotating a compromised administrator's password would restore previously issued tokens. Two new tests reproduced this with 2 assertion failures and no errors.
- Fixed that finding by requiring a keyed credential version in newly issued tokens. A password-hash change invalidates earlier tokens; legacy tokens are rejected. Rollout requires a fresh login for existing sessions.
- Expanded published-password coverage to original/re-salted hashes and added all 11 admin endpoint denial checks for MEMBER/TRAINER plus an authorized admin read/write control.
- Final `./mvnw clean verify`: 172 tests, 0 failures/errors/skips, executable JAR built successfully. `git diff --check` passes. No auto-discovered `data.sql` or H2 dependency in the JAR.
- Optional S3 remains disabled by default and unchanged: it has no asset ownership model, outside the database-record vulnerabilities requested here. Reassess its access policy before enabling it for users.
- At the implementation checkpoint, no commits, pushes, deployments, or production changes had been made.

## Publication

- Subsequent user request: split the security changes into at most 13 commits and push to the repository's default branch.
- GitHub's default branch is `master`; no `main` branch exists. Publish a normal fast-forward update to `master`.
- Group the changes into seven focused commits: test infrastructure, compromised admin credentials, JWT revocation, personal record authorization, demo seed isolation, HTTP regression tests, and documentation.
- Run `./mvnw clean verify` before committing and verify the committed tree before pushing. Leave unrelated local files and ignored audit artifacts out of the commits.
