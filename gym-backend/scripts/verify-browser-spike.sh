#!/usr/bin/env bash
set -euo pipefail
[[ $# == 0 ]] || exit 64
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd -- "$script_dir/../.." && pwd)"
cd -- "$repo_dir"
for port in 18443 18444 18445; do
  ! lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null || { echo "Spike loopback port $port occupied; no resources started." >&2; exit 64; }
done
artifact="$repo_dir/output/playwright/browser-spike"
mkdir -p "$artifact"
rm -f "$artifact/results.json" "$artifact/shared-instances.json" "$artifact/recovery-results.json" "$artifact/gym-ui-results.json" "$artifact/native-restart.json" "$artifact/policy-ui-results.json"
runtime="$(mktemp -d "${TMPDIR:-/tmp}/fitcore-browser-spike.XXXXXX")"
chmod 700 "$runtime"
container_id=""; backend_pid=""; second_backend_pid=""; bff_pid=""; browser_started=false; restart_started=false; engine_started=false
session="fitcore-browser-spike-$$"
restart_session="fitcore-browser-restart-$$"
engine_session="fitcore-browser-engine-$$"
cleanup(){
  local result=$?
  trap - EXIT INT TERM
  if [[ "$restart_started" == true ]]; then playwright-cli -s="$restart_session" close >>"$artifact/restart-browser.log" 2>&1 || result=1; fi
  if [[ "$engine_started" == true ]]; then playwright-cli -s="$engine_session" close >>"$artifact/engines.log" 2>&1 || result=1; fi
  if [[ "$browser_started" == true ]]; then playwright-cli -s="$session" close >>"$artifact/browser.log" 2>&1 || result=1; fi
  for pid in "$bff_pid" "$backend_pid" "$second_backend_pid"; do
    if [[ -n "$pid" ]]; then kill -TERM "$pid" 2>/dev/null || true; wait "$pid" 2>/dev/null || true; fi
  done
  if [[ -n "$container_id" ]]; then docker rm -f "$container_id" >/dev/null || result=1; fi
  rm -rf -- "$runtime"
  exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
container_id="$(docker run --detach --rm --label fitcore.purpose=browser-spike-disposable --publish 127.0.0.1::5432 --env POSTGRES_DB=fitcore_phase2 --env POSTGRES_USER=fitcore_phase2_test --env POSTGRES_PASSWORD=fitcore-disposable-phase2-only postgres:18-alpine)"
for ((attempt=0;attempt<60;attempt++));do
  if docker exec "$container_id" pg_isready --host 127.0.0.1 -U fitcore_phase2_test -d fitcore_phase2 >/dev/null 2>&1;then break;fi
  sleep 1
done
binding="$(docker port "$container_id" 5432/tcp)"
[[ "$binding" =~ ^127\.0\.0\.1:([0-9]+)$ ]] || exit 1
pgport="${BASH_REMATCH[1]}"
docker exec -i "$container_id" psql -U fitcore_phase2_test -d fitcore_phase2 --set ON_ERROR_STOP=1 --set "marker=fitcore-disposable:$container_id" <<'SQL'
COMMENT ON DATABASE fitcore_phase2 IS :'marker';
SQL
# Dependency classpath only, then marked fixture executes Flyway against this freshly created container.
(cd gym-backend && env -i PATH="$PATH" HOME="$HOME" JAVA_HOME="${JAVA_HOME:-}" ./mvnw -q dependency:build-classpath -Dmdep.outputFile="$runtime/classpath")
env -i PATH="$PATH" HOME="$HOME" FITCORE_POSTGRES_TEST_URL="jdbc:postgresql://127.0.0.1:$pgport/fitcore_phase2" \
FITCORE_POSTGRES_TEST_USERNAME=fitcore_phase2_test FITCORE_POSTGRES_TEST_PASSWORD=fitcore-disposable-phase2-only \
FITCORE_TENANCY_TEST_TOKEN="$container_id" \
java -cp "$repo_dir/gym-backend/target/test-classes:$repo_dir/gym-backend/target/classes:$(cat "$runtime/classpath")" com.fitcore.gym.tenancy.BrowserSpikeFixture >"$artifact/fixture.log" 2>&1
openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj /CN=fitcore-local-spike -addext subjectAltName=IP:127.0.0.1 -addext basicConstraints=critical,CA:TRUE -keyout "$runtime/tls.key" -out "$runtime/tls.crt" >"$artifact/tls-creation.log" 2>&1
openssl pkcs12 -export -in "$runtime/tls.crt" -inkey "$runtime/tls.key" -out "$runtime/tls.p12" -passout pass:synthetic-local-only >/dev/null 2>&1
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$runtime/proof.key" >/dev/null 2>&1
openssl pkey -in "$runtime/proof.key" -pubout -out "$runtime/proof.pub" >/dev/null 2>&1
start_backend(){
 cd "$runtime"
 exec env -i PATH="$PATH" FITCORE_TENANCY_TEST_TOKEN="$container_id" java -jar "$repo_dir/gym-backend/target/gym-0.0.1-SNAPSHOT.jar" \
 --server.address=127.0.0.1 --server.port="$1" --server.ssl.enabled=true --server.ssl.key-store="$runtime/tls.p12" --server.ssl.key-store-type=PKCS12 --server.ssl.key-store-password=synthetic-local-only \
 --spring.config.import= --spring.datasource.url="jdbc:postgresql://127.0.0.1:$pgport/fitcore_phase2" \
 --spring.datasource.username=browser_spike_runtime --spring.datasource.password=synthetic-browser-runtime-only \
 --spring.flyway.enabled=false --spring.jpa.hibernate.ddl-auto=validate --spring.sql.init.mode=never --aws.s3.enabled=false \
 --jwt.secret=synthetic-spike-credential-version-only-key-not-for-production-2026 --fitcore.browser.spike-enabled=true \
 --fitcore.browser.public-origin=https://127.0.0.1:18444 --fitcore.browser.gateway-public-key="$(cat "$runtime/proof.pub")"
}
(start_backend 18443) >"$artifact/backend.log" 2>&1 &
backend_pid=$!
(start_backend 18445) >"$artifact/backend-second.log" 2>&1 &
second_backend_pid=$!
ready=false
for ((attempt=0;attempt<60;attempt++));do
 kill -0 "$backend_pid" || { tail -30 "$artifact/backend.log";exit 1; }
 if curl --noproxy '*' --cacert "$runtime/tls.crt" --silent --fail --max-time 2 https://127.0.0.1:18443/api/health >/dev/null;then ready=true;break;fi
 sleep 1
done
[[ "$ready" == true ]] || exit 1
ready=false
for ((attempt=0;attempt<60;attempt++));do
 kill -0 "$second_backend_pid" || { tail -30 "$artifact/backend-second.log";exit 1; }
 if curl --noproxy '*' --cacert "$runtime/tls.crt" --silent --fail --max-time 2 https://127.0.0.1:18445/api/health >/dev/null;then ready=true;break;fi
 sleep 1
done
[[ "$ready" == true ]] || exit 1
node - "$repo_dir/gym-frontend/vite.config.js" "$runtime" <<'NODE'
const fs=require('node:fs'),{pathToFileURL}=require('node:url');
const [,,original,dir]=process.argv;
fs.writeFileSync(`${dir}/vite.config.mjs`,`import base from ${JSON.stringify(pathToFileURL(original).href)};export default {...base,envDir:false};`);
fs.writeFileSync(`${dir}/bff.json`,JSON.stringify({upstream:'https://127.0.0.1:18443',publicOrigin:'https://127.0.0.1:18444',privateKeyPem:fs.readFileSync(`${dir}/proof.key`,'utf8'),caPem:fs.readFileSync(`${dir}/tls.crt`,'utf8'),tlsKeyPem:fs.readFileSync(`${dir}/tls.key`,'utf8'),tlsCertPem:fs.readFileSync(`${dir}/tls.crt`,'utf8'),staticRoot:`${dir}/dist`,fixtureControl:require('node:crypto').randomBytes(32).toString('hex')}));
fs.writeFileSync(`${dir}/playwright.json`,JSON.stringify({browser:{contextOptions:{ignoreHTTPSErrors:true}}}));
NODE
(
 cd gym-frontend
 env -i PATH="$PATH" VITE_AUTH_TRANSPORT=cookie-spike node node_modules/vite/bin/vite.js build --config "$runtime/vite.config.mjs" --outDir "$runtime/dist" --emptyOutDir
) >"$artifact/build.log" 2>&1
node - "$repo_dir/gym-frontend/scripts/browser-spike.pw.js" "$runtime" <<'NODE'
const fs=require('node:fs');const [,,source,dir]=process.argv;const config=JSON.parse(fs.readFileSync(`${dir}/bff.json`));fs.writeFileSync(`${dir}/runner.js`,fs.readFileSync(source,'utf8').replace('@FIXTURE_CONTROL@',config.fixtureControl));
NODE
node gym-frontend/scripts/browser-spike-shared.mjs "$runtime/bff.json" "$artifact/shared-instances.json" >"$artifact/shared-instances.log" 2>&1
# Shared admission tests and UI proofs use separate synthetic request budgets.
docker exec "$container_id" psql -U fitcore_phase2_test -d fitcore_phase2 --set ON_ERROR_STOP=1 -c 'delete from security_rate_limits' >/dev/null
node gym-frontend/scripts/browser-spike-server.mjs "$runtime/bff.json" >"$artifact/bff.log" 2>&1 &
bff_pid=$!
for ((attempt=0;attempt<30;attempt++));do
 if curl --noproxy '*' --cacert "$runtime/tls.crt" --silent --fail --max-time 2 https://127.0.0.1:18444/login >/dev/null;then break;fi
 sleep 1
done
browser_started=true
playwright-cli -s="$session" open https://127.0.0.1:18444/login --config "$runtime/playwright.json" >"$artifact/browser.log" 2>&1
playwright-cli -s="$session" run-code --filename="$runtime/runner.js" >"$artifact/integrated.log" 2>&1 || { tail -40 "$artifact/integrated.log";exit 1; }
# Results are structured and independently gated, not merely a successful browser command.
node - "$artifact/integrated.log" "$artifact/results.json" <<'NODE'
const fs=require('node:fs');const [,,input,output]=process.argv;const text=fs.readFileSync(input,'utf8');const match=text.match(/### Result\s*\n([\s\S]*?)(?=\n###|$)/);if(!match)throw Error('No structured browser result');const result=JSON.parse(match[1]);if(!result.checks?.length)throw Error('Empty browser checks');fs.writeFileSync(output,JSON.stringify(result,null,2));
NODE
# Every additional runner uses the same owned DB and exact HTTPS BFF, with isolated browsers.
record_json(){
 node - "$1" "$2" <<'NODE'
const fs=require('node:fs');const [,,input,output]=process.argv;const text=fs.readFileSync(input,'utf8');const match=text.match(/### Result\s*\n([\s\S]*?)(?=\n###|$)/);if(!match)throw Error('No structured browser result');fs.writeFileSync(output,JSON.stringify(JSON.parse(match[1]),null,2));
NODE
}
prepare_runner(){
 node - "$1" "$2" "$runtime/bff.json" "${3:-}" <<'NODE'
const fs=require('node:fs');const [,,source,output,configFile,stage]=process.argv;const config=JSON.parse(fs.readFileSync(configFile));fs.writeFileSync(output,fs.readFileSync(source,'utf8').replace('@FIXTURE_CONTROL@',config.fixtureControl).replace('@RESTART_STAGE@',stage).replace('@POLICY_STAGE@',stage));
NODE
}
prepare_runner gym-frontend/scripts/cookie-recovery.pw.js "$runtime/recovery.js"
for engine in chrome firefox webkit; do
 docker exec "$container_id" psql -U fitcore_phase2_test -d fitcore_phase2 --set ON_ERROR_STOP=1 -c 'delete from security_rate_limits' >/dev/null
 engine_started=true
 if [[ "$engine" == firefox && "$(uname -s)" == Darwin ]]; then
   # macOS27 protects the real Firefox app-data directory even with an isolated
   # profile. Use only harness-owned app data; never change privacy permissions.
   mkdir -p "$runtime/firefox-appdata" "$runtime/firefox-local-appdata"
   MOZ_APP_DATA="$runtime/firefox-appdata" MOZ_LOCAL_APP_DATA="$runtime/firefox-local-appdata" playwright-cli -s="$engine_session" open https://127.0.0.1:18444/login --browser="$engine" --config "$runtime/playwright.json" >>"$artifact/engines.log" 2>&1
 else
   playwright-cli -s="$engine_session" open https://127.0.0.1:18444/login --browser="$engine" --config "$runtime/playwright.json" >>"$artifact/engines.log" 2>&1
 fi
 playwright-cli -s="$engine_session" run-code --filename="$runtime/recovery.js" >"$artifact/recovery-$engine.log" 2>&1
 record_json "$artifact/recovery-$engine.log" "$artifact/recovery-$engine.json"
 playwright-cli -s="$engine_session" close >>"$artifact/engines.log" 2>&1
 engine_started=false
done
node - "$artifact" <<'NODE'
const fs=require('node:fs');const dir=process.argv[2];fs.writeFileSync(`${dir}/recovery-results.json`,JSON.stringify({engines:['chrome','firefox','webkit'].map(engine=>({engine,report:JSON.parse(fs.readFileSync(`${dir}/recovery-${engine}.json`))}))},null,2));
NODE
docker exec "$container_id" psql -U fitcore_phase2_test -d fitcore_phase2 --set ON_ERROR_STOP=1 -c 'delete from security_rate_limits' >/dev/null
prepare_runner gym-frontend/scripts/cookie-gym-ui.pw.js "$runtime/gym-ui.js"
playwright-cli -s="$session" run-code --filename="$runtime/gym-ui.js" >"$artifact/gym-ui.log" 2>&1
record_json "$artifact/gym-ui.log" "$artifact/gym-ui-results.json"
# Current-account policy boundaries are staged by root-owned CLI only. No HTTP
# fixture route can mutate the DB. Every mutation checks this created DB marker.
policy_mutation(){
 docker exec -i "$container_id" psql -U fitcore_phase2_test -d fitcore_phase2 --set ON_ERROR_STOP=1 --set "expected_marker=fitcore-disposable:$container_id" --set "scenario=$1" --set "fixturegym=$2" --set "prepared_family=$3" <<'SQL'
BEGIN;
-- Any false/NULL prerequisite raises a real SQL error; ON_ERROR_STOP returns
-- nonzero and disconnect rolls back this entire disposable staging transaction.
SELECT 1/(coalesce(:'scenario' IN ('expired','password','role','disabled'),false)::int) AS valid_scenario;
SELECT 1/(coalesce(:'fixturegym' IN ('1','2'),false)::int) AS valid_fixture_gym;
SELECT 1/(coalesce((SELECT shobj_description(oid,'pg_database') = :'expected_marker'
 FROM pg_database WHERE datname=current_database()),false)::int) AS owned_database;
SELECT 1/((count(*)=1)::int) AS prepared_current FROM browser_sessions s JOIN browser_session_families f ON f.id=s.family_id JOIN users u ON u.id=s.user_id AND u.gym_id=s.gym_id
 WHERE f.id=:'prepared_family'::uuid AND NOT s.used AND s.kind='AUTH' AND s.family_revision=f.revision AND f.pending_hash IS NULL
 AND u.email=(CASE WHEN :'scenario'='role' THEN 'trainer' ELSE 'member' END)||:'fixturegym'||'@fixture.invalid'
 AND u.gym_id=('10000000-0000-0000-0000-'||lpad(:'fixturegym',12,'0'))::uuid;
SELECT :'scenario'='expired' AS expire_case, :'scenario'='password' AS password_case, :'scenario'='role' AS role_case, :'scenario'='disabled' AS disabled_case \gset
\if :expire_case
 WITH changed AS (
  UPDATE browser_sessions s SET created_at=least(s.created_at,now()-interval '31 minutes'), expires_at=now()-interval '1 second'
  FROM browser_session_families f, users u WHERE s.family_id=f.id AND s.user_id=u.id AND s.gym_id=u.gym_id
   AND u.email='member'||:'fixturegym'||'@fixture.invalid' AND u.gym_id=('10000000-0000-0000-0000-'||lpad(:'fixturegym',12,'0'))::uuid
   AND f.id=:'prepared_family'::uuid AND s.kind='AUTH' AND NOT s.used AND s.family_revision=f.revision AND f.pending_hash IS NULL
  RETURNING s.family_id
 ) SELECT 1/((count(*)=1)::int) AS changed_one FROM changed;
\endif
\if :password_case
 WITH changed AS (
  UPDATE users u SET password_hash=d.password_hash FROM users d
  WHERE u.email='member'||:'fixturegym'||'@fixture.invalid' AND d.email='passworddonor'||:'fixturegym'||'@fixture.invalid'
   AND u.gym_id=d.gym_id AND u.gym_id=('10000000-0000-0000-0000-'||lpad(:'fixturegym',12,'0'))::uuid
   AND d.id=('50000000-0000-0000-0000-'||lpad(:'fixturegym',12,'0'))::uuid
  RETURNING u.id
 ) SELECT 1/((count(*)=1)::int) AS changed_one FROM changed;
\endif
\if :role_case
 WITH changed AS (
  UPDATE users SET role='MEMBER',authz_version=authz_version+1
  WHERE email='trainer'||:'fixturegym'||'@fixture.invalid' AND role='TRAINER'
   AND gym_id=('10000000-0000-0000-0000-'||lpad(:'fixturegym',12,'0'))::uuid RETURNING id
 ) SELECT 1/((count(*)=1)::int) AS changed_one FROM changed;
\endif
\if :disabled_case
 WITH changed AS (
  UPDATE users SET enabled=false WHERE email='member'||:'fixturegym'||'@fixture.invalid' AND enabled
   AND gym_id=('10000000-0000-0000-0000-'||lpad(:'fixturegym',12,'0'))::uuid RETURNING id
 ) SELECT 1/((count(*)=1)::int) AS changed_one FROM changed;
\endif
COMMIT;
SQL
}
for gym in 1 2; do
 for policy in expired password role disabled; do
  docker exec "$container_id" psql -U fitcore_phase2_test -d fitcore_phase2 --set ON_ERROR_STOP=1 -c 'delete from security_rate_limits' >/dev/null
  prepare_runner gym-frontend/scripts/cookie-policy-ui.pw.js "$runtime/policy-prepare.js" "gym-$gym-$policy-prepare"
  prepare_runner gym-frontend/scripts/cookie-policy-ui.pw.js "$runtime/policy-verify.js" "gym-$gym-$policy-verify"
  engine_started=true
  playwright-cli -s="$engine_session" open https://127.0.0.1:18444/login --browser=chrome --config "$runtime/playwright.json" >>"$artifact/engines.log" 2>&1
  playwright-cli -s="$engine_session" run-code --filename="$runtime/policy-prepare.js" >"$artifact/policy-$gym-$policy-prepare.log" 2>&1
  record_json "$artifact/policy-$gym-$policy-prepare.log" "$artifact/policy-$gym-$policy-prepare.json"
  node - "$artifact/policy-$gym-$policy-prepare.json" <<'NODE'
const fs=require('node:fs');if(JSON.parse(fs.readFileSync(process.argv[2])).prepared!==true)throw Error('Policy UI preparation failed');
NODE
  policy_family="$(node - "$artifact/policy-$gym-$policy-prepare.json" <<'NODE'
const fs=require('node:fs'),id=JSON.parse(fs.readFileSync(process.argv[2])).policyFamilyId;if(!/^[0-9a-f-]{36}$/.test(id))throw Error('Missing prepared policy family');process.stdout.write(id);
NODE
)"
  policy_mutation "$policy" "$gym" "$policy_family" >"$artifact/policy-$gym-$policy-sql.log" 2>&1
  playwright-cli -s="$engine_session" run-code --filename="$runtime/policy-verify.js" >"$artifact/policy-$gym-$policy.log" 2>&1
  record_json "$artifact/policy-$gym-$policy.log" "$artifact/policy-$gym-$policy.json"
  playwright-cli -s="$engine_session" close >>"$artifact/engines.log" 2>&1
  engine_started=false
 done
done
node - "$artifact" <<'NODE'
const fs=require('node:fs'),dir=process.argv[2],reports=[];
for(const gym of [1,2])for(const policy of ['expired','password','role','disabled'])reports.push(JSON.parse(fs.readFileSync(`${dir}/policy-${gym}-${policy}.json`)));
const first=reports[0];for(const report of reports)if(report.suite!=='fitcore-cookie-policy-ui-v1'||report.browserType!==first.browserType||report.browserVersion!==first.browserVersion||report.production!=='BLOCKED')throw Error('Mismatched policy stage report');
const checks=reports.flatMap(r=>r.checks),failures=reports.flatMap(r=>r.failures);fs.writeFileSync(`${dir}/policy-ui-results.json`,JSON.stringify({suite:first.suite,passed:checks.length,checks,failures,browserType:first.browserType,browserVersion:first.browserVersion,production:'BLOCKED'},null,2));
NODE
# Close and restart an actual browser process against its disposable profile.
docker exec "$container_id" psql -U fitcore_phase2_test -d fitcore_phase2 --set ON_ERROR_STOP=1 -c 'delete from security_rate_limits' >/dev/null
prepare_runner gym-frontend/scripts/cookie-restart.pw.js "$runtime/restart-before.js" before
prepare_runner gym-frontend/scripts/cookie-restart.pw.js "$runtime/restart-after.js" after
restart_started=true
playwright-cli -s="$restart_session" open https://127.0.0.1:18444/login --profile "$runtime/browser-profile" --config "$runtime/playwright.json" >"$artifact/restart-browser.log" 2>&1
playwright-cli -s="$restart_session" run-code --filename="$runtime/restart-before.js" >"$artifact/restart-before.log" 2>&1
record_json "$artifact/restart-before.log" "$artifact/restart-before.json"
playwright-cli -s="$restart_session" close >>"$artifact/restart-browser.log" 2>&1
restart_started=false
restart_started=true
playwright-cli -s="$restart_session" open https://127.0.0.1:18444/login --profile "$runtime/browser-profile" --config "$runtime/playwright.json" >>"$artifact/restart-browser.log" 2>&1
playwright-cli -s="$restart_session" run-code --filename="$runtime/restart-after.js" >"$artifact/restart-after.log" 2>&1
record_json "$artifact/restart-after.log" "$artifact/native-restart.json"
playwright-cli -s="$restart_session" close >>"$artifact/restart-browser.log" 2>&1
restart_started=false
# No raw handles/CSRF/proofs/private keys in application logs; only fixed marker violations counted.
if rg -n 'BEGIN PRIVATE KEY|Bearer eyJ|__Host-fitcore_session=|__Host-fitcore_preauth=' "$artifact/backend.log" "$artifact/backend-second.log" "$artifact/bff.log";then echo 'Secret log leak';exit 1;fi
