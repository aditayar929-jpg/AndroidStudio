package com.itsaky.androidide.tooling.buildgrpc.api

/**
 * Contract for a JVM-local build service protocol inspired by BSP + Build Event Protocol + REAPI.
 *
 * - gRPC: typed request/response and event stream.
 * - AIDL: IPC-friendly bridge for Android process boundaries.
 */
interface BuildProtocolContract {
  val protocolVersion: String
  val serverName: String

  fun supportedFeatures(): Set<String>
}
