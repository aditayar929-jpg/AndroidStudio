package com.itsaky.androidide.tooling.impl.transport

import com.itsaky.androidide.tooling.api.IToolingApiServer
import com.itsaky.androidide.tooling.api.transport.ToolingTransportMode
import com.itsaky.androidide.tooling.api.transport.ToolingTransportServerEndpoint
import java.io.File
import org.slf4j.LoggerFactory

/**
 * Factory contract for creating a concrete tooling transport endpoint.
 *
 * Transport stack selection is controlled by `androidide.tooling.transport`:
 * - `legacy` (default): JSON-RPC/LSP4J based server proxy.
 * - `integrated`: AIDL + gRPC(UDS) + REAPI integrated stack (reserved, not implemented yet).
 */
fun interface ToolingServerEndpointFactory {
  fun create(server: IToolingApiServer): ToolingTransportServerEndpoint
}

object ToolingServerEndpointFactories {
  private val log = LoggerFactory.getLogger(ToolingServerEndpointFactories::class.java)

  const val TRANSPORT_SWITCH_PROPERTY: String = "androidide.tooling.transport"
  const val LEGACY: String = "legacy"
  const val REAPI_WORKSPACE_PATH: String = "tooling/reapi"

  @JvmStatic
  fun fromSystemProperty(): ToolingServerEndpointFactory {
    val configured = System.getProperty(TRANSPORT_SWITCH_PROPERTY, LEGACY)
    return fromSelection(resolveSelection(configured))
  }

  @JvmStatic
  fun fromTransportValue(value: String?): ToolingServerEndpointFactory {
    return fromSelection(resolveSelection(value))
  }

  @JvmStatic
  fun resolveSelection(value: String?): TransportSelectionResult {
    val configured = value.orEmpty().ifBlank { LEGACY }.trim().lowercase()
    val requestedMode = ToolingTransportMode.fromWireValue(configured)
    val workspaceReady = isReapiWorkspaceReady()
    return when (requestedMode) {
      ToolingTransportMode.LEGACY_JSONRPC ->
          TransportSelectionResult.legacy(
              requestedValue = configured,
              requestedMode = requestedMode,
              reapiWorkspacePath = REAPI_WORKSPACE_PATH,
              reapiWorkspaceReady = workspaceReady,
          )
      ToolingTransportMode.INTEGRATED_AIDL_GRPC_REAPI ->
          if (!workspaceReady) {
            TransportSelectionResult.legacy(
                requestedValue = configured,
                requestedMode = requestedMode,
                reapiWorkspacePath = REAPI_WORKSPACE_PATH,
                reapiWorkspaceReady = false,
                reason =
                    "Missing REAPI workspace at '$REAPI_WORKSPACE_PATH'. Run: git clone https://github.com/bazelbuild/remote-apis.git tooling/reapi",
            )
          } else {
            TransportSelectionResult.integrated(
                requestedValue = configured,
                reapiWorkspacePath = REAPI_WORKSPACE_PATH,
                reapiWorkspaceReady = true,
                reason = "Integrated transport stack '$configured' is in transitional gateway mode",
            )
          }
      null ->
          TransportSelectionResult.legacy(
              requestedValue = configured,
              requestedMode = null,
              reapiWorkspacePath = REAPI_WORKSPACE_PATH,
              reapiWorkspaceReady = workspaceReady,
              reason = "Unknown transport value '$configured'",
          )
    }
  }

  @JvmStatic
  fun fromSelection(selection: TransportSelectionResult): ToolingServerEndpointFactory {
    when (selection.requestedMode) {
      ToolingTransportMode.LEGACY_JSONRPC -> Unit
      ToolingTransportMode.INTEGRATED_AIDL_GRPC_REAPI -> {
        if (!selection.reapiWorkspaceReady) {
          log.warn(
              "Integrated transport requested but REAPI workspace is missing at '{}'. Falling back to '{}'.",
              selection.reapiWorkspacePath,
              LEGACY,
          )
          return ToolingServerEndpointFactory(::LegacyToolingServerEndpoint)
        }
        log.info(
            "Integrated transport stack '{}' is routed through transitional gateway endpoint.",
            selection.requestedValue,
        )
        return ToolingServerEndpointFactory(::IntegratedToolingServerEndpointGateway)
      }
      null -> {
        log.warn(
            "Unknown transport switch '{}' from -D{}. Falling back to '{}'.",
            selection.requestedValue,
            TRANSPORT_SWITCH_PROPERTY,
            LEGACY,
        )
      }
    }
    return ToolingServerEndpointFactory(::LegacyToolingServerEndpoint)
  }

  private fun isReapiWorkspaceReady(): Boolean {
    val root = File(REAPI_WORKSPACE_PATH)
    return root.exists() && File(root, ".git").exists()
  }
}
