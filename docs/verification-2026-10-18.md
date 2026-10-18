# FitCore functional verification — 18 October 2026

## Scope and environment

Playwright CLI exercised the local React frontend at `http://127.0.0.1:5173` against the real Spring Boot API at `http://localhost:8080`. All write checks used the separate local PostgreSQL database `fitcore_codex_verify`, never the production database. A workout-plan fixture was inserted only in that database to exercise plan assignment.

## Confirmed after fixes

- All 28 signed-in module pages rendered without uncaught JavaScript errors in the final route sweep.
- Registration, login, logout, session persistence after reload, and member exclusion from admin pages.
- Remember me disabled stores the session in sessionStorage rather than localStorage.
- Missing reset email is rejected without an API call; pending form submissions are disabled.
- A deliberately unresponsive local HTTP server caused an Axios timeout and the retry message. The production request limit is 60 seconds; the test temporarily shortened it to 200 milliseconds.
- Weight `79.4` saves and appears in history.
- Food logging saves and appears in the food log.
- Workout-plan assignment selects matching exercises; saving a workout updates workout history.
- Trainer cards open booking and a booking saves successfully.
- Membership purchase persists and the active plan's renewal button opens plan selection.
- Attendance check-in, check-out, and history.
- Admin edits to users, trainers, and membership plans persist through the API.
- Admin attendance renders and downloads a CSV containing member IDs, dates, and check-in/out times.
- Header page search, notifications, theme switching, and mobile menu navigation.
- A 390-pixel mobile viewport has no horizontal document overflow on the inspected attendance page.
- Active module-tab text remains visible; an unlayered global anchor rule previously overrode Tailwind text colors.

`npm run lint` and `npm run build` pass. `./mvnw clean test` passes with the test database configured: three tests, zero failures/errors, including two new disabled-trainer regressions. The backend smoke test requires PostgreSQL and the documented datasource/JWT environment variables; running it without configuration fails.

## Hosting and deferred work

Render service `FitCore` was inspected in the user-confirmed workspace. Logs showed a successful startup taking 317.693 seconds; requests during startup timed out or returned `503` with `hibernate-wake-error`. The health endpoint subsequently returned `200` and `{"status":"UP"}`. The Docker startup command now includes `-XX:TieredStopAtLevel=1`; its effect on the hosted cold start remains unverified until deployed. No paid plan upgrade or production configuration change was made.

Password-reset email delivery was explicitly deferred by the user. The page now explains that email recovery is not configured and directs the member to their gym administrator; it does not promise a reset email.

These source changes require deployment. The Vercel connector denied project access with `403 forbidden`, so the existing production frontend has not been updated or certified with these fixes.
