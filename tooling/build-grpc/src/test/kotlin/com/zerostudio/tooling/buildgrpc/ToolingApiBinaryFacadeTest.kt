package com.zerostudio.tooling.buildgrpc

import com.itsaky.androidide.tooling.api.messages.InitializeProjectParams
import com.zerostudio.tooling.buildgrpc.customapi.ToolingApiBinaryFacade
import kotlin.test.Test
import kotlin.test.assertTrue

class ToolingApiBinaryFacadeTest {
  @Test
  fun `initialize maps legacy tooling params to binary initialize`() {
    val api = ToolingApiBinaryFacade(DefaultBinaryBuildServiceApi(RoutingBuildGrpcModule(emptyMap(), NoopFacadeModule())))
    val result = api.initialize(InitializeProjectParams(directory = "/tmp/project")).get()
    assertTrue(result.isSuccessful)
    assertTrue(result.supportsModelSnapshot)
  }
}

private class NoopFacadeModule : BuildGrpcModule {
  override suspend fun initialize(request: BuildInit): BuildServerInfo =
    BuildServerInfo("noop", "1", emptyList(), emptyList())
  override fun startBuild(request: BuildStart) = kotlinx.coroutines.flow.emptyFlow<com.zerostudio.tooling.buildgrpc.proto.BuildEventEnvelope>()
  override suspend fun shutdown(reason: String): Boolean = true
}

