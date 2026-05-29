package com.itsaky.androidide.tooling.impl.transport.reapi

import build.bazel.remote.execution.v2.ActionResult
import build.bazel.remote.execution.v2.ActionCacheGrpc
import build.bazel.remote.execution.v2.ExecuteResponse
import build.bazel.remote.execution.v2.ExecutionGrpc
import build.bazel.remote.execution.v2.GetActionResultRequest
import build.bazel.remote.execution.v2.Platform
import build.bazel.remote.execution.v2.RequestMetadata
import build.bazel.remote.execution.v2.ToolDetails
import build.bazel.remote.execution.v2.UpdateActionResultRequest
import build.bazel.semver.SemVer
import com.itsaky.androidide.tooling.reapi.protocol.ReapiProtoCatalog
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory

/** Typed digest contract mapped to REAPI Digest message on demand. */
data class ReapiDigest(
    val hash: String,
    val sizeBytes: Long,
)

data class ReapiExecutionRequest(
    val actionDigest: ReapiDigest,
    val skipCacheLookup: Boolean,
    val executionPolicyPriority: Int = 0,
)

data class ReapiClientMetadata(
    val toolName: String = "ZeroStudio",
    val toolVersion: String = "iteration2",
    val actionId: String = "",
)

/**
 * REAPI gateway seam for integrated transport stack.
 *
 * This is now strongly typed to generated proto message families so upper layers can evolve from
 * placeholder logging to real gRPC calls without changing contracts again.
 */
interface ReapiExecutionGateway {
  fun isEnabled(): Boolean

  fun queryActionResult(digest: ReapiDigest): ActionResult?

  fun executeAction(request: ReapiExecutionRequest, metadata: ReapiClientMetadata = ReapiClientMetadata()): ExecuteResponse?

  fun publishActionResult(digest: ReapiDigest, result: ActionResult)

  fun supportedProtoPackages(): Set<String> = ReapiProtoCatalog.allPackages

  fun shutdown()
}

/** No-op implementation used before REAPI endpoint configuration is provided. */
class NoOpReapiExecutionGateway : ReapiExecutionGateway {
  override fun isEnabled(): Boolean = false

  override fun queryActionResult(digest: ReapiDigest): ActionResult? = null

  override fun executeAction(
      request: ReapiExecutionRequest,
      metadata: ReapiClientMetadata,
  ): ExecuteResponse? = null

  override fun publishActionResult(digest: ReapiDigest, result: ActionResult) = Unit

  override fun shutdown() = Unit
}

/**
 * gRPC REAPI gateway implementation (minimal subset for Iteration2).
 *
 * Supports:
 * - GetActionResult
 * - UpdateActionResult
 *
 * Execute() remains staged because Operation streaming/result polling strategy is still being
 * integrated with transport lifecycle and cancellation semantics.
 */
class GrpcReapiExecutionGateway(
    private val endpoint: String,
    private val instanceName: String,
) : ReapiExecutionGateway {

  private val log = LoggerFactory.getLogger(GrpcReapiExecutionGateway::class.java)
  private val channel: ManagedChannel? = createChannel(endpoint)

  override fun isEnabled(): Boolean = endpoint.isNotBlank() && channel != null

  override fun queryActionResult(digest: ReapiDigest): ActionResult? {
    val ch = channel ?: return null
    return runCatching {
          val request =
              GetActionResultRequest.newBuilder()
                  .setInstanceName(instanceName)
                  .setActionDigest(
                      build.bazel.remote.execution.v2.Digest.newBuilder()
                          .setHash(digest.hash)
                          .setSizeBytes(digest.sizeBytes)
                          .build(),
                  )
                  .build()
          ActionCacheGrpc.newBlockingStub(ch).getActionResult(request)
        }
        .onFailure {
          log.warn(
              "REAPI getActionResult failed. endpoint={}, instanceName={}, digest={}#{}, cause={}",
              endpoint,
              instanceName,
              digest.hash,
              digest.sizeBytes,
              it.message,
          )
        }
        .getOrNull()
  }

  override fun executeAction(
      request: ReapiExecutionRequest,
      metadata: ReapiClientMetadata,
  ): ExecuteResponse? {
    log.debug(
        "REAPI executeAction deferred. endpoint={}, instanceName={}, digest={}#{}, skipCacheLookup={}, priority={}, actionId={}",
        endpoint,
        instanceName,
        request.actionDigest.hash,
        request.actionDigest.sizeBytes,
        request.skipCacheLookup,
        request.executionPolicyPriority,
        metadata.actionId,
    )
    return null
  }

  override fun publishActionResult(digest: ReapiDigest, result: ActionResult) {
    val ch = channel ?: return
    runCatching {
          val request =
              UpdateActionResultRequest.newBuilder()
                  .setInstanceName(instanceName)
                  .setActionDigest(
                      build.bazel.remote.execution.v2.Digest.newBuilder()
                          .setHash(digest.hash)
                          .setSizeBytes(digest.sizeBytes)
                          .build(),
                  )
                  .setActionResult(result)
                  .build()
          ActionCacheGrpc.newBlockingStub(ch).updateActionResult(request)
        }
        .onFailure {
          log.warn(
              "REAPI updateActionResult failed. endpoint={}, instanceName={}, digest={}#{}, cause={}",
              endpoint,
              instanceName,
              digest.hash,
              digest.sizeBytes,
              it.message,
          )
        }
  }

  override fun shutdown() {
    runCatching {
      channel?.shutdown()?.awaitTermination(3, TimeUnit.SECONDS)
      channel?.shutdownNow()
    }
    log.debug("REAPI gateway shutdown. endpoint={}, instanceName={}", endpoint, instanceName)
  }

  private fun createChannel(endpoint: String): ManagedChannel? {
    if (endpoint.isBlank()) return null
    val cleaned = endpoint.removePrefix("dns:///")
    return ManagedChannelBuilder.forTarget(cleaned).usePlaintext().build()
  }

  fun buildRequestMetadata(metadata: ReapiClientMetadata): RequestMetadata =
      RequestMetadata.newBuilder()
          .setToolDetails(
              ToolDetails.newBuilder().setToolName(metadata.toolName).setToolVersion(metadata.toolVersion).build(),
          )
          .setActionId(metadata.actionId)
          .build()

  fun buildExecutionPolicy(priority: Int): Platform.Property =
      Platform.Property.newBuilder().setName("priority").setValue(priority.toString()).build()

  fun supportedSemanticVersion(): SemVer =
      SemVer.newBuilder().setMajor(2).setMinor(0).setPatch(0).build()
}
