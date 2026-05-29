package com.itsaky.androidide.tooling.buildgrpc.service

import com.itsaky.androidide.tooling.buildgrpc.service.BuildServiceArchitecture
import com.itsaky.androidide.tooling.buildgrpc.bridge.AidlGrpcBridge
import com.zerostudio.tooling.buildgrpc.BuildGrpcModule
import com.zerostudio.tooling.buildgrpc.BuildSessionGrpcService
import com.zerostudio.tooling.buildgrpc.DefaultBinaryBuildServiceApi
import com.zerostudio.tooling.buildgrpc.RoutingBuildGrpcModule

/**
 * Factory for creating a fully wired binary build-service runtime.
 */
object BuildServiceRuntimeFactory {
  @JvmStatic
  fun create(
    module: BuildGrpcModule,
    grpcPort: Int,
    reapiEndpoint: String? = null,
    reapiInstanceName: String = "default",
  ): BuildServiceRuntime {
    val service = BuildSessionGrpcService.withReapiEndpoint(
      module = module,
      reapiEndpoint = reapiEndpoint,
      reapiInstanceName = reapiInstanceName,
    )
    val host = BuildGrpcServerHost(port = grpcPort, service = service)
    val routingModule = module as? RoutingBuildGrpcModule
      ?: error("BuildServiceRuntimeFactory requires RoutingBuildGrpcModule for binary bridge wiring")
    val bridge = AidlGrpcBridge(contract = BuildServiceArchitecture, binaryApi = DefaultBinaryBuildServiceApi(routingModule))
    val gateway = BuildServiceGateway(bridge)
    return BuildServiceRuntime(serverHost = host, gateway = gateway)
  }
}
