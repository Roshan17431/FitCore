#!/usr/bin/env bash
set -euo pipefail
[[ $# == 0 ]] || { echo 'The local release check accepts no deployment or database arguments.' >&2; exit 64; }
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd -- "$script_dir/.." && pwd)"
cd -- "$repo_dir"
python3 scripts/test_release_results.py
./gym-backend/scripts/verify-tenancy.sh
python3 scripts/check_release_results.py gym-backend/target/surefire-reports
node --test gym-frontend/scripts/*.test.mjs
npm --prefix gym-frontend run lint
VITE_API_BASE_URL=http://127.0.0.1:18082 npm --prefix gym-frontend run build
FITCORE_VERIFY_SECURITY_UI=true FITCORE_VERIFY_PHASE3_UI=true FITCORE_VERIFY_OWNER_UI=true ./gym-backend/scripts/verify-phase2-ui.sh
FITCORE_VERIFY_SECURITY_UI=true FITCORE_VERIFY_PHASE3_UI=false FITCORE_VERIFY_OWNER_UI=false ./gym-backend/scripts/verify-phase2-ui.sh
printf '%s\n' 'Local candidate verification passed. Production release remains separately blocked by the release checklist.'
