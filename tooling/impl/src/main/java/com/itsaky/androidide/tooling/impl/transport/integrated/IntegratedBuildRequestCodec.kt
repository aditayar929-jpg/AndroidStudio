package com.itsaky.androidide.tooling.impl.transport.integrated

import com.itsaky.androidide.tooling.api.messages.ExecutionRequest
import com.itsaky.androidide.tooling.api.messages.InitializeProjectParams
import com.itsaky.androidide.tooling.api.messages.TaskExecutionMessage
import com.zerostudio.tooling.buildgrpc.proto.InitializeRequest
import com.zerostudio.tooling.buildgrpc.proto.StartBuildRequest

/**
 * Codec for converting tooling transport requests into build-grpc protobuf payloads.
 */
object IntegratedBuildRequestCodec {

  fun encodeInitialize(params: InitializeProjectParams): ByteArray =
    InitializeRequest.newBuilder()
      .setWorkspaceRoot(params.directory)
      .setClientName("androidide-app")
      .setClientVersion("integrated")
      .build()
      .toByteArray()

  fun encodeTaskExecution(message: TaskExecutionMessage): ByteArray =
    StartBuildRequest.newBuilder()
      .setBuildId("task-exec")
      .addAllTargets(message.tasks)
      .putAllOptions((message.arguments + message.jvmArguments).associateWith { "true" })
      .build()
      .toByteArray()

  fun encodeExecution(request: ExecutionRequest): ByteArray =
    StartBuildRequest.newBuilder()
      .setBuildId(request.requestId)
      .addAllTargets(request.tasks)
      .putAllOptions((request.arguments + request.jvmArguments).associateWith { "true" })
      .build()
      .toByteArray()
}
