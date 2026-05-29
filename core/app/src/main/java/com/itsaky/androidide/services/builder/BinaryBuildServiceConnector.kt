package com.itsaky.androidide.services.builder

import com.itsaky.androidide.tooling.buildgrpc.service.BuildServiceRuntime
import com.itsaky.androidide.tooling.buildgrpc.service.BuildServiceRuntimeState
import com.itsaky.androidide.tooling.buildgrpc.model.BuildBridgeEvent
import kotlinx.coroutines.flow.Flow

/**
 * App-side binary build-service connector.
 *
 * This is the app integration entry for the AIDL + gRPC + REAPI build protocol.
 */
class BinaryBuildServiceConnector(
  private val runtime: BuildServiceRuntime,
) {
  fun ensureStarted() {
    if (runtime.state() == BuildServiceRuntimeState.STOPPED) {
      runtime.start()
    }
  }

  fun initialize(payload: ByteArray): ByteArray {
    ensureStarted()
    return runtime.initialize(payload)
  }

  fun requestBuild(payload: ByteArray): Flow<BuildBridgeEvent> {
    ensureStarted()
    return runtime.submitBuildRequest(payload)
  }

  fun shutdown() {
    runtime.stop()
  }
}
