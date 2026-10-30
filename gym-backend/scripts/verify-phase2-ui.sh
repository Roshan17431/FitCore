#!/usr/bin/env bash
set -euo pipefail

# Disposable local verification only. No Maven, .env files, shared databases, or production settings.
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd -- "$script_dir/../.." && pwd)"
frontend_dir="$repo_dir/gym-frontend"
jar_path="$repo_dir/gym-backend/target/gym-0.0.1-SNAPSHOT.jar"
artifact_dir="$repo_dir/output/playwright/phase2-2026-10-20"
container_id=""
backend_pid=""
vite_pid=""
browser_started=false
runtime_dir=""

for executable in docker java node curl lsof mktemp; do
  command -v "$executable" >/dev/null 2>&1 || { echo "Missing required executable: $executable" >&2; exit 1; }
done
for port in 18082 5175; do
  if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "Refusing to run: loopback application port $port is already occupied. No resources were started." >&2
    exit 64
  fi
done
[[ -f "$jar_path" ]] || { echo 'Build the backend JAR before running this harness.' >&2; exit 1; }
[[ -f "$frontend_dir/node_modules/vite/bin/vite.js" ]] || { echo 'Install frontend dependencies before running this harness.' >&2; exit 1; }
docker info >/dev/null
java_bin="$(command -v java)"
node_bin="$(command -v node)"
if command -v playwright-cli >/dev/null 2>&1; then
  playwright_command=("$(command -v playwright-cli)")
else
  command -v npx >/dev/null 2>&1 || { echo 'Playwright CLI or npx is required.' >&2; exit 1; }
  playwright_command=(npx --yes @playwright/cli)
fi
mkdir -p -- "$artifact_dir"
runtime_dir="$(mktemp -d "${TMPDIR:-/tmp}/fitcore-phase2-ui.XXXXXX")"
browser_session="fitcore-phase2-local-$$"

stop_owned_pid() {
  local owned_pid="$1"
  [[ -n "$owned_pid" ]] || return 0
  if kill -0 "$owned_pid" 2>/dev/null; then
    kill -TERM "$owned_pid" 2>/dev/null || true
    for ((attempt = 0; attempt < 50; attempt++)); do
      kill -0 "$owned_pid" 2>/dev/null || break
      sleep 0.2
    done
    if kill -0 "$owned_pid" 2>/dev/null; then kill -KILL "$owned_pid" 2>/dev/null || true; fi
  fi
  wait "$owned_pid" 2>/dev/null || true
}
cleanup() {
  local result=$?
  local cleanup_failed=false
  trap - EXIT INT TERM
  if [[ "$browser_started" == true ]]; then
    "${playwright_command[@]}" -s="$browser_session" close >>"$artifact_dir/browser.log" 2>&1 || cleanup_failed=true
  fi
  stop_owned_pid "$vite_pid"
  stop_owned_pid "$backend_pid"
  if [[ -n "$container_id" ]]; then
    docker logs "$container_id" >"$artifact_dir/postgres.log" 2>&1 || true
    docker rm -f "$container_id" >/dev/null 2>&1 || cleanup_failed=true
  fi
  : >"$artifact_dir/cleanup.log"
  for owned_pid in "$vite_pid" "$backend_pid"; do
    if [[ -n "$owned_pid" ]] && kill -0 "$owned_pid" 2>/dev/null; then
      echo "Owned process $owned_pid is still running." >>"$artifact_dir/cleanup.log"
      cleanup_failed=true
    fi
  done
  for port in 18082 5175; do
    if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
      echo "Port $port remains occupied; no unknown process was killed." >>"$artifact_dir/cleanup.log"
      cleanup_failed=true
    else
      echo "Port $port is free." >>"$artifact_dir/cleanup.log"
    fi
  done
  if [[ -n "$container_id" ]]; then
    if docker inspect "$container_id" >/dev/null 2>&1; then
      echo 'Owned disposable PostgreSQL container still exists.' >>"$artifact_dir/cleanup.log"
      cleanup_failed=true
    else
      echo 'Owned disposable PostgreSQL container removed.' >>"$artifact_dir/cleanup.log"
    fi
  fi
  if [[ "$browser_started" == true ]]; then echo "Owned Playwright session close requested: $browser_session" >>"$artifact_dir/cleanup.log"; fi
  [[ -z "$runtime_dir" ]] || rm -rf -- "$runtime_dir"
  if [[ "$cleanup_failed" == true ]]; then
    echo 'Cleanup verification failed; inspect cleanup.log.' >&2
    result=1
  else
    echo 'Cleanup verified: owned server PIDs stopped, application ports free, disposable container removed.' >>"$artifact_dir/cleanup.log"
  fi
  cat "$artifact_dir/cleanup.log"
  exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

container_id="$(docker run --detach --rm --name "fitcore-phase2-ui-$$" \
  --label fitcore.purpose=phase2-disposable-ui-verification \
  --publish 127.0.0.1::5432 \
  --env POSTGRES_DB=fitcore_phase2_e2e \
  --env POSTGRES_USER=fitcore_phase2_e2e \
  --env POSTGRES_PASSWORD=fitcore-phase2-local-disposable-only \
  postgres:18-alpine)"
ready=false
for ((attempt = 0; attempt < 60; attempt++)); do
  if docker exec "$container_id" pg_isready --host 127.0.0.1 --port 5432 \
    --username fitcore_phase2_e2e --dbname fitcore_phase2_e2e >/dev/null 2>&1; then ready=true; break; fi
  sleep 1
done
[[ "$ready" == true ]] || { echo 'Disposable PostgreSQL did not become ready.' >&2; exit 1; }
binding="$(docker port "$container_id" 5432/tcp)"
[[ "$binding" =~ ^127\.0\.0\.1:([0-9]+)$ ]] || { echo 'Database binding is not exclusively loopback.' >&2; exit 1; }
postgres_port="${BASH_REMATCH[1]}"

docker exec -i "$container_id" psql --username fitcore_phase2_e2e --dbname fitcore_phase2_e2e \
  --set ON_ERROR_STOP=1 --set "marker=fitcore-disposable:$container_id" <<'SQL'
COMMENT ON DATABASE fitcore_phase2_e2e IS :'marker';
SQL

# The working directory is empty and config import is disabled; inherited application env is removed.
(
  cd -- "$runtime_dir"
  exec env -i PATH="$PATH" FITCORE_TENANCY_TEST_TOKEN="$container_id" "$java_bin" -jar "$jar_path" \
    --server.address=127.0.0.1 --server.port=18082 \
    --spring.config.import= --spring.profiles.active=phase2-local-e2e \
    --spring.datasource.url="jdbc:postgresql://127.0.0.1:${postgres_port}/fitcore_phase2_e2e" \
    --spring.datasource.username=fitcore_phase2_e2e \
    --spring.datasource.password=fitcore-phase2-local-disposable-only \
    --spring.flyway.enabled=true --spring.jpa.hibernate.ddl-auto=validate --spring.jpa.show-sql=false \
    --spring.sql.init.mode=never --aws.s3.enabled=false \
    --jwt.secret=fitcore-phase2-public-local-e2e-signing-key-for-disposable-verification-only-2026 \
    --jwt.expiration-ms=3600000 --cors.allowed-origins=http://127.0.0.1:5175
) >"$artifact_dir/backend.log" 2>&1 &
backend_pid=$!
ready=false
for ((attempt = 0; attempt < 90; attempt++)); do
  kill -0 "$backend_pid" 2>/dev/null || { echo 'Owned backend exited during startup; inspect backend.log.' >&2; exit 1; }
  if curl --noproxy '*' --fail --silent --max-time 2 http://127.0.0.1:18082/api/health >"$artifact_dir/backend-health.json"; then ready=true; break; fi
  sleep 1
done
[[ "$ready" == true ]] || { echo 'Owned backend health did not become ready.' >&2; exit 1; }
[[ "$(lsof -nP -a -p "$backend_pid" -iTCP:18082 -sTCP:LISTEN -Fn | sed -n '/^n/p')" == 'n127.0.0.1:18082' ]] || { echo 'Owned backend is not listening exclusively on loopback.' >&2; exit 1; }

# Reuse only the documented PUBLIC demo BCrypt fixture, never local credentials or an env file.
public_fixture_hash="$("$node_bin" - "$repo_dir/gym-backend/src/main/resources/db/demo-data.sql" <<'NODE'
const fs = require('node:fs');
const match = fs.readFileSync(process.argv[2], 'utf8').match(/\$2[aby]\$\d{2}\$[./A-Za-z0-9]{53}/);
if (!match) throw new Error('Tracked public demo BCrypt fixture is missing');
process.stdout.write(match[0]);
NODE
)"
docker exec -i "$container_id" psql --username fitcore_phase2_e2e --dbname fitcore_phase2_e2e \
  --set ON_ERROR_STOP=1 --set "public_hash=$public_fixture_hash" >"$artifact_dir/seed.log" 2>&1 <<'SQL'
BEGIN;
INSERT INTO gyms (id, name, slug, status, created_at, updated_at) VALUES
('90000000-0000-0000-0000-000000000001', 'Browser fixture gym', 'browser-fixture', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
-- Public synthetic password/hash used only by this disposable browser fixture.
INSERT INTO users (id, gym_id, full_name, email, password_hash, role, enabled, created_at, updated_at) VALUES
('90000000-0000-0000-0000-000000000002', '90000000-0000-0000-0000-000000000001', 'Browser Fixture Owner', 'browser-owner@fixture.invalid', '$2y$10$rCG7lNQmE1JLMX3lAfxPY.xl3agde2dbjIFIhsAgWau.M.4e6GwFm', 'OWNER', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
UPDATE gyms SET primary_owner_user_id = '90000000-0000-0000-0000-000000000002' WHERE id = '90000000-0000-0000-0000-000000000001';
INSERT INTO users (id, full_name, email, password_hash, role, fitness_goal, enabled, created_at, updated_at, gym_id) VALUES
('22222222-2222-2222-2222-222222222222', 'Phase 2 Member', 'phase2-member@e2e.test', :'public_hash', 'MEMBER', 'Local verification', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '90000000-0000-0000-0000-000000000001'),
('33333333-3333-3333-3333-333333333333', 'Phase 2 Other Member', 'phase2-other@e2e.test', :'public_hash', 'MEMBER', 'Local verification', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '90000000-0000-0000-0000-000000000001'),
('55555555-5555-5555-5555-555555555555', 'Alpha Coach', 'phase2-alpha@e2e.test', :'public_hash', 'TRAINER', 'Local verification', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '90000000-0000-0000-0000-000000000001'),
('66666666-6666-6666-6666-666666666666', 'Beta Coach', 'phase2-beta@e2e.test', :'public_hash', 'TRAINER', 'Local verification', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '90000000-0000-0000-0000-000000000001');
INSERT INTO membership_plans (id, name, price, duration_months, recommended, gym_id) VALUES
(1, 'Basic', 29, 1, false, '90000000-0000-0000-0000-000000000001'),
(2, 'Premium', 79, 3, true, '90000000-0000-0000-0000-000000000001'),
(3, 'Elite', 149, 6, false, '90000000-0000-0000-0000-000000000001');
INSERT INTO membership_plan_features (plan_id, feature, gym_id) VALUES
(1, 'Gym floor access', '90000000-0000-0000-0000-000000000001'),
(2, 'Everything in Basic', '90000000-0000-0000-0000-000000000001'),
(3, 'Everything in Premium', '90000000-0000-0000-0000-000000000001');
INSERT INTO trainer_profiles (id, user_id, specialization, experience_years, bio, photo_url, gym_id) VALUES
(1, '55555555-5555-5555-5555-555555555555', 'Strength', 5, 'Local Alpha fixture', 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI4IiBoZWlnaHQ9IjgiPjxwYXRoIGZpbGw9IiMxMGI5ODEiIGQ9Ik0wIDBoOHY4SDB6Ii8+PC9zdmc+', '90000000-0000-0000-0000-000000000001'),
(2, '66666666-6666-6666-6666-666666666666', 'Mobility', 4, 'Local Beta fixture', 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI4IiBoZWlnaHQ9IjgiPjxwYXRoIGZpbGw9IiMxMGI5ODEiIGQ9Ik0wIDBoOHY4SDB6Ii8+PC9zdmc+', '90000000-0000-0000-0000-000000000001');
SELECT setval(pg_get_serial_sequence('membership_plans', 'id'), 3, true);
SELECT setval(pg_get_serial_sequence('trainer_profiles', 'id'), 2, true);
COMMIT;
SELECT 'initial_memberships=' || count(*) FROM active_memberships;
SELECT 'initial_sessions=' || count(*) FROM trainer_sessions;
SQL
unset public_fixture_hash

# Validate the canonical aggregate audit only against this disposable database.
: >"$artifact_dir/local-audit-validation.log"
docker exec -i "$container_id" psql --username fitcore_phase2_e2e --dbname fitcore_phase2_e2e \
  --set ON_ERROR_STOP=1 <"$repo_dir/docs/sql/phase2-readonly-audit.sql" >>"$artifact_dir/local-audit-validation.log" 2>&1

# Keep the real project config/aliases, but disable Vite env-file loading and force loopback.
"$node_bin" - "$frontend_dir/vite.config.js" "$runtime_dir/vite.phase2.config.mjs" \
  "$frontend_dir/scripts/phase2-integrated.pw.js" "$runtime_dir/phase2-integrated.pw.js" "$artifact_dir" <<'NODE'
const fs = require('node:fs');
const { pathToFileURL } = require('node:url');
const [, , original, configPath, sourcePath, runnerPath, artifacts] = process.argv;
fs.writeFileSync(configPath, `import base from ${JSON.stringify(pathToFileURL(original).href)};\nexport default { ...base, envDir: false, server: { ...base.server, host: '127.0.0.1', port: 5175, strictPort: true } };\n`);
const source = fs.readFileSync(sourcePath, 'utf8');
const portable = source.replace(/const output = '[^']+';/, `const output = ${JSON.stringify(artifacts)};`);
if (source === portable) throw new Error('Integrated runner artifact directory substitution failed');
fs.writeFileSync(runnerPath, portable);
NODE
(
  cd -- "$frontend_dir"
  exec env -i PATH="$PATH" VITE_API_BASE_URL=http://127.0.0.1:18082/api \
    "$node_bin" "$frontend_dir/node_modules/vite/bin/vite.js" --config "$runtime_dir/vite.phase2.config.mjs"
) >"$artifact_dir/vite.log" 2>&1 &
vite_pid=$!
ready=false
for ((attempt = 0; attempt < 60; attempt++)); do
  kill -0 "$backend_pid" 2>/dev/null || { echo 'Owned backend stopped unexpectedly.' >&2; exit 1; }
  kill -0 "$vite_pid" 2>/dev/null || { echo 'Owned Vite exited during startup; inspect vite.log.' >&2; exit 1; }
  if curl --noproxy '*' --fail --silent --max-time 2 http://127.0.0.1:5175/login >/dev/null; then ready=true; break; fi
  sleep 1
done
[[ "$ready" == true ]] || { echo 'Owned Vite did not become ready.' >&2; exit 1; }
[[ "$(lsof -nP -a -p "$vite_pid" -iTCP:5175 -sTCP:LISTEN -Fn | sed -n '/^n/p')" == 'n127.0.0.1:5175' ]] || { echo 'Owned Vite is not listening exclusively on loopback.' >&2; exit 1; }

echo "Running real local browser checks with PostgreSQL 18 on disposable loopback port $postgres_port."
browser_started=true
(
  cd -- "$frontend_dir"
  "${playwright_command[@]}" -s="$browser_session" open http://127.0.0.1:5175/login
) >"$artifact_dir/browser.log" 2>&1
if ! (
  cd -- "$frontend_dir"
  "${playwright_command[@]}" -s="$browser_session" run-code --filename="$runtime_dir/phase2-integrated.pw.js"
) >"$artifact_dir/integrated.log" 2>&1; then
  echo 'Integrated browser verification failed; inspect integrated.log and integrated-failure*.png.' >&2
  tail -n 30 "$artifact_dir/integrated.log" >&2
  exit 1
fi
# Optional Phase 3 UI checks use only this harness-owned disposable database and services.
if [[ "${FITCORE_VERIFY_PHASE3_UI:-false}" == true ]]; then
  # Final schema requires explicit gym ownership; do not use unaffiliated registration.
  # This public synthetic BCrypt fixture matches Phase3-Local-Only!Verify26 in the browser runner.
  docker exec -i "$container_id" psql --username fitcore_phase2_e2e --dbname fitcore_phase2_e2e \
    --set ON_ERROR_STOP=1 >"$artifact_dir/phase3-fixture.log" 2>&1 <<'SQL'
INSERT INTO users (id, gym_id, full_name, email, password_hash, role, enabled, created_at, updated_at) VALUES
('90000000-0000-0000-0000-000000000003', '90000000-0000-0000-0000-000000000001', 'Local Phase 3 Admin', 'phase3-admin@example.invalid', '$2y$10$rCG7lNQmE1JLMX3lAfxPY.xl3agde2dbjIFIhsAgWau.M.4e6GwFm', 'ADMIN', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
SQL
  if ! (
    cd -- "$frontend_dir"
    "${playwright_command[@]}" -s="$browser_session" run-code --filename="$frontend_dir/scripts/phase3-integrated.pw.js"
  ) >"$artifact_dir/phase3-integrated.log" 2>&1; then
    echo 'Phase 3 integrated UI verification failed; inspect phase3-integrated.log.' >&2
    tail -n 15 "$artifact_dir/phase3-integrated.log" >&2
    exit 1
  fi
fi
kill -0 "$backend_pid" && kill -0 "$vite_pid"
docker exec -i "$container_id" psql --username fitcore_phase2_e2e --dbname fitcore_phase2_e2e \
  --set ON_ERROR_STOP=1 >"$artifact_dir/db-final-state.log" 2>&1 <<'SQL'
SELECT status, count(*) FROM active_memberships GROUP BY status ORDER BY status;
SELECT am.id, p.name, am.start_date, am.end_date, am.status FROM active_memberships am JOIN membership_plans p ON p.id = am.plan_id ORDER BY am.id;
SELECT ts.id, tp.id AS trainer_id, ts.session_date, ts.start_time, ts.end_time, ts.status FROM trainer_sessions ts JOIN trainer_profiles tp ON tp.id = ts.trainer_id ORDER BY ts.id;
DO $$ BEGIN
  IF (SELECT count(*) FROM active_memberships) <> 2
     OR (SELECT count(*) FROM active_memberships WHERE status = 'ACTIVE') <> 1
     OR (SELECT count(*) FROM active_memberships WHERE status = 'CANCELLED') <> 1
     OR (SELECT count(*) FROM trainer_sessions) <> 2 THEN
    RAISE EXCEPTION 'Integrated final database counts differ from expected periods/sessions';
  END IF;
END $$;
SQL
echo "Integrated browser checks and persisted PostgreSQL counts passed. Artifacts: $artifact_dir"
