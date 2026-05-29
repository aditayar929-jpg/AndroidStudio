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

summaries=$(grep "W1_PROGRESS_SUMMARY" "$log_file" || true)
if [[ -z "$summaries" ]]; then
  echo "No W1_PROGRESS_SUMMARY lines found"
  exit 1
fi

status=0
while IFS= read -r line; do
  started=$(echo "$line" | sed -n 's/.* started=\([0-9][0-9]*\).*/\1/p')
  finished=$(echo "$line" | sed -n 's/.* finished=\([0-9][0-9]*\).*/\1/p')
  dangling=$(echo "$line" | sed -n 's/.* dangling=\([0-9][0-9]*\).*/\1/p')

  if [[ -z "$started" || -z "$finished" || -z "$dangling" ]]; then
    echo "Unparseable summary line: $line"
    status=1
    continue
  fi

  if [[ "$started" -ne "$finished" || "$dangling" -ne 0 ]]; then
    echo "FAIL summary: started=$started finished=$finished dangling=$dangling"
    status=1
  else
    echo "PASS summary: started=$started finished=$finished dangling=$dangling"
  fi
done <<< "$summaries"

exit $status
