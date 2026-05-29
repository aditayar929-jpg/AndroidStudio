/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.tooling.api

import com.itsaky.androidide.tooling.api.messages.InitializeProjectParams
import com.itsaky.androidide.tooling.api.messages.TaskExecutionMessage
import com.itsaky.androidide.tooling.api.messages.ExecutionRequest
import com.itsaky.androidide.tooling.api.messages.result.BuildCancellationRequestResult
import com.itsaky.androidide.tooling.api.messages.result.ExecutionResult
import com.itsaky.androidide.tooling.api.messages.result.InitializeResult
import com.itsaky.androidide.tooling.api.messages.result.TaskExecutionResult
import com.itsaky.androidide.tooling.api.models.ToolingServerMetadata
import java.util.concurrent.CompletableFuture
import com.zerostudio.tooling.buildgrpc.customapi.rpc.BinaryRpcRequest
import com.zerostudio.tooling.buildgrpc.customapi.rpc.BinaryRpcSegment

/**
 * A tooling api server provides services related to the Gradle Tooling API.
 *
 * @author Akash Yadav
 */
@BinaryRpcSegment("server")
interface IToolingApiServer {

  /** Returns the metadata about the tooling server. */
  @BinaryRpcRequest fun metadata(): CompletableFuture<ToolingServerMetadata>

  /** Initialize the server with the project directory. */
  @BinaryRpcRequest fun initialize(params: InitializeProjectParams): CompletableFuture<InitializeResult>

  /** Is the server initialized? */
  @BinaryRpcRequest fun isServerInitialized(): CompletableFuture<Boolean>

  /** Get the root project. */
  @BinaryRpcRequest fun getRootProject(): CompletableFuture<IProject>

  /** Execute the tasks specified in the message. */
  @BinaryRpcRequest
  fun executeTasks(message: TaskExecutionMessage): CompletableFuture<TaskExecutionResult>

  /** Execute a generic build request. */
  @BinaryRpcRequest fun execute(request: ExecutionRequest): CompletableFuture<ExecutionResult>

  /**
   * Cancel the current build.
   *
   * @return A [CompletableFuture] which completes when the current build cancellation process
   *   finishes (either successfully or with an error).
   */
  @BinaryRpcRequest fun cancelCurrentBuild(): CompletableFuture<BuildCancellationRequestResult>

  /**
   * Shutdown the tooling API server. This will disconnect all the project connection instances.
   *
   * @return A [CompletableFuture] which completes when the shutdown process is finished.
   */
  @BinaryRpcRequest fun shutdown(): CompletableFuture<Void>
}
