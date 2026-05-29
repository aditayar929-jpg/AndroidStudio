# Iteration2 阶段进展：REAPI Proto Gradle 接入与混合协议契约（2026-05-22）

## 本次推进内容（Week2 / P0-P1 衔接）

1. 新增 `:tooling:reapi-proto` 模块。
2. 模块直接读取 `tooling/reapi/build/bazel/**/*.proto`，统一生成 Java/Kotlin/gRPC stub。
3. 在 `tooling/api` 增加 `IntegratedProtocolContracts`，明确 AIDL + gRPC(UDS) + REAPI 的本地化契约。
4. 在 `tooling/impl` 对 `:tooling:reapi-proto` 建立依赖，为后续服务端/客户端网关落地提供编译期契约。

## 设计要点

- **本地通信约束**：`LocalEndpoint` 仅保留 UDS 路径与 AIDL 服务名，不暴露 host/port。
- **协议分层**：
  - AIDL：控制面（会话、生命周期）；
  - gRPC/Proto：数据面（流式事件/请求）；
  - REAPI：执行语义（CAS/Execute 等）。
- **渐进迁移**：保留 legacy adapter，综合通路通过 capability 协商逐步切换。

## 下一步建议（紧接当前提交）

1. `tooling/impl` 新增 gRPC UDS server bootstrap（最小 handshake + ping）。
2. `core/app` 增加 `IntegratedProtocolContracts.Handshake` 消费与降级日志。
3. 将 `ReapiExecutionGateway` 的占位方法替换为 proto stub 调用（先 QueryActionResult/Execute 最小子集）。
