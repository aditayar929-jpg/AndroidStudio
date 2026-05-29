package com.zerostudio.tooling.buildgrpc

import com.zerostudio.tooling.buildgrpc.proto.ExecutionStatus

/**
 * REAPI-oriented action execution abstraction.
 */
interface BuildActionExecutor {
  suspend fun execute(request: ActionExecutionRequest): ActionExecutionResult
}

data class ActionExecutionRequest(
  val buildId: String,
  val actionDigest: String,
  val command: ByteArray,
  val inputRootDigest: ByteArray,
)

data class ActionExecutionResult(
  val operationName: String,
  val status: ExecutionStatus,
  val actionResult: ByteArray,
)

class LocalNoopBuildActionExecutor : BuildActionExecutor {
  override suspend fun execute(request: ActionExecutionRequest): ActionExecutionResult {
    val opName = "build/${request.buildId}/action/${request.actionDigest}"
    return ActionExecutionResult(
      operationName = opName,
      status = ExecutionStatus.EXECUTION_STATUS_COMPLETED,
      actionResult = ByteArray(0),
    )
  }
}
