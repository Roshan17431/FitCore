# Security fixes — 19 October 2026

These changes address audit issues 1–3. They do not change membership calculations, booking availability, UI design, images, email delivery, or other features.

## Authentication and existing accounts

- Administrator accounts using the published demo password are rejected during user lookup, regardless of email address or BCrypt salt. This covers database rows created before this patch as well as future rows. Existing JWTs for those accounts are also rejected.
- Normal administrators using private passwords can still log in. Rotating a demo administrator to a private password restores login.
- New tokens contain a keyed credential version, not the password or its stored BCrypt hash. Password rotation invalidates previously issued tokens, so an attacker's token cannot regain access when a demo password is replaced. Legacy tokens without this binding are rejected; existing users must sign in again after rollout.
- Each JWT request reloads the user from the database. Validation now checks that the account is enabled, unlocked, unexpired, and has unexpired credentials, in addition to checking token identity, signature, and expiry. Disabling an account therefore prevents subsequent reads and writes with an existing token. Current database roles also govern authorization.

## Personal record authorization

Authorization runs before personal service operations using [Spring Security method checks](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html). A caller must own the target records or be an administrator. There is no trainer/member assignment relationship granting access in the current application, so trainers have the same ownership restriction as members.

| Resource | Read endpoints | Write endpoints |
| --- | --- | --- |
| Profile | `/api/users/{id}` | User management remains admin-only |
| Dashboard | `/api/dashboard/{userId}` | — |
| Memberships | `/api/memberships/user/{userId}`, `/api/memberships/user/{userId}/active` | `POST /api/memberships/subscribe/{userId}/{planId}` |
| Workouts | `/api/workouts/logs/{userId}` | `POST /api/workouts/logs/{userId}` |
| Nutrition | `/api/nutrition/dashboard/{userId}`, `/api/nutrition/goal/{userId}`, `/api/nutrition/logs/{userId}` | `POST /api/nutrition/goal/{userId}`, `POST /api/nutrition/log/{userId}` |
| Progress | `/api/tracking/progress/{userId}` | `POST /api/tracking/progress/{userId}` |
| Attendance | `/api/tracking/attendance/{userId}` | `POST /api/tracking/attendance/check-in/{userId}`, `PUT /api/tracking/attendance/check-out/{attendanceId}` |
| Trainer sessions | `/api/trainers/sessions/{memberId}` | `POST /api/trainers/book/{memberId}/{trainerId}` |
| Notifications | `/api/notifications/{userId}` | — |

Attendance checkout resolves the attendance record's owner from the database; knowing its numeric ID does not grant access. Body-supplied user/member IDs cannot replace the authorized path owner. Unauthorized personal access returns 403, unauthenticated/disabled requests return 401, and denied writes leave records unchanged. Shared catalog reads remain available to authenticated users. Existing `/api/admin/**` restrictions remain in force.

## Production and demo configuration

- Automatic SQL seeding defaults to `never`; the Render manifest and environment example use that value too.
- The original demo script is now `gym-backend/src/main/resources/db/demo-data.sql`, outside Spring Boot's automatic `data.sql` location. A legacy `SQL_INIT_MODE=always` setting alone can no longer discover and reload it.
- Only an explicit `demo` profile selects that script. Use it solely against a disposable PostgreSQL demonstration database. The public administrator password remains blocked even in that profile.

Before a future deployment, prepare an intended administrator with a private, unique password using trusted database administration tooling. Store a BCrypt hash in `users.password_hash`, not a plaintext password. Keep production `SQL_INIT_MODE=never`, do not enable the demo profile or explicitly select the demo SQL, and build with `./mvnw clean verify` so obsolete build resources are removed. The patch does not create a new bootstrap account or embed replacement credentials.

The security implementation was verified locally before publication. No production database account was changed. A subsequent request authorized publishing the changes in at most 13 commits to the repository's default branch, `master`; connected hosting may deploy automatically after that push. Live deployment status must be checked separately.

## Automated verification

Run in `gym-backend`:

```sh
./mvnw clean verify
```

Tests use an isolated, in-memory H2 database and a test-only JWT secret; they do not use `.env` or connect to development/production PostgreSQL. The H2 dependency is test-scoped.

`SecurityRegressionTest` exercises actual signed JWTs, the filter chain, controllers, method authorization, services, and JPA repositories without mocked authentication or mocked business services. The endpoint matrix checks non-owner rejection for both MEMBER and TRAINER, authorized owner/admin success, unauthenticated rejection, unchanged database state after denied requests, body-ID spoofing, disabled tokens, admin credential rotation, role changes, and shared catalogs.

Before the fix, its 142 cases produced 47 assertion failures and zero test errors, reproducing all three vulnerabilities. After the fix, those cases pass. `ProductionSeedIsolationTest` starts with SQL initialization explicitly enabled and proves that ordinary startup creates no demo users. The three existing backend tests are retained.

The identical, untracked duplicate `TrainerServiceTest 2.java` prevented compilation. It was preserved at `output/playwright/security-fixes-2026-10-19/backups/TrainerServiceTest 2.java`, outside the source tree. No test content was discarded.

The independent review identified potential token reuse after demo-admin password rotation. Two additional tests first reproduced that gap, then verified credential binding and legacy-token rejection. All 11 admin read/write routes are also checked against both non-admin roles, and the published-password test covers both the original seed hash and a newly salted hash.

The optional S3 image API is disabled by default and was left unchanged. It has no asset-ownership model; these fixes and authorization claims concern the database-backed personal endpoints above and existing admin routes.

Final clean verification: **172 tests, 0 failures, 0 errors, 0 skipped**, followed by a successful executable JAR build. This includes 168 security HTTP regression cases, the seed-isolation test, and the 3 existing tests. A valid private administrator is also tested performing an admin read and update.

Preliminary non-clean test runs encountered stale duplicate `.class` artifacts in the generated build directory, including a bad-magic-number error. Clean builds remove those generated artifacts. Final results come from `./mvnw clean verify`; no such failures remain in that run.

Test logs and machine-readable results are under `output/playwright/security-fixes-2026-10-19/`.
