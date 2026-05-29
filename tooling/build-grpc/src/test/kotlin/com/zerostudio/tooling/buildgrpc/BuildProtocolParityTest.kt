package com.zerostudio.tooling.buildgrpc

import com.zerostudio.tooling.buildgrpc.proto.InitializeRequest
import com.zerostudio.tooling.buildgrpc.proto.SerializationKind
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuildProtocolParityTest {
  @Test
  fun `aidl api and grpc initialize negotiate equivalent capabilities`() = runBlocking {
    val module = RoutingBuildGrpcModule(emptyMap(), defaultModule = NoopModule())
    val aidl = DefaultBinaryBuildServiceApi(module)
    val grpc = BuildSessionGrpcService(module = module)

    val aidlResp = aidl.buildInitialize(
      BuildInitializeRequest(
        workspaceRoot = "/repo",
        buildSystemId = "gradle",
        clientName = "ide",
        clientVersion = "1",
        capabilities = setOf("streaming"),
      ),
    )
    val grpcResp = grpc.initialize(
      InitializeRequest.newBuilder()
        .setWorkspaceRoot("/repo")
        .setClientName("ide")
        .setClientVersion("1")
        .addSupportedCapabilities("streaming")
        .setSerialization(
          com.zerostudio.tooling.buildgrpc.proto.SerializationPreferences.newBuilder()
            .addPreferred(SerializationKind.SERIALIZATION_KIND_PROTOBUF_BINARY),
        )
        .build(),
    )

    assertEquals(aidlResp.serverName, grpcResp.serverName)
    assertEquals(aidlResp.serverVersion, grpcResp.serverVersion)
    assertTrue(grpcResp.protocolFeaturesList.containsAll(aidlResp.negotiatedCapabilities))
  }
}

private class NoopModule : BuildGrpcModule {
  override suspend fun initialize(request: BuildInit): BuildServerInfo =
    BuildServerInfo("noop", "1", emptyList(), listOf("streaming"))
  override fun startBuild(request: BuildStart) = kotlinx.coroutines.flow.emptyFlow<com.zerostudio.tooling.buildgrpc.proto.BuildEventEnvelope>()
  override suspend fun shutdown(reason: String): Boolean = true
}
