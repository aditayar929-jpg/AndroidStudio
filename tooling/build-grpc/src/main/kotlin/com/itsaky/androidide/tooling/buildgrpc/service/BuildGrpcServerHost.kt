package com.itsaky.androidide.tooling.buildgrpc.service

import com.zerostudio.tooling.buildgrpc.BuildSessionGrpcService
import io.grpc.Server
import io.grpc.ServerBuilder
import java.io.Closeable
import java.util.concurrent.TimeUnit

/**
 * Host controller for starting/stopping BuildSession gRPC service.
 */
class BuildGrpcServerHost(
  private val port: Int,
  private val service: BuildSessionGrpcService,
) : Closeable {
  @Volatile private var server: Server? = null

  fun start(): BuildGrpcServerHost {
    if (server != null) return this
    server = ServerBuilder.forPort(port)
      .addService(service)
      .build()
      .start()
    return this
  }

  fun isRunning(): Boolean = server?.isShutdown == false

  override fun close() {
    val active = server ?: return
    active.shutdown()
    active.awaitTermination(5, TimeUnit.SECONDS)
    server = null
  }
}
