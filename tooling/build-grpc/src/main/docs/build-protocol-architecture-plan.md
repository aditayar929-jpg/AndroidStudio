# Binary Build Service Protocol Architecture Plan

## 1. Product Goal (M+N)
- Provide one standard client protocol for IDEs/agents and one server protocol for build systems.
- Reduce `M × N` integration complexity to `M + N` through a stable transport-neutral contract.
- Support Bazel/Gradle/Maven/custom engines through capability negotiation.

## 2. Protocol Stack
- **AIDL channel**: Android in-process or Binder IPC entrypoint.
- **gRPC channel**: LAN/WAN or local socket streaming RPC entrypoint.
- **Proto schema**: canonical wire contract and backward-compatible evolution.
- **REAPI bridge**: digest, platform, action metadata compatibility for remote execution/caching.

## 3. Layered Architecture
1. **Session layer**
   - Initialize, capability negotiation, shutdown.
2. **Build orchestration layer**
   - Start build, target status, diagnostics, lifecycle events.
3. **Execution layer**
   - REAPI action-oriented execution and per-action telemetry.
4. **High-throughput data layer**
   - Chunked upload/download, transfer acks, compression negotiation.
5. **Context layer**
   - Streaming context frames for env/toolchain/property synchronization.

## 4. Performance Strategy
- Prefer protobuf binary for default serialization.
- Add negotiated serialization kind for future FlatBuffers/Cap'n Proto adapters.
- Use chunked streaming with compression kinds (`zstd`, `lz4`, `gzip`) for large artifacts/logs.
- Carry explicit resource budget/usage to prevent OOM and support adaptive throttling.

## 5. Compatibility Rules
- Keep existing fields stable; only append new fields.
- Add new RPCs for data/context streams rather than overloading old RPC semantics.
- Ensure unknown enum/message values are safely ignored by older clients.

## 6. Implementation Phases
1. **Phase A (done in schema)**: capabilities, transport/serialization negotiation, data/context stream RPCs.
2. **Phase B**: hook stream RPCs to CAS/log storage backend and add checksum validation.
3. **Phase C**: implement REAPI execute wiring (`Execution`, `ActionCache`) and operation polling.
4. **Phase D**: AIDL-gRPC bridge parity tests and contract conformance test suite.
5. **Phase E**: benchmark large project scenarios (memory, throughput, p99 latency).

## 7. KPI Baseline
- p95 event delivery latency < 50ms in local mode.
- Sustained artifact stream throughput > 200MB/s on loopback.
- Peak process RSS bounded under configurable budget policy.
- Zero protocol-breaking changes for minor version updates.


## 8. Text-First Data Plane Note
- Source files, compiler diagnostics, patch hunks, and script snippets are predominantly text payloads.
- The binary channel must optimize text transfer via:
  - chunked UTF-8 byte streams with checksum validation,
  - resumable offset-based reads,
  - optional compression negotiated per transfer,
  - bounded frame sizing to avoid memory spikes.
- For very large mono-repos, stream text deltas incrementally instead of materializing full snapshots in memory.
