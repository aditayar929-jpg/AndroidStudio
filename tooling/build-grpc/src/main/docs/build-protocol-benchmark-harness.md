# Build Protocol Benchmark Harness (Phase E Baseline)

## Scope
- Throughput smoke benchmark for transfer store write/read range paths.
- Latency/throughput hooks intended for CI trend tracking.

## Current Harness
- `BuildDataStreamStoreBenchmarkTest`:
  - writes 8MiB chunk with checksum validation,
  - performs 2MiB range read from 4MiB offset,
  - computes MB/s metrics and asserts non-zero baseline.

## KPI Mapping
- Throughput baseline supports the architecture KPI tracking for sustained stream performance.
- Range-read behavior validates resumable read path memory safety after `readAllBytes` removal.

## Next Steps
- Add JFR/JMH profile jobs for p95/p99 latency.
- Capture peak RSS under multi-transfer concurrency.
- Publish periodic benchmark report artifacts in CI.

