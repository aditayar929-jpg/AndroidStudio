package com.zerostudio.tooling.buildgrpc

import build.bazel.remote.execution.v2.ExecuteRequest
import build.bazel.remote.execution.v2.ExecutionGrpcKt
import com.zerostudio.tooling.buildgrpc.proto.ExecutionStatus
import io.grpc.ManagedChannel
import kotlinx.coroutines.flow.firstOrNull

/**
 * REAPI-backed execution bridge using official remote execution gRPC API.
 */
class RemoteReapiExecutionBridge(
  private val channel: ManagedChannel,
  private val instanceName: String,
) : ReapiExecutionBridge {

  private val executionStub: ExecutionGrpcKt.ExecutionCoroutineStub =
    ExecutionGrpcKt.ExecutionCoroutineStub(channel)

  override suspend fun execute(request: ReapiExecuteRequest): ActionExecutionResult? {
    val executeRequest = ExecuteRequest.newBuilder()
      .setInstanceName(request.instanceName.ifBlank { instanceName })
      .setActionDigest(request.actionDigest)
      .setSkipCacheLookup(false)
      .build()

    val operation = executionStub.execute(executeRequest).firstOrNull() ?: return null

    // Keep this initial implementation allocation-light: return operation metadata bytes.
    val metadataBytes = if (operation.hasMetadata()) operation.metadata.value.toByteArray() else ByteArray(0)

    return ActionExecutionResult(
      operationName = operation.name,
      status = if (operation.done) ExecutionStatus.EXECUTION_STATUS_COMPLETED else ExecutionStatus.EXECUTION_STATUS_EXECUTING,
      actionResult = metadataBytes,
    )
  }
}
