# Iteration2-Week1 执行计划（构建服务实质升级）

> 目标：从“诊断/规划”转向“客户端与服务端构建能力实装”，避免低收益文档/脚本占用开发预算。

## P0-1 传输层实装起步（Server/Client）
1. 新建 `tooling/transport-spi` 接口骨架（会话、请求、事件流）。
2. 将现有 `ToolingApiServerImpl` 通过网关接口暴露，解除对具体 JSON-RPC 的直接耦合。
3. 在 `core/app` 侧接入 transport gateway 调用入口（先保持 legacy 默认实现）。

**验收**
- 不改变现有功能情况下，编译通过且 build/sync 主路径可调用 SPI。

## P0-2 非 Android 通用同步增强（实装）
1. root/module 级能力探测统一化：先拉 `GradleProject/IdeaProject` 基础模型。
2. Android 模型请求改为按模块按需获取；失败时局部降级而非全局失败。
3. `core/projects` 展示层保证 JVM 模块可用（任务、源码根、依赖基础信息）。

**验收**
- 无 AGP 项目初始化成功。
- 混合项目（Android+JVM）可同时展示并执行任务。

## P0-3 初始化协商结果的客户端策略生效
1. 根据 `InitializeResult` 的协商结果动态调整客户端请求行为：
   - 不支持 phasedAction 时自动降级。
   - 不支持 snapshot/query 时禁用相关调用入口。
2. 增加 UI/日志提示，避免“请求了但服务端不支持”的静默失败。

**验收**
- 协商不支持时无崩溃、无无效调用重试风暴。

## P1-1 gRPC(UDS, Termux) 最小 PoC（并行）
1. 在独立分支模块建立 UDS gRPC 通道最小样例（握手+ping）。
2. 验证 socket 生命周期：创建/断开/重连。

**验收**
- 端内可完成一次 UDS ping 往返并打印耗时。

## 交付标准
- 每个 P0 至少落地 1 组可运行代码与调用链，不再仅提供规划文档。


## Tooling API 9.5.1 源码使用约束

- 服务端与客户端升级开发必须优先查阅并对齐以下源码路径：
  - `gradle/libs/android/zero/studio/gradle/gradle-tooling-api/9.5.1/gradle-tooling-api-9.5.1-sources/org/gradle`
- 所有新能力/新接口接入前，先进行 API 对照：
  1. 查 `org/gradle/tooling/**` 与 `org/gradle/tooling/events/**` 对应入口；
  2. 明确可用版本与兼容边界；
  3. 再落到 `tooling/api` 与 `tooling/impl` 的实现。
- 禁止脱离 9.5.1 源码接口“猜测式实现”，避免后续协议/模型漂移。
- 本项目构建服务运行在 Termux 本地 JVM 环境，默认不依赖互联网/在线 HTTP 服务。
