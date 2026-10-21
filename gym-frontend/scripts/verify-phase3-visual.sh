#!/usr/bin/env bash
set -euo pipefail
# Synthetic fixture UI only: no backend, credentials, database, or environment-file loading.
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
frontend_dir="$(cd -- "$script_dir/.." && pwd)"
repo_dir="$(cd -- "$frontend_dir/.." && pwd)"
artifact_dir="$repo_dir/output/playwright/phase3-2026-10-21"
mode="${1:-full}"
case "$mode" in full|--quick|--baseline) ;; *) echo 'Usage: verify-phase3-visual.sh [--quick|--baseline]' >&2; exit 64;; esac
for binary in node lsof curl playwright-cli rg; do command -v "$binary" >/dev/null || { echo "Required: $binary" >&2; exit 1; }; done
if lsof -nP -iTCP:5177 -sTCP:LISTEN >/dev/null 2>&1; then echo 'Port 5177 is occupied; no process was changed.' >&2; exit 64; fi
if [[ "$mode" == --baseline ]] && rg --files "$artifact_dir" 2>/dev/null | rg -q '/before-.*\.png$'; then
  echo 'Existing before screenshots are preserved; refusing to overwrite them.' >&2
  exit 64
fi
node_bin="$(command -v node)"
runtime_dir="$(mktemp -d "${TMPDIR:-/tmp}/fitcore-phase3-visual.XXXXXX")"
vite_pid=""
session="fitcore-phase3-visual-$$"
browser_started=false
mkdir -p -- "$artifact_dir"
cleanup() {
  local status=$?
  trap - EXIT INT TERM
  if [[ "$browser_started" == true ]]; then playwright-cli -s="$session" close >>"$artifact_dir/visual-browser.log" 2>&1 || status=1; fi
  if [[ -n "$vite_pid" ]]; then kill -TERM "$vite_pid" 2>/dev/null || true; wait "$vite_pid" 2>/dev/null || true; fi
  rm -rf -- "$runtime_dir"
  if lsof -nP -iTCP:5177 -sTCP:LISTEN >/dev/null 2>&1; then echo 'Owned Vite cleanup failed; port 5177 still occupied.' >&2; status=1; else echo 'Owned browser closed and loopback port 5177 released.'; fi
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
"$node_bin" - "$frontend_dir/vite.config.js" "$runtime_dir/vite.config.mjs" "$script_dir/phase3-ui.pw.js" "$runtime_dir/phase3-ui.pw.js" "$artifact_dir" <<'NODE'
const fs = require('node:fs');
const { pathToFileURL } = require('node:url');
const [original,config,source,runner,out] = process.argv.slice(2);
fs.writeFileSync(config, `import base from ${JSON.stringify(pathToFileURL(original).href)};\nexport default { ...base, envDir: false, server: { ...base.server, host: '127.0.0.1', port: 5177, strictPort: true } };\n`);
const code = fs.readFileSync(source,'utf8').replace("const origin = 'http://127.0.0.1:5174';", "const origin = 'http://127.0.0.1:5177';").replace(/const out = '[^']+';/, `const out = ${JSON.stringify(out)};`);
if (code === fs.readFileSync(source,'utf8')) throw Error('Fixture runner path substitution failed');
fs.writeFileSync(runner,code);
NODE
(
  cd -- "$frontend_dir"
  exec env -i PATH="$PATH" VITE_API_BASE_URL=http://127.0.0.1:18081/api "$node_bin" "$frontend_dir/node_modules/vite/bin/vite.js" --config "$runtime_dir/vite.config.mjs"
) >"$artifact_dir/visual-vite.log" 2>&1 &
vite_pid=$!
ready=false
for ((attempt=0; attempt<40; attempt++)); do
  kill -0 "$vite_pid" 2>/dev/null || { echo 'Owned Vite stopped; inspect visual-vite.log.' >&2; exit 1; }
  if curl --noproxy '*' --silent --fail http://127.0.0.1:5177/login >/dev/null; then ready=true; break; fi
  sleep 0.5
done
[[ "$ready" == true ]] || { echo 'Owned loopback Vite failed to start.' >&2; exit 1; }
url=http://127.0.0.1:5177/login
[[ "$mode" != --quick ]] || url+='?quick=1'
[[ "$mode" != --baseline ]] || url+='?baseline=1'
browser_started=true
playwright-cli -s="$session" open "$url" >"$artifact_dir/visual-browser.log" 2>&1
playwright-cli -s="$session" run-code --filename="$runtime_dir/phase3-ui.pw.js" >"$artifact_dir/visual-${mode#--}.log" 2>&1
if rg -q '^### Error' "$artifact_dir/visual-${mode#--}.log"; then echo 'Browser checks failed; inspect visual log.' >&2; exit 1; fi
echo "Visual $mode checks completed. Artifacts: $artifact_dir"
