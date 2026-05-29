package com.zerostudio.tooling.buildgrpc

import com.google.protobuf.ByteString
import com.zerostudio.tooling.buildgrpc.proto.BuildEventEnvelope
import com.zerostudio.tooling.buildgrpc.proto.BuildEventKind
import com.zerostudio.tooling.buildgrpc.proto.ArtifactKind
import com.zerostudio.tooling.buildgrpc.proto.DataTransferEvent
import com.zerostudio.tooling.buildgrpc.proto.ContextFrame
import com.zerostudio.tooling.buildgrpc.proto.DataChunk
import com.zerostudio.tooling.buildgrpc.proto.DataTransferAck
import com.zerostudio.tooling.buildgrpc.proto.CompressionKind
import com.zerostudio.tooling.buildgrpc.proto.FetchDataRequest
import com.zerostudio.tooling.buildgrpc.proto.BuildSessionServiceGrpcKt
import com.zerostudio.tooling.buildgrpc.proto.ExecuteActionRequest
import com.zerostudio.tooling.buildgrpc.proto.ExecuteActionResponse
import com.zerostudio.tooling.buildgrpc.proto.InitializeRequest
import com.zerostudio.tooling.buildgrpc.proto.NegotiateContextRequest
import com.zerostudio.tooling.buildgrpc.proto.NegotiateContextResponse
import com.zerostudio.tooling.buildgrpc.proto.ResourceChunk
import com.zerostudio.tooling.buildgrpc.proto.ResourceTransferAck
import com.zerostudio.tooling.buildgrpc.proto.ResourceTransferRequest
import com.zerostudio.tooling.buildgrpc.proto.SerializationCodec
import com.zerostudio.tooling.buildgrpc.proto.TransferCompression
import com.zerostudio.tooling.buildgrpc.proto.InitializeResponse
import com.zerostudio.tooling.buildgrpc.proto.ShutdownRequest
import com.zerostudio.tooling.buildgrpc.proto.ShutdownResponse
import com.zerostudio.tooling.buildgrpc.proto.StartBuildRequest
import com.zerostudio.tooling.buildgrpc.proto.StreamBuildEventsRequest
import com.zerostudio.tooling.buildgrpc.proto.TransferRejectReason
import com.zerostudio.tooling.buildgrpc.proto.CleanupTransferResponse
import com.zerostudio.tooling.buildgrpc.proto.CleanupTransferRequest
import com.zerostudio.tooling.buildgrpc.proto.QueryTransferCursorResponse
import com.zerostudio.tooling.buildgrpc.proto.QueryTransferCursorRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.flow
import com.itsaky.androidide.tooling.buildgrpc.service.BuildServiceArchitecture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * gRPC service implementation for BuildSessionService.
 */
private data class TransferStats(
  val buildId: String,
  val transferId: String,
  val contentType: String,
  val compression: CompressionKind,
  val transferredBytes: Long,
  val chunkCount: Long,
  val durationMillis: Long,
  val accepted: Boolean,
  val rejectReason: TransferRejectReason = TransferRejectReason.TRANSFER_REJECT_REASON_NONE,
)

class BuildSessionGrpcService(
  private val module: BuildGrpcModule,
  private val actionExecutor: BuildActionExecutor = LocalNoopBuildActionExecutor(),
  private val reapiExecutionBridge: ReapiExecutionBridge = NoopReapiExecutionBridge(),
  private val eventStore: BuildEventStore = BuildEventStore(),
  private val dataStreamStore: BuildDataStreamStore = BuildDataStreamStore(),
  private val contextStateStore: BuildContextStateStore = BuildContextStateStore(),
  private val transferRegistry: BuildTransferRegistry = BuildTransferRegistry(),
) : BuildSessionServiceGrpcKt.BuildSessionServiceCoroutineImplBase() {

  companion object {
    fun withReapiEndpoint(
      module: BuildGrpcModule,
      reapiEndpoint: String?,
      reapiInstanceName: String,
      actionExecutor: BuildActionExecutor = LocalNoopBuildActionExecutor(),
    ): BuildSessionGrpcService = BuildSessionGrpcService(
      module = module,
      actionExecutor = actionExecutor,
      reapiExecutionBridge = ReapiExecutionBridgeFactory.fromEndpoint(reapiEndpoint, reapiInstanceName),
    )
  }
  private val buildSequences = ConcurrentHashMap<String, AtomicLong>()
  private val policyMutex = Mutex()
  @Volatile private var runtimePolicy = RuntimeTransportPolicy(
    maxFrameBytes = 64 * 1024,
    compression = CompressionKind.COMPRESSION_KIND_NONE,
    serialization = com.zerostudio.tooling.buildgrpc.proto.SerializationKind.SERIALIZATION_KIND_PROTOBUF_BINARY,
  )

  override suspend fun initialize(request: InitializeRequest): InitializeResponse {
    policyMutex.withLock {
      runtimePolicy = BuildTransportPolicy.negotiate(request)
    }
    val init = BuildProtocolMapper.toInit(request)
    val serverInfo = module.initialize(init)
    return BuildProtocolMapper.toInitResponse(serverInfo).toBuilder()
      .setTransport(
        com.zerostudio.tooling.buildgrpc.proto.TransportConfig.newBuilder()
          .setMaxFrameBytes(runtimePolicy.maxFrameBytes)
          .setCompression(runtimePolicy.compression)
          .setMultiplexEnabled(request.transport.supportsMultiplex)
          .build(),
      )
      .setSerialization(
        com.zerostudio.tooling.buildgrpc.proto.SerializationConfig.newBuilder()
          .setSelected(runtimePolicy.serialization)
          .build(),
      )
      .build()
  }

  override fun startBuild(request: StartBuildRequest): Flow<BuildEventEnvelope> {
    val buildStart = BuildProtocolMapper.toStart(request)
    return module.startBuild(buildStart).onEach(eventStore::append)
  }

  override fun streamBuildEvents(request: StreamBuildEventsRequest): Flow<BuildEventEnvelope> = flow {
    val history = eventStore.getFromSequence(request.buildId, request.fromSequence)
    history.forEach { emit(it) }
  }

  override suspend fun executeAction(request: ExecuteActionRequest): ExecuteActionResponse {
    val reapiResult =
      if (request.hasReapiActionDigest() && request.reapiActionDigest.hash.isNotBlank()) {
        reapiExecutionBridge.execute(
          ReapiExecuteRequest(
            buildId = request.buildId,
            instanceName = request.instanceName,
            actionDigest = request.reapiActionDigest,
            platform = if (request.hasPlatform()) request.platform else null,
            priority = request.priority,
          ),
        )
      } else {
        null
      }

    if (reapiResult != null) {
      return ExecuteActionResponse.newBuilder()
        .setOperationName(reapiResult.operationName)
        .setStatus(reapiResult.status)
        .setActionResult(ByteString.copyFrom(reapiResult.actionResult))
        .build()
    }

    val actionRequest = ActionExecutionRequest(
      buildId = request.buildId,
      actionDigest = request.actionDigest,
      command = request.command.toByteArray(),
      inputRootDigest = request.inputRootDigest.toByteArray(),
    )

    val result = when (module) {
      is RoutingBuildGrpcModule -> module.executeAction(actionRequest)
      else -> actionExecutor.execute(actionRequest)
    }

    return ExecuteActionResponse.newBuilder()
      .setOperationName(result.operationName)
      .setStatus(result.status)
      .setActionResult(ByteString.copyFrom(result.actionResult))
      .build()
  }



  override suspend fun negotiateContext(request: NegotiateContextRequest): NegotiateContextResponse {
    val accepted = request.requestedCapabilitiesList
      .filter { capability -> capability in BuildServiceArchitecture.supportedFeatures() }

    val codec = if (request.preferredCodec != SerializationCodec.SERIALIZATION_CODEC_UNSPECIFIED) {
      request.preferredCodec
    } else {
      SerializationCodec.SERIALIZATION_CODEC_PROTOBUF
    }

    val compression = if (
      request.preferredCompression != TransferCompression.TRANSFER_COMPRESSION_UNSPECIFIED
    ) {
      request.preferredCompression
    } else {
      TransferCompression.TRANSFER_COMPRESSION_ZSTD
    }

    return NegotiateContextResponse.newBuilder()
      .addAllAcceptedCapabilities(accepted)
      .setNegotiatedCodec(codec)
      .setNegotiatedCompression(compression)
      .setMaxChunkSizeBytes(1024 * 1024)
      .build()
  }

  override suspend fun uploadResource(requests: Flow<ResourceChunk>): ResourceTransferAck {
    var transferId = ""
    var count = 0L
    requests.collect { chunk ->
      transferId = chunk.transferId
      count++
    }

    return ResourceTransferAck.newBuilder()
      .setTransferId(transferId)
      .setAccepted(true)
      .setReceivedChunks(count)
      .setMessage("Resource stream received by stub transport")
      .build()
  }

  override fun downloadResource(request: ResourceTransferRequest): Flow<ResourceChunk> = flow {
    emit(
      ResourceChunk.newBuilder()
        .setTransferId(request.transferId.ifBlank { request.resourceUri })
        .setSequence(0)
        .setEof(true)
        .build(),
    )
  }

  override suspend fun shutdown(request: ShutdownRequest): ShutdownResponse {
    val accepted = module.shutdown(request.reason)
    if (accepted) {
      eventStore.clearAll()
    }
    return ShutdownResponse.newBuilder().setAccepted(accepted).build()
  }
}
