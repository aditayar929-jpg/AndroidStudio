package com.zerostudio.tooling.buildgrpc

import com.zerostudio.tooling.buildgrpc.proto.BuildEventEnvelope
import kotlinx.coroutines.flow.Flow

/**
 * Adapter turning existing in-process module into a pluggable backend.
 */
class InProcessBackendAdapter(
  private val module: InProcessBuildGrpcModule,
  private val actionExecutor: BuildActionExecutor = LocalNoopBuildActionExecutor(),
  override val backendId: String = "gradle-inprocess",
  override val buildSystem: BuildSystem = BuildSystem.GRADLE,
) : BuildBackend {

  override suspend fun initialize(request: BuildInit): BuildServerInfo = module.initialize(request)

  override fun startBuild(request: BuildStart): Flow<BuildEventEnvelope> = module.startBuild(request)

  override suspend fun executeAction(request: ActionExecutionRequest): ActionExecutionResult =
    actionExecutor.execute(request)

  override suspend fun shutdown(reason: String): Boolean = module.shutdown(reason)
}
