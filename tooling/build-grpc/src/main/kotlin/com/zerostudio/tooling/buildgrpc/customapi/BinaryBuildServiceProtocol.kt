package com.zerostudio.tooling.buildgrpc.customapi

import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.flow.Flow

/**
 * Standardized binary protocol contract for build-service communication.
 *
 * This contract intentionally avoids JSON-centric APIs and is designed for
 * low-allocation, streaming-oriented and zero-copy friendly interaction.
 */
interface BinaryBuildServiceProtocol {

  suspend fun handshake(request: ProtocolHandshakeRequest): ProtocolHandshakeResponse

  suspend fun openSession(request: OpenSessionRequest): OpenSessionResponse

  suspend fun closeSession(request: CloseSessionRequest): CloseSessionResponse

  suspend fun submitCommand(request: BuildCommandEnvelope): BuildCommandAck

  fun subscribeEvents(request: EventSubscriptionRequest): Flow<BuildEventChunk>

  fun streamArtifact(request: ArtifactStreamRequest): Flow<BinaryChunk>

  suspend fun pushArtifact(request: ArtifactUploadRequest): ArtifactUploadResult

  suspend fun queryModel(request: ModelQueryRequest): ModelQueryResponse

  suspend fun queryCache(request: CacheQueryRequest): CacheQueryResponse
}

data class ProtocolHandshakeRequest(
  val clientName: String,
  val clientVersion: String,
  val supportedProtocolVersions: List<Int>,
  val supportedCapabilities: Set<ProtocolCapability>,
)

data class ProtocolHandshakeResponse(
  val acceptedProtocolVersion: Int,
  val negotiatedCapabilities: Set<ProtocolCapability>,
  val serverName: String,
  val serverVersion: String,
)

enum class ProtocolCapability {
  ZERO_COPY_BINARY_STREAM,
  ASYNC_COMMAND_PIPELINE,
  EVENT_SUBSCRIPTION,
  MODEL_QUERY,
  BUILD_CACHE_QUERY,
  INCREMENTAL_BUILD,
  RESUMABLE_TRANSFER,
}

data class OpenSessionRequest(
  val workspaceRoot: String,
  val buildSystemId: String,
  val metadata: Map<String, String> = emptyMap(),
)

data class OpenSessionResponse(
  val sessionId: String,
  val accepted: Boolean,
)

data class CloseSessionRequest(val sessionId: String)

data class CloseSessionResponse(val closed: Boolean)

data class BuildCommandEnvelope(
  val sessionId: String,
  val commandId: String,
  val kind: BuildCommandKind,
  val payload: BinaryPayloadRef,
  val timeoutMs: Long? = null,
)

enum class BuildCommandKind {
  EXECUTE_TASKS,
  EXECUTE_ACTION,
  CANCEL_TASK,
  SYNC_MODEL,
  REFRESH_DEPENDENCIES,
}

data class BuildCommandAck(
  val commandId: String,
  val accepted: Boolean,
  val queuePosition: Int? = null,
)

data class EventSubscriptionRequest(
  val sessionId: String,
  val channels: Set<EventChannel>,
)

enum class EventChannel {
  LIFECYCLE,
  TASK,
  DIAGNOSTIC,
  OUTPUT,
  CACHE,
  ARTIFACT,
}

data class BuildEventChunk(
  val sessionId: String,
  val commandId: String?,
  val eventType: String,
  val payload: BinaryPayloadRef,
)

data class ArtifactStreamRequest(
  val sessionId: String,
  val artifactId: String,
)

data class ArtifactUploadRequest(
  val sessionId: String,
  val artifactId: String,
  val content: BinaryPayloadRef,
)

data class ArtifactUploadResult(
  val artifactId: String,
  val stored: Boolean,
  val checksum: String? = null,
)

data class ModelQueryRequest(
  val sessionId: String,
  val modelKey: String,
  val selector: String? = null,
)

data class ModelQueryResponse(
  val modelKey: String,
  val found: Boolean,
  val payload: BinaryPayloadRef? = null,
)

data class CacheQueryRequest(
  val sessionId: String,
  val key: String,
)

data class CacheQueryResponse(
  val key: String,
  val hit: Boolean,
  val payload: BinaryPayloadRef? = null,
)

/**
 * Binary payload abstraction for high-throughput / low-copy transfer.
 */
sealed interface BinaryPayloadRef {
  val sizeBytes: Long

  data class HeapBytes(
    val bytes: ByteArray,
  ) : BinaryPayloadRef {
    override val sizeBytes: Long = bytes.size.toLong()
  }

  data class DirectBuffer(
    val buffer: ByteBuffer,
  ) : BinaryPayloadRef {
    override val sizeBytes: Long = buffer.remaining().toLong()
  }

  data class ChannelSource(
    val sizeHintBytes: Long,
    val openChannel: () -> ReadableByteChannel,
  ) : BinaryPayloadRef {
    override val sizeBytes: Long = sizeHintBytes
  }
}

data class BinaryChunk(
  val content: BinaryPayloadRef,
  val endOfStream: Boolean,
)

/** Utility for zero-copy capable relay between channels. */
object BinaryChannelRelay {
  fun relay(
    source: ReadableByteChannel,
    sink: WritableByteChannel,
    directBufferSize: Int = DEFAULT_DIRECT_BUFFER_SIZE,
  ): CompletableFuture<Long> {
    return CompletableFuture.supplyAsync {
      val buffer = ByteBuffer.allocateDirect(directBufferSize)
      var total = 0L
      while (source.read(buffer) >= 0) {
        buffer.flip()
        while (buffer.hasRemaining()) {
          total += sink.write(buffer).toLong()
        }
        buffer.compact()
      }
      buffer.flip()
      while (buffer.hasRemaining()) {
        total += sink.write(buffer).toLong()
      }
      total
    }
  }

  const val DEFAULT_DIRECT_BUFFER_SIZE: Int = 64 * 1024
}
