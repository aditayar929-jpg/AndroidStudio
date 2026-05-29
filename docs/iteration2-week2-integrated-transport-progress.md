# Iteration2 Week2 进展：Integrated Transport 执行链路与客户端路由升级（2026-05-22）

## 本次升级目标

在已完成 proto 模块与 gateway 骨架后，本次继续推进“下一阶段”：

1. **核心客户端执行路由收敛**：清理 `GradleBuildService.executeTasks()` 的重复分支，统一执行策略。
2. **与 integrated 传输策略对齐**：将路由决策显式绑定到 transport mode 与 execute 开关日志，便于排错与观测。
3. **构建进程环境组装模块化**：把 PATH/LD_LIBRARY_PATH/TMPDIR 注入逻辑抽到独立函数，避免未来 AIDL/gRPC/REAPI 双通路漂移。

## 关键改动

- `executeTasks` 由多重重复 `if (useToolingExecute())` 分支改成单路径：
  - `shouldRouteThroughToolingExecute(tasks)`
  - tooling execute 路径
  - gradlew shell 路径
- 新增 `augmentProcessEnvironment(finalEnv)`，集中处理进程环境。
- 补充 integrated 路由日志：`enabled/integratedTransport/tasks`。

## 预期收益

1. 降低构建主链路认知复杂度，减少分支行为不一致风险。
2. 为后续把 execute 正式切到 AIDL+gRPC+REAPI 通路提供清晰决策入口。
3. 客户端侧更容易做 capability 驱动降级，不再散落在多处分支内。

## 下一步

1. 在 `shouldRouteThroughToolingExecute` 中接入 capability 协商结果（例如 REAPI 可用性）作为硬条件。
2. 引入真正的 gRPC UDS server binding（替换 placeholder socket 文件方案）。
3. 在 `core/projects` 增加混合项目回归样例并自动化 smoke。
