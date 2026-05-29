package com.zerostudio.tooling.buildgrpc

import com.itsaky.androidide.tooling.api.messages.ExecutionRequest
import com.itsaky.androidide.tooling.api.messages.InitializeProjectParams

/**
 * Migration mapper from legacy tooling/api JSON-RPC messages to BSP-like binary requests.
 */
object LegacyToolingApiMigrationMapper {

  fun toBuildInitialize(
    params: InitializeProjectParams,
    clientName: String,
    clientVersion: String,
  ): BuildInitializeRequest {
    return BuildInitializeRequest(
      workspaceRoot = params.directory,
      buildSystemId = "gradle",
      clientName = clientName,
      clientVersion = clientVersion,
      capabilities = setOf("migration:legacy-jsonrpc"),
    )
  }

  fun toBuildTargetCompile(request: ExecutionRequest): BuildTargetCompileRequest {
    return BuildTargetCompileRequest(
      buildId = request.requestId,
      targetIds = if (request.tasks.isEmpty()) listOf(":") else request.tasks,
      arguments = request.arguments + request.jvmArguments,
    )
  }

  fun toBuildCancel(requestId: String): BuildCancelRequest = BuildCancelRequest(requestId)
}
