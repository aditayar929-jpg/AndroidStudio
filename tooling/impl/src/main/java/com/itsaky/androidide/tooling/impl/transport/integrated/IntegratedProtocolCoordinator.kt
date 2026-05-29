package com.itsaky.androidide.tooling.impl.transport.integrated

import com.itsaky.androidide.tooling.api.transport.IntegratedProtocolContracts
import com.itsaky.androidide.tooling.api.transport.ToolingTransportMode
import com.itsaky.androidide.tooling.impl.transport.IntegratedTransportRuntimeConfig
import java.util.concurrent.CompletableFuture

/**
 * Coordinates local integrated protocol concerns:
 * 1) endpoint/session materialization
 * 2) UDS bootstrap handshake
 * 3) capability envelope generation for upper layers
 */
class IntegratedProtocolCoordinator(
    private val runtimeConfig: IntegratedTransportRuntimeConfig,
    private val lifecycle: IntegratedTransportLifecycle = IntegratedTransportLifecycle(),
) {

  val session: IntegratedTransportSession by lazy {
    IntegratedTransportSession(
        endpoint =
            IntegratedProtocolContracts.LocalEndpoint(
                udsPath = defaultUdsPath(),
                aidlServiceName = "com.itsaky.androidide.tooling.IntegratedToolingService",
            ),
        reapiEnabled = runtimeConfig.reapiEnabled,
        reapiInstanceName = runtimeConfig.reapiInstanceName,
    )
  }

  private val grpcBootstrap: IntegratedGrpcUdsServerBootstrap by lazy {
    IntegratedGrpcUdsServerBootstrap(lifecycle = lifecycle, session = session)
  }

  fun startHandshake(): CompletableFuture<IntegratedProtocolContracts.Handshake> = grpcBootstrap.start()

  fun capabilityEnvelope(toolingApiVersion: String): IntegratedProtocolContracts.CapabilityEnvelope =
      IntegratedProtocolContracts.CapabilityEnvelope(
          toolingApiVersion = toolingApiVersion,
          transportMode = ToolingTransportMode.INTEGRATED_AIDL_GRPC_REAPI,
          reapiInstanceName = runtimeConfig.reapiInstanceName,
          reapiEnabled = runtimeConfig.reapiEnabled,
      )

  fun pingUdsUptimeMillis(): Long = grpcBootstrap.ping().toMillis()

  fun shutdown(): CompletableFuture<Void> = grpcBootstrap.stop()

  private fun defaultUdsPath(): String {
    val configured = System.getProperty("androidide.tooling.integrated.uds.path", "").trim()
    return if (configured.isNotEmpty()) configured else "/tmp/androidide-tooling-integrated.sock"
  }
}
