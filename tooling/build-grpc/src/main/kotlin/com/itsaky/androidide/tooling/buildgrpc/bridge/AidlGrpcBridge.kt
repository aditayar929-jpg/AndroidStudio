package com.itsaky.androidide.tooling.buildgrpc.bridge

import com.itsaky.androidide.tooling.buildgrpc.api.BuildProtocolContract
import com.itsaky.androidide.tooling.buildgrpc.model.BuildBridgeEvent
import com.itsaky.androidide.tooling.buildgrpc.model.BuildBridgeRequest
import com.zerostudio.tooling.buildgrpc.BinaryBuildServiceApi
import com.zerostudio.tooling.buildgrpc.BuildInitializeRequest
import com.zerostudio.tooling.buildgrpc.BuildTargetCompileRequest
import com.zerostudio.tooling.buildgrpc.proto.BuildEventEnvelope
import com.zerostudio.tooling.buildgrpc.proto.StartBuildRequest
import com.zerostudio.tooling.buildgrpc.proto.InitializeRequest
import com.zerostudio.tooling.buildgrpc.proto.InitializeResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/**
 * Binary AIDL <-> gRPC bridge gateway for hybrid protocol stack:
 * AIDL (process boundary) + gRPC (service transport) + REAPI capable backend.
 */
class AidlGrpcBridge(
  private val contract: BuildProtocolContract,
  private val binaryApi: BinaryBuildServiceApi,
) {

  fun describe(): String {
    return buildString {
      append(contract.serverName)
      append('@')
      append(contract.protocolVersion)
      append(':')
      append(contract.supportedFeatures().sorted().joinToString(","))
    }
  }

  /**
   * Accepts protobuf bytes from AIDL and returns protobuf bytes for initialize response.
   */
  fun initialize(payload: ByteArray): ByteArray = runBlocking {
    val request = InitializeRequest.parseFrom(payload)
    val response = binaryApi.buildInitialize(
      BuildInitializeRequest(
        workspaceRoot = request.workspaceRoot,
        buildSystemId = request.profile.name,
        clientName = request.clientName,
        clientVersion = request.clientVersion,
        capabilities = request.supportedCapabilitiesList.toSet(),
      ),
    )

    InitializeResponse.newBuilder()
      .setServerName(response.serverName)
      .setServerVersion(response.serverVersion)
      .addAllProtocolFeatures(response.negotiatedCapabilities.sorted())
      .build()
      .toByteArray()
  }

  /**
   * Converts AIDL binary request payload to build API call and exposes binary event payloads.
   */
  fun startBuild(payload: ByteArray): Flow<BuildBridgeEvent> {
    val request = StartBuildRequest.parseFrom(payload)
    val bridgeRequest = BuildBridgeRequest(
      requestId = request.buildId,
      workspaceRoot = "",
      targetIds = request.targetsList,
      arguments = request.optionsMap.entries.map { "${it.key}=${it.value}" },
    )

    return binaryApi.buildTargetCompile(
      BuildTargetCompileRequest(
        buildId = bridgeRequest.requestId,
        targetIds = bridgeRequest.targetIds,
        arguments = bridgeRequest.arguments,
      ),
    ).map { event -> event.toBridgeEvent(bridgeRequest.requestId) }
  }

  private fun BuildEventEnvelope.toBridgeEvent(requestId: String): BuildBridgeEvent =
    BuildBridgeEvent(
      requestId = requestId,
      eventId = "$buildId:$sequence",
      category = kind.name.lowercase(),
      payload = this.toByteArray(),
    )
}
