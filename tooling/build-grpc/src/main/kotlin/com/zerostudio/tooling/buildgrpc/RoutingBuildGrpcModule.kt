package com.zerostudio.tooling.buildgrpc

import com.zerostudio.tooling.buildgrpc.proto.BuildEventEnvelope
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.ConcurrentHashMap

/**
 * Orchestrator that routes requests to Gradle/Bazel specific backends.
 */
class RoutingBuildGrpcModule(
  private val backendRegistry: BuildBackendRegistry,
) : BuildGrpcModule {

  private val backendByBuildId = ConcurrentHashMap<String, BuildBackend>()
  private var sessionContext: BuildSessionContext? = null

  override suspend fun initialize(request: BuildInit): BuildServerInfo {
    sessionContext = BuildSessionContextResolver.fromInit(request)
    val backend = resolveContextBackend()
    val info = backend.initialize(request)
    return info.copy(
      protocolFeatures = (info.protocolFeatures + listOf(
        "buildSystem:${sessionContext!!.buildSystem.name.lowercase()}",
        "routing.backends:${backendRegistry.ids().sorted().joinToString(",")}",
      )).distinct(),
    )
  }

  override fun startBuild(request: BuildStart): Flow<BuildEventEnvelope> {
    val backend = selectBackend(request)
    backendByBuildId[request.buildId] = backend
    return backend.startBuild(request)
  }

  override suspend fun shutdown(reason: String): Boolean {
    val grouped = backendByBuildId.values.toSet()
    backendByBuildId.clear()
    sessionContext = null
    return grouped.all { it.shutdown(reason) }
  }

  suspend fun executeAction(request: ActionExecutionRequest): ActionExecutionResult {
    val backend = backendByBuildId[request.buildId] ?: resolveContextBackend()
    return backend.executeAction(request)
  }

  private fun selectBackend(request: BuildStart): BuildBackend {
    val explicitBackendId = request.options[BACKEND_OPTION_KEY]
    if (!explicitBackendId.isNullOrBlank()) {
      return backendRegistry.requireById(explicitBackendId)
    }
    return resolveContextBackend()
  }

  private fun resolveContextBackend(): BuildBackend {
    val context = sessionContext ?: error("Build session is not initialized")
    return backendRegistry.requireByBuildSystem(context.buildSystem)
  }

  companion object {
    const val BACKEND_OPTION_KEY = "build.backend"
  }
}
