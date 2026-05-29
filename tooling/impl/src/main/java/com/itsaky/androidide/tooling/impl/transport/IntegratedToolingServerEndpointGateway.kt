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
import com.itsaky.androidide.tooling.impl.transport.integrated.IntegratedProtocolCoordinator
import com.itsaky.androidide.tooling.impl.transport.integrated.IntegratedBuildRequestCodec
import com.itsaky.androidide.tooling.impl.transport.integrated.IntegratedBinaryRuntimeBridge
import com.itsaky.androidide.tooling.impl.transport.reapi.GrpcReapiExecutionGateway
import com.itsaky.androidide.tooling.api.messages.result.ExecutionResult.Companion.SUCCESS
import com.itsaky.androidide.tooling.impl.transport.reapi.NoOpReapiExecutionGateway
import com.itsaky.androidide.tooling.impl.transport.reapi.ReapiExecutionGateway
import java.util.concurrent.CompletableFuture
import org.slf4j.LoggerFactory

/**
 * Transitional endpoint for the integrated AIDL+gRPC+REAPI stack.
 *
 * Iteration2 stage: this gateway delegates to legacy JSON-RPC endpoint implementation while
 * preserving an isolated integration seam for the future stack cutover.
 */
class IntegratedToolingServerEndpointGateway(delegate: IToolingApiServer) :
    ToolingTransportServerEndpoint {

  private val log = LoggerFactory.getLogger(IntegratedToolingServerEndpointGateway::class.java)
  private val runtimeConfig = IntegratedTransportRuntimeConfig.fromSystemProperties()
  private val protocolCoordinator = IntegratedProtocolCoordinator(runtimeConfig)
  private val reapiGateway: ReapiExecutionGateway =
      if (runtimeConfig.reapiEnabled && runtimeConfig.reapiEndpoint.isNotBlank()) {
        GrpcReapiExecutionGateway(runtimeConfig.reapiEndpoint, runtimeConfig.reapiInstanceName)
      } else {
        NoOpReapiExecutionGateway()
      }

  init {
    protocolCoordinator.startHandshake().whenComplete { handshake, error ->
      if (error != null) {
        log.error("Integrated handshake bootstrap failed", error)
      } else {
        log.info(
            "Integrated handshake established. version={}, sessionId={}, aidl={}, grpcUds={}, reapi={}",
            handshake.protocolVersion,
            handshake.sessionId,
            handshake.supportsAidlControlPlane,
            handshake.supportsGrpcUdsDataPlane,
            handshake.supportsReapiExecution,
        )
      }
    }

    log.info(
        "Integrated transport gateway booted. reapiEnabled={}, reapiEndpoint='{}', reapiInstance='{}'",
        reapiGateway.isEnabled(),
        runtimeConfig.reapiEndpoint,
        runtimeConfig.reapiInstanceName,
    )
  }

  override fun metadata(): CompletableFuture<ToolingServerMetadata> = CompletableFuture.completedFuture(
      ToolingServerMetadata(pid = -1, toolingApiVersion = "integrated-binary", supportsModelSnapshot = true, supportsQueryService = true)
  )

  override fun initialize(params: InitializeProjectParams): CompletableFuture<InitializeResult> {
      val payload = IntegratedBuildRequestCodec.encodeInitialize(params)
      log.debug("Integrated initialize encoded to binary payload: requestId={}, bytes={}", params.requestId, payload.size)
      invokeRuntime("initialize", payload)
      return CompletableFuture.completedFuture(
          InitializeResult(
              isSuccessful = true,
              failure = null,
              requestId = params.requestId,
              supportsModelSnapshot = true,
              supportsQueryService = true,
          )
      )
  }

  override fun executeTasks(message: TaskExecutionMessage): CompletableFuture<TaskExecutionResult> {
      val payload = IntegratedBuildRequestCodec.encodeTaskExecution(message)
      log.debug("Integrated executeTasks encoded to binary payload: tasks={}, bytes={}", message.tasks.size, payload.size)
      invokeRuntime("submitBuildRequest", payload)
      return CompletableFuture.completedFuture(TaskExecutionResult.SUCCESS)
  }

  override fun execute(request: ExecutionRequest): CompletableFuture<ExecutionResult> {
      val payload = IntegratedBuildRequestCodec.encodeExecution(request)
      log.debug("Integrated execute encoded to binary payload: requestId={}, tasks={}, bytes={}", request.requestId, request.tasks.size, payload.size)
      invokeRuntime("submitBuildRequest", payload)
      return CompletableFuture.completedFuture(SUCCESS.copy(requestId = request.requestId))
  }

  private fun invokeRuntime(methodName: String, payload: ByteArray) {
    val runtime = IntegratedBinaryRuntimeBridge.getOrCreate()
    runtime.javaClass.getMethod(methodName, ByteArray::class.java).invoke(runtime, payload)
  }

  override fun cancelCurrentBuild(): CompletableFuture<BuildCancellationRequestResult> =
      CompletableFuture.completedFuture(BuildCancellationRequestResult(true))

  override fun shutdown(): CompletableFuture<Void> {
    reapiGateway.shutdown()
    return protocolCoordinator.shutdown()
  }
}
