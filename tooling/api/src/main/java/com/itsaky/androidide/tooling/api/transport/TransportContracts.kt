package com.itsaky.androidide.tooling.api.transport

import com.itsaky.androidide.tooling.api.messages.ExecutionRequest
import com.itsaky.androidide.tooling.api.messages.InitializeProjectParams
import com.itsaky.androidide.tooling.api.messages.TaskExecutionMessage
import com.itsaky.androidide.tooling.api.messages.result.BuildCancellationRequestResult
import com.itsaky.androidide.tooling.api.messages.result.ExecutionResult
import com.itsaky.androidide.tooling.api.messages.result.InitializeResult
import com.itsaky.androidide.tooling.api.messages.result.TaskExecutionResult
import com.itsaky.androidide.tooling.api.models.ToolingServerMetadata
import com.itsaky.androidide.tooling.events.ProgressEvent
import java.io.InputStream
import java.util.concurrent.CompletableFuture

/** Transport-neutral contract for build-service client->server calls. */
interface ToolingTransportServerEndpoint {
  fun metadata(): CompletableFuture<ToolingServerMetadata>

  fun initialize(params: InitializeProjectParams): CompletableFuture<InitializeResult>

  fun executeTasks(message: TaskExecutionMessage): CompletableFuture<TaskExecutionResult>

  fun execute(request: ExecutionRequest): CompletableFuture<ExecutionResult>

  fun cancelCurrentBuild(): CompletableFuture<BuildCancellationRequestResult>

  fun shutdown(): CompletableFuture<Void>
}

/** Transport-neutral observer for server lifecycle and callback events. */
interface ToolingTransportClientObserver {
  fun onLogMessage(tag: String, level: Char, message: String)

  fun onBuildPrepared(buildInfo: com.itsaky.androidide.tooling.api.messages.result.BuildInfo)

  fun onBuildSuccessful(result: com.itsaky.androidide.tooling.api.messages.result.BuildResult)

  fun onBuildFailed(result: com.itsaky.androidide.tooling.api.messages.result.BuildResult)

  fun onProgressEvent(event: ProgressEvent)

  fun buildArguments(): CompletableFuture<List<String>>

  fun onServerStarted(
      serverEndpoint: ToolingTransportServerEndpoint,
      projectProxy: com.itsaky.androidide.tooling.api.IProject,
      errorStream: InputStream,
  )

  fun onServerExited(exitCode: Int)
}
