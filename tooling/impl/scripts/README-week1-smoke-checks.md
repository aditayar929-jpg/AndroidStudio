# Iteration1-Week1 Tooling Smoke Checks

本目录下脚本用于 Week1 收口时对 Tooling Server 日志进行自动化验收。

## 1) `check_initialize_negotiation_summary.sh`
校验初始化协商日志是否存在且协商结果非空。

- 输入：tooling server 日志文件
- 失败条件：
  - 没有初始化协商成功日志行
  - 任一日志行 `negotiatedOperationTypes` 为空集合

## 2) `check_progress_closure_summary.sh`
校验构建结束后的 progress 闭合摘要（`W1_PROGRESS_SUMMARY`）。

- 输入：tooling server 日志文件
- 失败条件：
  - 没有 `W1_PROGRESS_SUMMARY` 行
  - 任一摘要不满足：`started == finished && dangling == 0`

## 3) `run_week1_tooling_smoke_checks.sh`
统一入口：按顺序执行上面两个检查器。

```bash
tooling/impl/scripts/run_week1_tooling_smoke_checks.sh <tooling-server-log-file>
```

## 推荐在 CI 中的最小接入

```bash
# 假设 tooling server 日志输出到 tooling-server.log
tooling/impl/scripts/run_week1_tooling_smoke_checks.sh tooling-server.log
```

若脚本退出码非 0，应判定 Week1 验收未通过。


## 4) `check_non_android_init_summary.sh`
校验 non-Android 初始化回归场景是否出现硬失败标记，并确认至少一次初始化成功。

- 输入：tooling server 日志文件
- 失败条件：
  - 没有初始化成功标记
  - 存在 `Failed to initialize project`
  - 存在 `Unable to transform project`

```bash
tooling/impl/scripts/check_non_android_init_summary.sh <tooling-server-log-file>
```
