package com.zerostudio.tooling.buildgrpc

import com.zerostudio.tooling.buildgrpc.proto.BuildEventEnvelope
import kotlinx.coroutines.flow.Flow

/**
 * Extensible backend SPI for build-system-specific execution.
 */
interface BuildBackend {
  val backendId: String
  val buildSystem: BuildSystem

  suspend fun initialize(request: BuildInit): BuildServerInfo

  fun startBuild(request: BuildStart): Flow<BuildEventEnvelope>

  suspend fun executeAction(request: ActionExecutionRequest): ActionExecutionResult

  suspend fun shutdown(reason: String): Boolean
}

class BuildBackendRegistry(
  backends: List<BuildBackend>,
) {
  private val byId: Map<String, BuildBackend> = backends.associateBy { it.backendId }
  private val byBuildSystem: Map<BuildSystem, BuildBackend> = backends.associateBy { it.buildSystem }

  fun requireById(backendId: String): BuildBackend =
    byId[backendId] ?: error("Unknown build backend: $backendId")

  fun requireByBuildSystem(buildSystem: BuildSystem): BuildBackend =
    byBuildSystem[buildSystem] ?: error("No backend registered for build system: $buildSystem")

  fun ids(): Set<String> = byId.keys
}
