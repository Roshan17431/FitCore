#!/usr/bin/env bash
set -euo pipefail
# Reuse the existing guarded, loopback-only disposable PostgreSQL harness.
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
mkdir -p -- "$script_dir/../../output/playwright/phase3-2026-10-21"
export FITCORE_VERIFY_PHASE3_UI=true
exec "$script_dir/verify-phase2-ui.sh" "$@"
