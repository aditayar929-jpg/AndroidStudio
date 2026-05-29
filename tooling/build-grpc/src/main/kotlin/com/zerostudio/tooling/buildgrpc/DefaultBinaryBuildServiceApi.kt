package com.zerostudio.tooling.buildgrpc

import com.zerostudio.tooling.buildgrpc.proto.BuildEventEnvelope
import com.zerostudio.tooling.buildgrpc.proto.BuildMode
import kotlinx.coroutines.flow.Flow

/**
 * Default BSP-like binary protocol implementation over routing module.
 */
class DefaultBinaryBuildServiceApi(
  private val module: RoutingBuildGrpcModule,
) : BinaryBuildServiceApi {

  override suspend fun buildInitialize(request: BuildInitializeRequest): BuildInitializeResponse {
    val init = BuildInit(
      workspaceRoot = request.workspaceRoot,
      clientName = request.clientName,
      clientVersion = request.clientVersion,
      capabilities = (request.capabilities + setOf("buildSystem:${request.buildSystemId}")).toList(),
    )
    val info = module.initialize(init)
    return BuildInitializeResponse(
      serverName = info.serverName,
      serverVersion = info.serverVersion,
      protocolVersion = "1.0.0",
      negotiatedCapabilities = info.protocolFeatures.toSet(),
    )
  }

  override suspend fun workspaceBuildTargets(request: WorkspaceBuildTargetsRequest): WorkspaceBuildTargetsResponse {
    return WorkspaceBuildTargetsResponse(emptyList())
  }

  override fun buildTargetCompile(request: BuildTargetCompileRequest): Flow<BuildEventEnvelope> =
    build(request.buildId, request.targetIds, request.arguments, BuildMode.BUILD_MODE_INCREMENTAL)

  override fun buildTargetTest(request: BuildTargetTestRequest): Flow<BuildEventEnvelope> =
    build(request.buildId, request.targetIds, request.arguments, BuildMode.BUILD_MODE_TEST)

  override fun buildTargetRun(request: BuildTargetRunRequest): Flow<BuildEventEnvelope> =
    module.startBuild(
      BuildStart(
        buildId = request.buildId,
        targets = listOf(request.targetId),
        options = mapOf(
          "build.mode" to BuildMode.BUILD_MODE_INCREMENTAL.name,
          "build.intent" to "run",
          "build.args" to request.arguments.joinToString(" "),
        ),
      ),
    )

  override suspend fun buildTargetDependencyModules(
    request: BuildTargetDependencyModulesRequest,
  ): BuildTargetDependencyModulesResponse {
    return BuildTargetDependencyModulesResponse(
      dependencyModulesByTargetId = request.targetIds.associateWith { emptyList<String>() },
    )
  }

  override suspend fun executeAction(request: ActionExecutionRequest): ActionExecutionResult =
    module.executeAction(request)

  override suspend fun cancelBuild(request: BuildCancelRequest): BuildCancelResponse =
    BuildCancelResponse(accepted = request.buildId.isNotBlank())

  override suspend fun shutdown(request: BuildShutdownRequest): BuildShutdownResponse =
    BuildShutdownResponse(accepted = module.shutdown(request.reason))

  private fun build(
    buildId: String,
    targetIds: List<String>,
    arguments: List<String>,
    mode: BuildMode,
  ): Flow<BuildEventEnvelope> {
    return module.startBuild(
      BuildStart(
        buildId = buildId,
        targets = targetIds,
        options = mapOf(
          "build.mode" to mode.name,
          "build.args" to arguments.joinToString(" "),
        ),
      ),
    )
  }
}
