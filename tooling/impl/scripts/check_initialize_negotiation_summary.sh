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

# Parse lines emitted by ToolingApiServerImpl:
# "Project initialization succeeded: requestId=<id> negotiatedOperationTypes=<set>"
lines=$(grep "Project initialization succeeded: requestId=" "$log_file" || true)
if [[ -z "$lines" ]]; then
  echo "No initialize success negotiation lines found"
  exit 1
fi

status=0
while IFS= read -r line; do
  request_id=$(echo "$line" | sed -n 's/.*requestId=\([^ ]*\).*/\1/p')
  negotiated=$(echo "$line" | sed -n 's/.*negotiatedOperationTypes=\(.*\)$/\1/p')

  if [[ -z "$request_id" || -z "$negotiated" ]]; then
    echo "Unparseable initialize negotiation line: $line"
    status=1
    continue
  fi

  if [[ "$negotiated" == "[]" || "$negotiated" == "{}" ]]; then
    echo "FAIL initialize negotiation: requestId=$request_id negotiated=$negotiated"
    status=1
  else
    echo "PASS initialize negotiation: requestId=$request_id negotiated=$negotiated"
  fi
done <<< "$lines"

exit $status
