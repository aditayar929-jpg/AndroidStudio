package com.itsaky.androidide.tooling.impl.transport.integrated

import com.itsaky.androidide.tooling.api.transport.IntegratedProtocolContracts
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import org.slf4j.LoggerFactory

/**
 * Minimal bootstrap for gRPC-over-UDS runtime.
 *
 * This Iteration2 implementation intentionally avoids binding to concrete grpc-netty UDS classes
 * until CI/runtime matrix for Android/Termux JVM is validated. It still provides deterministic
 * local socket lifecycle hooks + handshake metadata for upper gateway integration.
 */
class IntegratedGrpcUdsServerBootstrap(
    private val lifecycle: IntegratedTransportLifecycle,
    private val session: IntegratedTransportSession,
) {

  private val log = LoggerFactory.getLogger(IntegratedGrpcUdsServerBootstrap::class.java)
  private val started = AtomicBoolean(false)
  private var startTime: Instant? = null

  fun start(): CompletableFuture<IntegratedProtocolContracts.Handshake> {
    val future = CompletableFuture<IntegratedProtocolContracts.Handshake>()
    try {
      if (!lifecycle.markStarting()) {
        future.completeExceptionally(
            IllegalStateException("Unable to start UDS bootstrap from lifecycle state=${lifecycle.state}"),
        )
        return future
      }

      ensureSocketParentExists(session.endpoint.udsPath)
      touchSocketPlaceholder(session.endpoint.udsPath)
      started.set(true)
      startTime = Instant.now()
      lifecycle.markRunning()

      val handshake =
          IntegratedProtocolContracts.Handshake(
              protocolVersion = session.protocolVersion,
              sessionId = session.sessionId,
              supportsAidlControlPlane = true,
              supportsGrpcUdsDataPlane = true,
              supportsReapiExecution = session.reapiEnabled,
          )

      log.info(
          "Integrated gRPC UDS bootstrap started. sessionId={}, udsPath='{}', aidlService='{}', reapiEnabled={}",
          session.sessionId,
          session.endpoint.udsPath,
          session.endpoint.aidlServiceName,
          session.reapiEnabled,
      )
      future.complete(handshake)
    } catch (t: Throwable) {
      lifecycle.markFailed()
      log.error("Failed to start integrated gRPC UDS bootstrap", t)
      future.completeExceptionally(t)
    }
    return future
  }

  fun ping(): Duration {
    check(started.get()) { "UDS runtime is not started" }
    val now = Instant.now()
    val startedAt = startTime ?: now
    return Duration.between(startedAt, now)
  }

  fun stop(): CompletableFuture<Void> {
    val future = CompletableFuture<Void>()
    try {
      if (!started.get()) {
        future.complete(null)
        return future
      }
      lifecycle.markStopping()
      started.set(false)
      lifecycle.markStopped()
      log.info("Integrated gRPC UDS bootstrap stopped. sessionId={}", session.sessionId)
      future.complete(null)
    } catch (t: Throwable) {
      lifecycle.markFailed()
      future.completeExceptionally(t)
    }
    return future
  }

  private fun ensureSocketParentExists(path: String) {
    val parent = File(path).parentFile ?: return
    if (!parent.exists() && !parent.mkdirs()) {
      error("Failed to create parent directory for UDS path: ${parent.absolutePath}")
    }
  }

  private fun touchSocketPlaceholder(path: String) {
    val file = File(path)
    if (!file.exists()) {
      runCatching { file.createNewFile() }
          .onFailure { log.warn("Unable to create UDS placeholder '{}': {}", path, it.message) }
    }
  }
}
