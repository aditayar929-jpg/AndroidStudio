package com.zerostudio.tooling.buildgrpc

import com.zerostudio.tooling.buildgrpc.proto.CompressionKind
import com.zerostudio.tooling.buildgrpc.proto.InitializeRequest
import com.zerostudio.tooling.buildgrpc.proto.SerializationKind

data class RuntimeTransportPolicy(
  val maxFrameBytes: Int,
  val compression: CompressionKind,
  val serialization: SerializationKind,
)

object BuildTransportPolicy {
  private const val DEFAULT_FRAME_BYTES = 64 * 1024
  private const val MAX_FRAME_BYTES = 4 * 1024 * 1024

  fun negotiate(request: InitializeRequest): RuntimeTransportPolicy {
    val frame = request.transport.maxFrameBytes
      .toInt().takeIf { it > 0 }?.coerceAtMost(MAX_FRAME_BYTES)
      ?: DEFAULT_FRAME_BYTES
    val compression = request.transport.supportedCompressionList
      .firstOrNull { it == CompressionKind.COMPRESSION_KIND_NONE || it == CompressionKind.COMPRESSION_KIND_GZIP }
      ?: CompressionKind.COMPRESSION_KIND_NONE
    val serialization = request.serialization.preferredList
      .firstOrNull {
        it == SerializationKind.SERIALIZATION_KIND_PROTOBUF_BINARY ||
          it == SerializationKind.SERIALIZATION_KIND_UNSPECIFIED
      } ?: SerializationKind.SERIALIZATION_KIND_PROTOBUF_BINARY
    return RuntimeTransportPolicy(frame, compression, serialization)
  }
}

