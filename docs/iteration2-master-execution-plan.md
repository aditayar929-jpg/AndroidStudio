# Iteration2 总执行计划（2026-05-22）

> 决策：跳过 Iteration1-Week1 剩余验收项，直接进入 Iteration2 实装阶段。  
> 核心目标：围绕“构建服务客户端+服务端实质升级”推进，不再以补充脚本/验收文档为主。

---

## 0. 范围与交付边界

### 0.1 本迭代包含
1. `tooling/*` 传输抽象与服务端执行链路重构（SPI 化）。
2. `core/app` 构建客户端对协商结果/传输层的消费改造。
3. `core/projects` 非 Android 与混合项目同步能力增强。
4. gRPC(UDS, Termux) 最小可用 PoC（独立可切换）。

### 0.2 本迭代不包含
1. 一次性删除全部 legacy-lsp4j。
2. 完整 REAPI 生产落地（仅做接口预留与 PoC 验证）。

---

## 1. 迭代目标（Outcome）

1. **目标A：传输层可替换**  
   上层调用不再直接绑定 JSON-RPC 启动/通信细节。
2. **目标B：非 Android 初始化稳定可用**  
   无 AGP 项目、混合项目可同步且可执行任务。
3. **目标C：协商驱动行为生效**  
   客户端按 server 协商结果动态降级，不做无效调用。
4. **目标D：UDS 通道可运行**  
   在 Termux 服务端场景跑通 gRPC UDS 最小链路。

---

## 2. 分周计划

## Week1：架构落地周（P0）

### W2-P0-1 `tooling/transport-spi` 落地
- 新增接口：
  - `TransportSession`
  - `TransportServerEndpoint`
  - `TransportClientEndpoint`
  - `TransportEventStream`
- 将 `ToolingApiServerImpl` 对外调用入口通过 gateway 适配。

**完成标准**
- 现有 initialize/execute 流程不变功能前提下，可通过 SPI 调度。

### W2-P0-2 协商结果驱动客户端行为
- `core/app` 在 initialize 后缓存协商结果。
- 禁止调用不支持能力：
  - phasedAction
  - modelSnapshot
  - queryService
- UI/日志可见“已降级执行”。

**完成标准**
- 协商不支持场景下无异常、无重试风暴。

### W2-P0-3 非 Android 同步增强
- `tooling/impl` 先拉通用模型，再按模块请求 Android 模型。
- Android 模型失败局部降级，不中断全局。

**完成标准**
- 纯 JVM 项目 initialize+sync 成功。
- 混合项目 Android/JVM 模块都可见。

---

## Week2：能力整合周（P0/P1）

### W2-P0-4 gRPC UDS 最小可用通路（Termux）
- 新增 `transport-grpc-uds` 原型模块（或包）。
- 完成握手+ping+basic request。
- 完成 socket 生命周期（创建/断开/重连）。

### W2-P1-1 legacy 通路网关化
- legacy-lsp4j 下沉为 adapter，实现与 SPI 接口对齐。

### W2-P1-2 执行链路指标埋点
- initialize/execute 增加耗时、失败分类、协商结果指标字段。

---

## Week3：稳定性周（P1/P2）

### W2-P1-3 非 Android 回归样本集
- 增加最小样本：
  1. 纯 Java Gradle
  2. Kotlin JVM
  3. 混合 Android+JVM
- 每个样本可跑 initialize 与 execute smoke。

### W2-P2-1 REAPI 接口预留
- 定义最小客户端接口壳（不强制业务接入）。

---

## 3. 模块级任务分配

### 3.1 tooling/api
- 整理 transport-neutral 消息 DTO。
- 兼容字段默认值策略固化。

### 3.2 tooling/impl
- server gateway/SPI 接入。
- 非 Android 模型分流与局部降级。
- UDS gRPC PoC 服务端接口。

### 3.3 core/app
- 协商结果缓存与行为降级。
- 传输层入口切换开关（legacy/spi）。

### 3.4 core/projects
- workspace 转换稳定化（JVM/Android 混合视图一致性）。

---

## 4. 里程碑（Milestones）

- **M2-W1**：SPI 架构可运行 + 非 Android 同步可用。  
- **M2-W2**：UDS PoC 可运行 + legacy adapter 网关化。  
- **M2-W3**：样本回归集可跑 + 指标/日志闭环。

---

## 5. 风险与缓解

1. **风险：重构破坏现有链路**  
   - 缓解：保留 legacy adapter + 开关灰度。
2. **风险：UDS 在设备环境不稳定**  
   - 缓解：生命周期状态机 + 自动重连策略。
3. **风险：模型分流导致数据不一致**  
   - 缓解：模块级 contract 校验与回归样本。

---

## 6. 本迭代 Done 定义

满足以下条件即判定 Iteration2 完成：
1. SPI 路径可支撑 initialize/execute 主流程。
2. 纯 JVM 与混合项目同步成功率达到可用阈值。
3. UDS PoC 在 Termux 场景稳定跑通最小调用。
4. 协商降级逻辑在客户端稳定生效。


## Tooling API 9.5.1 源码使用约束

- 服务端与客户端升级开发必须优先查阅并对齐以下源码路径：
  - `gradle/libs/android/zero/studio/gradle/gradle-tooling-api/9.5.1/gradle-tooling-api-9.5.1-sources/org/gradle`
- 所有新能力/新接口接入前，先进行 API 对照：
  1. 查 `org/gradle/tooling/**` 与 `org/gradle/tooling/events/**` 对应入口；
  2. 明确可用版本与兼容边界；
  3. 再落到 `tooling/api` 与 `tooling/impl` 的实现。
- 禁止脱离 9.5.1 源码接口“猜测式实现”，避免后续协议/模型漂移。
- 本项目构建服务运行在 Termux 本地 JVM 环境，默认不依赖互联网/在线 HTTP 服务。
