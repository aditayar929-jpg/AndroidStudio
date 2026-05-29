package com.itsaky.androidide.tooling.impl.transport

import com.itsaky.androidide.tooling.api.IToolingApiServer
import com.itsaky.androidide.tooling.api.messages.ExecutionRequest
import com.itsaky.androidide.tooling.api.messages.InitializeProjectParams
import com.itsaky.androidide.tooling.api.messages.TaskExecutionMessage
import com.itsaky.androidide.tooling.api.messages.result.BuildCancellationRequestResult
import com.itsaky.androidide.tooling.api.messages.result.ExecutionResult
import com.itsaky.androidide.tooling.api.messages.result.InitializeResult
import com.itsaky.androidide.tooling.api.messages.result.TaskExecutionResult
import com.itsaky.androidide.tooling.api.models.ToolingServerMetadata
import com.itsaky.androidide.tooling.api.transport.ToolingTransportServerEndpoint
import java.util.concurrent.CompletableFuture

/**
 * Legacy adapter that exposes [IToolingApiServer] through transport-neutral SPI.
 */
class LegacyToolingServerEndpoint(private val delegate: IToolingApiServer) :
    ToolingTransportServerEndpoint {

  override fun metadata(): CompletableFuture<ToolingServerMetadata> = delegate.metadata()

  override fun initialize(params: InitializeProjectParams): CompletableFuture<InitializeResult> =
      delegate.initialize(params)

  override fun executeTasks(message: TaskExecutionMessage): CompletableFuture<TaskExecutionResult> =
      delegate.executeTasks(message)

  override fun execute(request: ExecutionRequest): CompletableFuture<ExecutionResult> =
      delegate.execute(request)

  override fun cancelCurrentBuild(): CompletableFuture<BuildCancellationRequestResult> =
      delegate.cancelCurrentBuild()

  override fun shutdown(): CompletableFuture<Void> = delegate.shutdown()
}
