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
rm -f "$artifact/results.json" "$artifact/shared-instances.json"
runtime="$(mktemp -d "${TMPDIR:-/tmp}/fitcore-browser-spike.XXXXXX")"
chmod 700 "$runtime"
container_id=""; backend_pid=""; second_backend_pid=""; bff_pid=""; browser_started=false
session="fitcore-browser-spike-$$"
cleanup(){
  local result=$?
  trap - EXIT INT TERM
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
# No raw handles/CSRF/proofs/private keys in application logs; only fixed marker violations counted.
if rg -n 'BEGIN PRIVATE KEY|Bearer eyJ|__Host-fitcore_session=|__Host-fitcore_preauth=' "$artifact/backend.log" "$artifact/backend-second.log" "$artifact/bff.log";then echo 'Secret log leak';exit 1;fi
