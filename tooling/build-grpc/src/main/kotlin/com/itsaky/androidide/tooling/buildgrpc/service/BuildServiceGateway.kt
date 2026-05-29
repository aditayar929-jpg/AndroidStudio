package com.itsaky.androidide.tooling.buildgrpc.service

import com.itsaky.androidide.tooling.buildgrpc.bridge.AidlGrpcBridge
import com.itsaky.androidide.tooling.buildgrpc.model.BuildBridgeEvent
import com.zerostudio.tooling.buildgrpc.proto.StartBuildRequest
import kotlinx.coroutines.flow.Flow

/**
 * Binary build-service gateway for request handling.
 *
 * Responsibilities:
 * - accept binary build requests from IPC-facing layers
 * - route them to AIDL+gRPC bridge
 * - expose binary build-event stream for subscribers
 */
class BuildServiceGateway(
  private val bridge: AidlGrpcBridge,
) {
  fun initialize(requestPayload: ByteArray): ByteArray = bridge.initialize(requestPayload)

  fun submitBuildRequest(requestPayload: ByteArray): Flow<BuildBridgeEvent> =
    bridge.startBuild(requestPayload)

  fun submitBuildRequest(request: StartBuildRequest): Flow<BuildBridgeEvent> =
    submitBuildRequest(request.toByteArray())
}
