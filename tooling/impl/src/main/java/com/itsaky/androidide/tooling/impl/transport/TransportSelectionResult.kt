package com.itsaky.androidide.tooling.impl.transport

import com.itsaky.androidide.tooling.api.transport.ToolingTransportMode

/**
 * Immutable transport selection decision produced by [ToolingServerEndpointFactories.resolveSelection].
 */
data class TransportSelectionResult(
    val requestedValue: String,
    val requestedMode: ToolingTransportMode?,
    val resolvedMode: ToolingTransportMode,
    val reapiWorkspacePath: String,
    val reapiWorkspaceReady: Boolean,
    val reason: String? = null,
) {
  val isFallback: Boolean
    get() = requestedMode == null || requestedMode != resolvedMode

  val isIntegratedRequested: Boolean
    get() = requestedMode == ToolingTransportMode.INTEGRATED_AIDL_GRPC_REAPI

  companion object {
    fun legacy(
        requestedValue: String,
        requestedMode: ToolingTransportMode?,
        reapiWorkspacePath: String,
        reapiWorkspaceReady: Boolean,
        reason: String? = null,
    ): TransportSelectionResult {
      return TransportSelectionResult(
          requestedValue = requestedValue,
          requestedMode = requestedMode,
          resolvedMode = ToolingTransportMode.LEGACY_JSONRPC,
          reapiWorkspacePath = reapiWorkspacePath,
          reapiWorkspaceReady = reapiWorkspaceReady,
          reason = reason,
      )
    }

    fun integrated(
        requestedValue: String,
        reapiWorkspacePath: String,
        reapiWorkspaceReady: Boolean,
        reason: String,
    ): TransportSelectionResult {
      return TransportSelectionResult(
          requestedValue = requestedValue,
          requestedMode = ToolingTransportMode.INTEGRATED_AIDL_GRPC_REAPI,
          resolvedMode = ToolingTransportMode.INTEGRATED_AIDL_GRPC_REAPI,
          reapiWorkspacePath = reapiWorkspacePath,
          reapiWorkspaceReady = reapiWorkspaceReady,
          reason = reason,
      )
    }
  }
}
