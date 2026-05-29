package com.zerostudio.tooling.buildgrpc

import com.zerostudio.tooling.buildgrpc.proto.BuildEventEnvelope
import com.zerostudio.tooling.buildgrpc.proto.BuildTargetStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Phase 2 实现：基础 in-process 构建服务。
 */
class InProcessBuildGrpcModule(
  private val serverMetadata: BuildServerMetadata = BuildServerMetadata(
    serverName = "ZeroStudio BuildGrpc",
    serverVersion = "0.1.0",
    supportedLanguages = setOf("kotlin", "java", "groovy"),
    protocolFeatures = setOf("bsp.initialize", "bep.stream", "reapi.executeAction"),
  ),
) : BuildGrpcModule {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val stateByBuildId = ConcurrentHashMap<String, BuildLifecycleState>()

  override suspend fun initialize(request: BuildInit): BuildServerInfo {
    stateByBuildId.clear()
    return BuildServerInfo(
      serverName = serverMetadata.serverName,
      serverVersion = serverMetadata.serverVersion,
      supportedLanguages = serverMetadata.supportedLanguages.toList(),
      protocolFeatures = serverMetadata.protocolFeatures.toList(),
    )
  }

  override fun startBuild(request: BuildStart): Flow<BuildEventEnvelope> {
    val publisher = BuildEventPublisher(request.buildId)
    stateByBuildId[request.buildId] = BuildLifecycleState.RUNNING

    scope.launch {
      val start = System.currentTimeMillis()
      publisher.progress("build started", 0f)

      request.targets.forEachIndexed { index, target ->
        publisher.target(target, BuildTargetStatus.BUILD_TARGET_STATUS_RUNNING)
        delay(20)
        publisher.target(target, BuildTargetStatus.BUILD_TARGET_STATUS_SUCCESS)
        val p = ((index + 1).toFloat() / request.targets.size.coerceAtLeast(1)) * 100f
        publisher.progress("target complete: $target", p)
      }

      val duration = System.currentTimeMillis() - start
      publisher.finished(success = true, durationMillis = duration, summary = "build completed")
      stateByBuildId[request.buildId] = BuildLifecycleState.FINISHED
    }

    return publisher.events
  }

  override suspend fun shutdown(reason: String): Boolean {
    stateByBuildId.clear()
    return reason.isNotBlank()
  }
}
