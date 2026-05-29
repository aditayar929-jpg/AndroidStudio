package com.itsaky.androidide.tooling.api.transport

/**
 * Transport stack selection for tooling server endpoint wiring.
 *
 * Note: AIDL + gRPC(UDS) + REAPI are treated as one integrated stack, not three independent
 * modes.
 */
enum class ToolingTransportMode(val wireValue: String) {
  LEGACY_JSONRPC("legacy"),
  INTEGRATED_AIDL_GRPC_REAPI("integrated");

  companion object {
    @JvmStatic
    fun fromWireValue(value: String?): ToolingTransportMode? {
      val normalized = value.orEmpty().trim().lowercase()
      if (normalized.isBlank()) {
        return LEGACY_JSONRPC
      }
      return entries.firstOrNull { it.wireValue == normalized }
    }
  }
}
