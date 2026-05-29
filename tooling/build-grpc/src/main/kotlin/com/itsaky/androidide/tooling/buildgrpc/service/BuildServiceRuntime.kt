package com.itsaky.androidide.tooling.buildgrpc.service

import com.itsaky.androidide.tooling.buildgrpc.model.BuildBridgeEvent
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch

/**
 * Runtime coordinator that ensures build service startup + request handling lifecycle.
 */
class BuildServiceRuntime(
  private val serverHost: BuildGrpcServerHost,
  private val gateway: BuildServiceGateway,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val started = AtomicBoolean(false)
  private val state = AtomicReference(BuildServiceRuntimeState.STOPPED)

  @Synchronized
  fun start(): BuildServiceRuntime {
    if (started.compareAndSet(false, true)) {
      state.set(BuildServiceRuntimeState.STARTING)
      serverHost.start()
      state.set(BuildServiceRuntimeState.RUNNING)
    }
    return this
  }

  fun isRunning(): Boolean = started.get() && serverHost.isRunning()

  fun state(): BuildServiceRuntimeState = state.get()

  fun initialize(initializePayload: ByteArray): ByteArray {
    ensureStarted()
    return gateway.initialize(initializePayload)
  }

  fun submitBuildRequest(requestPayload: ByteArray): Flow<BuildBridgeEvent> {
    ensureStarted()
    return gateway.submitBuildRequest(requestPayload)
      .onCompletion { cause ->
        if (cause != null) {
          // Keep service alive for subsequent requests; only emit lifecycle hook here.
        }
      }
  }

  fun submitBuildRequestAsync(requestPayload: ByteArray) {
    ensureStarted()
    scope.launch {
      gateway.submitBuildRequest(requestPayload).collect { /* caller can use sync flow API when needed */ }
    }
  }

  @Synchronized
  fun stop() {
    if (started.compareAndSet(true, false)) {
      state.set(BuildServiceRuntimeState.STOPPING)
      serverHost.close()
      state.set(BuildServiceRuntimeState.STOPPED)
    }
  }

  private fun ensureStarted() {
    if (!isRunning()) {
      start()
    }
  }
}
