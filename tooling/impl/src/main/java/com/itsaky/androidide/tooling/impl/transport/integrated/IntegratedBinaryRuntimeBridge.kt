package com.itsaky.androidide.tooling.impl.transport.integrated

import com.itsaky.androidide.tooling.buildgrpc.service.BuildServiceRuntime
import com.itsaky.androidide.tooling.buildgrpc.service.BuildServiceRuntimeFactory
import com.zerostudio.tooling.buildgrpc.InProcessBuildGrpcModule
import java.util.concurrent.atomic.AtomicReference

/**
 * Shared runtime holder for integrated binary transport execution.
 */
object IntegratedBinaryRuntimeBridge {
  private val runtimeRef = AtomicReference<BuildServiceRuntime?>()

  fun getOrCreate(): BuildServiceRuntime {
    runtimeRef.get()?.let { return it }

    val created = BuildServiceRuntimeFactory.create(
      module = InProcessBuildGrpcModule(),
      grpcPort = 47920,
      reapiEndpoint = System.getProperty("androidide.tooling.reapi.endpoint", ""),
      reapiInstanceName = System.getProperty("androidide.tooling.reapi.instance", "default"),
    ).start()

    runtimeRef.compareAndSet(null, created)
    return runtimeRef.get() ?: created
  }
}
