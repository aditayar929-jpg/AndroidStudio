#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <tooling-server-log-file>"
  exit 2
fi

log_file="$1"
if [[ ! -f "$log_file" ]]; then
  echo "Log file not found: $log_file"
  exit 2
fi

# Expected success marker from server init path.
success_lines=$(grep "Project initialization succeeded: requestId=" "$log_file" || true)
if [[ -z "$success_lines" ]]; then
  echo "No successful initialization marker found"
  exit 1
fi

# Non-Android fallback warning marker emitted by WorkspaceModelBuilder when root type is Unknown.
# This is optional; script reports when seen to confirm fallback path was exercised.
fallback_lines=$(grep "Root project type is UNKNOWN, falling back to Gradle root model transformation" "$log_file" || true)

# Detect common hard-failure markers for init path.
if grep -q "Failed to initialize project" "$log_file"; then
  echo "FAIL: initialization failure marker found"
  exit 1
fi

if grep -q "Unable to transform project" "$log_file"; then
  echo "FAIL: workspace transform failure marker found"
  exit 1
fi

count=$(echo "$success_lines" | wc -l | tr -d ' ')
echo "PASS: successful initialize markers found: $count"

if [[ -n "$fallback_lines" ]]; then
  fb_count=$(echo "$fallback_lines" | wc -l | tr -d ' ')
  echo "INFO: non-android unknown-root fallback path observed: $fb_count"
else
  echo "INFO: non-android unknown-root fallback marker not observed in this log sample"
fi
