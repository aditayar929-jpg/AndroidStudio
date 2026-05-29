package com.zerostudio.tooling.buildgrpc

import io.grpc.ManagedChannelBuilder

/** Factory to create REAPI bridges for production vs fallback usage. */
object ReapiExecutionBridgeFactory {

  fun fromEndpoint(endpoint: String?, instanceName: String): ReapiExecutionBridge {
    if (endpoint.isNullOrBlank()) return NoopReapiExecutionBridge()

    val channel = ManagedChannelBuilder.forTarget(endpoint)
      .usePlaintext()
      .build()

    return RemoteReapiExecutionBridge(
      channel = channel,
      instanceName = instanceName,
    )
  }
}
