package com.itsaky.androidide.tooling.api.transport

/**
 * Unified protocol contract for the integrated local stack:
 * AIDL (session control) + gRPC/Proto (streaming) + REAPI (execution semantics).
 */
object IntegratedProtocolContracts {

  /** Versioned handshake between Android client and Termux JVM tooling server. */
  data class Handshake(
    val protocolVersion: Int = 1,
    val sessionId: String,
    val supportsAidlControlPlane: Boolean,
    val supportsGrpcUdsDataPlane: Boolean,
    val supportsReapiExecution: Boolean,
  )

  /**
   * Local-only endpoint definition.
   * host/port are intentionally absent to enforce non-internet local socket communication.
   */
  data class LocalEndpoint(
    val udsPath: String,
    val aidlServiceName: String,
    val authority: String = "androidide.tooling.local",
  )

  data class CapabilityEnvelope(
    val toolingApiVersion: String,
    val transportMode: ToolingTransportMode,
    val reapiInstanceName: String,
    val reapiEnabled: Boolean,
  )
}
