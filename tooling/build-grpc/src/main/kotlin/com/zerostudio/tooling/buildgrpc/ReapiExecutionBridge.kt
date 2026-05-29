package com.zerostudio.tooling.buildgrpc

import build.bazel.remote.execution.v2.Digest
import build.bazel.remote.execution.v2.Platform

data class ReapiExecuteRequest(
  val buildId: String,
  val instanceName: String,
  val actionDigest: Digest,
  val platform: Platform?,
  val priority: Int,
)

interface ReapiExecutionBridge {
  suspend fun execute(request: ReapiExecuteRequest): ActionExecutionResult?
}

class NoopReapiExecutionBridge : ReapiExecutionBridge {
  override suspend fun execute(request: ReapiExecuteRequest): ActionExecutionResult? = null
}

