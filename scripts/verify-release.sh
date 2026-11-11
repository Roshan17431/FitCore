#!/usr/bin/env bash
set -euo pipefail
[[ $# == 0 ]] || { echo 'The local release check accepts no deployment or database arguments.' >&2; exit 64; }
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd -- "$script_dir/.." && pwd)"
cd -- "$repo_dir"
python3 scripts/test_release_results.py
python3 scripts/test_browser_spike_results.py
python3 scripts/test_cookie_completion_results.py
./gym-backend/scripts/verify-tenancy.sh
python3 scripts/check_release_results.py gym-backend/target/surefire-reports
mkdir -p output/release
node --test --test-reporter=spec --test-reporter-destination=stdout --test-reporter=junit --test-reporter-destination=output/release/frontend.xml gym-frontend/scripts/*.test.mjs
npm --prefix gym-frontend run lint
VITE_API_BASE_URL=http://127.0.0.1:18082 npm --prefix gym-frontend run build
FITCORE_VERIFY_BACKEND_PORT=18088 FITCORE_VERIFY_FRONTEND_PORT=5178 FITCORE_VERIFY_SECURITY_UI=true FITCORE_VERIFY_PHASE3_UI=true FITCORE_VERIFY_OWNER_UI=true ./gym-backend/scripts/verify-phase2-ui.sh
python3 scripts/record_legacy_browser_results.py output/playwright/phase2-2026-10-20 enabled output/release/legacy-enabled.json
FITCORE_VERIFY_BACKEND_PORT=18088 FITCORE_VERIFY_FRONTEND_PORT=5178 FITCORE_VERIFY_SECURITY_UI=true FITCORE_VERIFY_PHASE3_UI=false FITCORE_VERIFY_OWNER_UI=false ./gym-backend/scripts/verify-phase2-ui.sh
python3 scripts/record_legacy_browser_results.py output/playwright/phase2-2026-10-20 disabled output/release/legacy-disabled.json
./gym-backend/scripts/verify-browser-spike.sh
python3 scripts/check_browser_spike_results.py output/release/frontend.xml output/playwright/browser-spike/results.json output/playwright/browser-spike/shared-instances.json
python3 scripts/check_cookie_completion_results.py output/playwright/browser-spike/recovery-results.json output/playwright/browser-spike/gym-ui-results.json output/playwright/browser-spike/native-restart.json output/playwright/browser-spike/policy-ui-results.json output/playwright/browser-spike/onboarding-results.json
printf '%s\n' 'Local candidate verification passed. Production release remains separately blocked by the release checklist.'
