#!/usr/bin/env bash
set -euo pipefail

# Own only this disposable container; never use a configured application DB.
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
backend_dir="$(cd -- "$script_dir/.." && pwd)"
postgres_image="${FITCORE_POSTGRES_TEST_IMAGE:-postgres:18-alpine}"
container_id=""

cleanup() {
  if [[ -n "$container_id" ]]; then
    docker rm -f "$container_id" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

command -v docker >/dev/null 2>&1 || { echo 'Docker is required for disposable PostgreSQL verification.' >&2; exit 1; }
docker info >/dev/null

container_id="$(docker run --detach --rm \
  --label fitcore.purpose=tenant-foundation-disposable-verification \
  --publish 127.0.0.1::5432 \
  --env POSTGRES_DB=fitcore_phase2 \
  --env POSTGRES_USER=fitcore_phase2_test \
  --env POSTGRES_PASSWORD=fitcore-disposable-phase2-only \
  "$postgres_image")"

ready=false
for ((attempt = 0; attempt < 60; attempt++)); do
  if docker exec "$container_id" pg_isready --host 127.0.0.1 --port 5432 \
    --username fitcore_phase2_test --dbname fitcore_phase2 >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 1
done
if [[ "$ready" != true ]]; then
  echo 'Disposable PostgreSQL did not become ready within 60 seconds.' >&2
  docker logs "$container_id" >&2
  exit 1
fi

binding="$(docker port "$container_id" 5432/tcp)"
if [[ ! "$binding" =~ ^127\.0\.0\.1:([0-9]+)$ ]]; then
  echo 'Expected exactly one loopback-only PostgreSQL port binding.' >&2
  exit 1
fi
postgres_port="${BASH_REMATCH[1]}"
export FITCORE_POSTGRES_TEST_URL="jdbc:postgresql://127.0.0.1:${postgres_port}/fitcore_phase2"
export FITCORE_POSTGRES_TEST_USERNAME=fitcore_phase2_test
export FITCORE_POSTGRES_TEST_PASSWORD=fitcore-disposable-phase2-only

export FITCORE_TENANCY_TEST_TOKEN="$container_id"
docker exec -i "$container_id" psql --username fitcore_phase2_test --dbname fitcore_phase2 \
  --set ON_ERROR_STOP=1 --set "marker=fitcore-disposable:$container_id" <<'SQL'
COMMENT ON DATABASE fitcore_phase2 IS :'marker';
SQL

echo "Verifying H2 regression tests and PostgreSQL concurrency tests using disposable loopback port ${postgres_port}."
cd -- "$backend_dir"
# Keep only tool/cache locations and these harness-owned connection values. In
# particular, never inherit Spring/Hikari/Flyway overrides or JVM/Maven options.
env -i PATH="$PATH" HOME="$HOME" JAVA_HOME="${JAVA_HOME:-}" \
  FITCORE_POSTGRES_TEST_URL="$FITCORE_POSTGRES_TEST_URL" \
  FITCORE_POSTGRES_TEST_USERNAME="$FITCORE_POSTGRES_TEST_USERNAME" \
  FITCORE_POSTGRES_TEST_PASSWORD="$FITCORE_POSTGRES_TEST_PASSWORD" \
  FITCORE_TENANCY_TEST_TOKEN="$FITCORE_TENANCY_TEST_TOKEN" \
  ./mvnw clean verify "$@"
