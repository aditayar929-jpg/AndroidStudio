package com.itsaky.androidide.tooling.impl.transport.integrated

import com.itsaky.androidide.tooling.api.transport.IntegratedProtocolContracts
import java.time.Instant
import java.util.UUID

/** Runtime view for integrated transport session on server side. */
data class IntegratedTransportSession(
    val sessionId: String = UUID.randomUUID().toString(),
    val createdAt: Instant = Instant.now(),
    val endpoint: IntegratedProtocolContracts.LocalEndpoint,
    val reapiEnabled: Boolean,
    val reapiInstanceName: String,
    val protocolVersion: Int = 1,
)

/**
 * Server-side capabilities exported through integrated handshake.
 *
 * Keep this stable and backward-compatible: fields can be added but should not be removed in
 * Iteration2.
 */
data class IntegratedServerCapabilities(
    val supportsAidlControlPlane: Boolean,
    val supportsGrpcUdsDataPlane: Boolean,
    val supportsReapiExecution: Boolean,
    val supportsModelSnapshot: Boolean,
    val supportsPhasedAction: Boolean,
    val supportsQueryService: Boolean,
)

/** Lifecycle state machine for local UDS transport runtime. */
enum class IntegratedTransportLifecycleState {
  NEW,
  STARTING,
  RUNNING,
  STOPPING,
  STOPPED,
  FAILED,
}
