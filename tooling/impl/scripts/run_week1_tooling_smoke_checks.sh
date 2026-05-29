#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <tooling-server-log-file>"
  exit 2
fi

log_file="$1"
script_dir="$(cd "$(dirname "$0")" && pwd)"

progress_checker="$script_dir/check_progress_closure_summary.sh"
negotiation_checker="$script_dir/check_initialize_negotiation_summary.sh"

if [[ ! -x "$progress_checker" ]]; then
  echo "Missing executable: $progress_checker"
  exit 2
fi

if [[ ! -x "$negotiation_checker" ]]; then
  echo "Missing executable: $negotiation_checker"
  exit 2
fi

echo "[W1] Running initialize negotiation checker..."
"$negotiation_checker" "$log_file"

echo "[W1] Running progress closure checker..."
"$progress_checker" "$log_file"

echo "[W1] PASS: tooling smoke checks passed for log file: $log_file"
