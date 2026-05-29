package com.zerostudio.tooling.buildgrpc

import com.zerostudio.tooling.buildgrpc.proto.BuildEventEnvelope
import kotlinx.coroutines.flow.Flow

/**
 * BSP-like generic binary build service protocol.
 *
 * Purpose:
 * - Solve M+N IDE/build-system integration complexity with one common contract.
 * - Replace legacy JSON-RPC+Gson transport with typed binary protocol semantics.
 */
interface BinaryBuildServiceApi {
  suspend fun buildInitialize(request: BuildInitializeRequest): BuildInitializeResponse

  suspend fun workspaceBuildTargets(request: WorkspaceBuildTargetsRequest): WorkspaceBuildTargetsResponse

  fun buildTargetCompile(request: BuildTargetCompileRequest): Flow<BuildEventEnvelope>

  fun buildTargetTest(request: BuildTargetTestRequest): Flow<BuildEventEnvelope>

  fun buildTargetRun(request: BuildTargetRunRequest): Flow<BuildEventEnvelope>

  suspend fun buildTargetDependencyModules(
    request: BuildTargetDependencyModulesRequest,
  ): BuildTargetDependencyModulesResponse

  suspend fun executeAction(request: ActionExecutionRequest): ActionExecutionResult

  suspend fun cancelBuild(request: BuildCancelRequest): BuildCancelResponse

  suspend fun shutdown(request: BuildShutdownRequest): BuildShutdownResponse
}

data class BuildInitializeRequest(
  val workspaceRoot: String,
  val buildSystemId: String,
  val clientName: String,
  val clientVersion: String,
  val capabilities: Set<String> = emptySet(),
)

data class BuildInitializeResponse(
  val serverName: String,
  val serverVersion: String,
  val protocolVersion: String,
  val negotiatedCapabilities: Set<String>,
)

data class WorkspaceBuildTargetsRequest(val workspaceRoot: String)

data class WorkspaceBuildTargetsResponse(val targets: List<BuildTargetInfo>)

data class BuildTargetInfo(
  val id: String,
  val displayName: String,
  val baseDirectory: String,
  val languageIds: List<String>,
  val dependencies: List<String> = emptyList(),
  val tags: Set<String> = emptySet(),
)

data class BuildTargetCompileRequest(
  val buildId: String,
  val targetIds: List<String>,
  val arguments: List<String> = emptyList(),
)

data class BuildTargetTestRequest(
  val buildId: String,
  val targetIds: List<String>,
  val arguments: List<String> = emptyList(),
)

data class BuildTargetRunRequest(
  val buildId: String,
  val targetId: String,
  val arguments: List<String> = emptyList(),
)

data class BuildTargetDependencyModulesRequest(val targetIds: List<String>)

data class BuildTargetDependencyModulesResponse(
  val dependencyModulesByTargetId: Map<String, List<String>>,
)

data class BuildCancelRequest(val buildId: String)

data class BuildCancelResponse(val accepted: Boolean)

data class BuildShutdownRequest(val reason: String)

data class BuildShutdownResponse(val accepted: Boolean)
