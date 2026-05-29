package com.itsaky.androidide.tooling.buildgrpc.service

import com.itsaky.androidide.tooling.buildgrpc.api.BuildProtocolContract

object BuildServiceArchitecture : BuildProtocolContract {
  override val protocolVersion: String = "0.2.0"
  override val serverName: String = "ZeroStudioBuildService"

  override fun supportedFeatures(): Set<String> =
    linkedSetOf(
      "bsp-like-session-lifecycle",
      "build-event-protocol-stream",
      "reapi-execution-metadata",
      "aidl-grpc-hybrid-bridge",
      "in-jvm-local-transport",
      "context-negotiation-and-capability-profiles",
      "chunked-resource-transfer",
      "binary-serialization-codec-negotiation",
      "large-artifact-streaming",
      "bidirectional-control-plane",
    )
}
