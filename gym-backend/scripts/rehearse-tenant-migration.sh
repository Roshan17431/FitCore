#!/usr/bin/env bash
set -euo pipefail
# No connection arguments or manifests. The owned runner creates/marks its source;
# recovery tests create separately owned targets. All fixture choices are synthetic.
[[ $# == 0 ]] || { echo 'This rehearsal accepts no database or ownership inputs.' >&2; exit 64; }
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec "$script_dir/verify-tenancy.sh" -Dtest=FoundationMigrationTest,TenantRecoveryTest
