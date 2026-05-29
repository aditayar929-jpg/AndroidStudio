package com.itsaky.androidide.tooling.buildgrpc.ipc

import com.itsaky.androidide.tooling.buildgrpc.IBuildServiceBridge
import com.itsaky.androidide.tooling.buildgrpc.model.BuildBridgeEvent
import com.itsaky.androidide.tooling.buildgrpc.service.BuildServiceRuntime
import com.zerostudio.tooling.buildgrpc.proto.BuildEventEnvelope
import com.zerostudio.tooling.buildgrpc.proto.StartBuildRequest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BinaryBuildServiceBridgeStub(
  private val runtime: BuildServiceRuntime,
) : IBuildServiceBridge.Stub() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val latestEventByRequest = ConcurrentHashMap<String, ByteArray>()

  override fun initialize(initializeBuildRequestPayload: ByteArray): ByteArray =
    runtime.initialize(initializeBuildRequestPayload)

  override fun queryTargets(queryBuildTargetsRequestPayload: ByteArray): ByteArray =
    runtime.initialize(queryBuildTargetsRequestPayload)

  override fun executeBuild(executeBuildRequestPayload: ByteArray): ByteArray {
    val request = StartBuildRequest.parseFrom(executeBuildRequestPayload)
    scope.launch {
      runtime.submitBuildRequest(executeBuildRequestPayload).collect { event: BuildBridgeEvent ->
        latestEventByRequest[request.buildId] = event.payload
      }
    }
    return ByteArray(0)
  }

  override fun getBuildResult(executeBuildRequestPayload: ByteArray): ByteArray {
    val request = StartBuildRequest.parseFrom(executeBuildRequestPayload)
    return latestEventByRequest[request.buildId] ?: BuildEventEnvelope.getDefaultInstance().toByteArray()
  }
}
