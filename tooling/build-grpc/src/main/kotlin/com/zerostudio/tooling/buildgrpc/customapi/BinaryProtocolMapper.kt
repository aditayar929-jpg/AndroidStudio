package com.zerostudio.tooling.buildgrpc.customapi

import com.google.protobuf.ByteString
import com.zerostudio.tooling.buildgrpc.proto.BuildCommandMessage
import com.zerostudio.tooling.buildgrpc.proto.BuildCommandType
import com.zerostudio.tooling.buildgrpc.proto.BuildEventMessage
import com.zerostudio.tooling.buildgrpc.proto.BuildEventTopic
import com.zerostudio.tooling.buildgrpc.proto.CacheQueryMessage
import com.zerostudio.tooling.buildgrpc.proto.EventSubscriptionMessage
import com.zerostudio.tooling.buildgrpc.proto.ModelQueryMessage
import com.zerostudio.tooling.buildgrpc.proto.OpenSessionMessage
import com.zerostudio.tooling.buildgrpc.proto.ProtocolCapabilityMessage
import com.zerostudio.tooling.buildgrpc.proto.ProtocolHandshakeMessage

/**
 * Mapper between in-process binary protocol contracts and protobuf wire messages.
 */
object BinaryProtocolMapper {

  fun toProto(request: ProtocolHandshakeRequest): ProtocolHandshakeMessage =
    ProtocolHandshakeMessage.newBuilder()
      .setClientName(request.clientName)
      .setClientVersion(request.clientVersion)
      .addAllSupportedProtocolVersions(request.supportedProtocolVersions)
      .addAllCapabilities(request.supportedCapabilities.map { it.toProto() })
      .build()

  fun toProto(request: OpenSessionRequest): OpenSessionMessage =
    OpenSessionMessage.newBuilder()
      .setWorkspaceRoot(request.workspaceRoot)
      .setBuildSystemId(request.buildSystemId)
      .putAllMetadata(request.metadata)
      .build()

  fun toProto(request: BuildCommandEnvelope): BuildCommandMessage =
    BuildCommandMessage.newBuilder()
      .setSessionId(request.sessionId)
      .setCommandId(request.commandId)
      .setType(request.kind.toProto())
      .setTimeoutMs(request.timeoutMs ?: 0L)
      .setPayload(toByteString(request.payload))
      .build()

  fun toProto(request: EventSubscriptionRequest): EventSubscriptionMessage =
    EventSubscriptionMessage.newBuilder()
      .setSessionId(request.sessionId)
      .addAllTopics(request.channels.map { it.toProto() })
      .build()

  fun toProto(request: ModelQueryRequest): ModelQueryMessage =
    ModelQueryMessage.newBuilder()
      .setSessionId(request.sessionId)
      .setModelKey(request.modelKey)
      .setSelector(request.selector.orEmpty())
      .build()

  fun toProto(request: CacheQueryRequest): CacheQueryMessage =
    CacheQueryMessage.newBuilder()
      .setSessionId(request.sessionId)
      .setKey(request.key)
      .build()

  fun fromProto(event: BuildEventMessage): BuildEventChunk =
    BuildEventChunk(
      sessionId = event.sessionId,
      commandId = event.commandId.ifBlank { null },
      eventType = event.eventType,
      payload = BinaryPayloadRef.HeapBytes(event.payload.toByteArray()),
    )

  private fun BuildCommandKind.toProto(): BuildCommandType = when (this) {
    BuildCommandKind.EXECUTE_TASKS -> BuildCommandType.BUILD_COMMAND_TYPE_EXECUTE_TASKS
    BuildCommandKind.EXECUTE_ACTION -> BuildCommandType.BUILD_COMMAND_TYPE_EXECUTE_ACTION
    BuildCommandKind.CANCEL_TASK -> BuildCommandType.BUILD_COMMAND_TYPE_CANCEL_TASK
    BuildCommandKind.SYNC_MODEL -> BuildCommandType.BUILD_COMMAND_TYPE_SYNC_MODEL
    BuildCommandKind.REFRESH_DEPENDENCIES -> BuildCommandType.BUILD_COMMAND_TYPE_REFRESH_DEPENDENCIES
  }

  private fun EventChannel.toProto(): BuildEventTopic = when (this) {
    EventChannel.LIFECYCLE -> BuildEventTopic.BUILD_EVENT_TOPIC_LIFECYCLE
    EventChannel.TASK -> BuildEventTopic.BUILD_EVENT_TOPIC_TASK
    EventChannel.DIAGNOSTIC -> BuildEventTopic.BUILD_EVENT_TOPIC_DIAGNOSTIC
    EventChannel.OUTPUT -> BuildEventTopic.BUILD_EVENT_TOPIC_OUTPUT
    EventChannel.CACHE -> BuildEventTopic.BUILD_EVENT_TOPIC_CACHE
    EventChannel.ARTIFACT -> BuildEventTopic.BUILD_EVENT_TOPIC_ARTIFACT
  }

  private fun ProtocolCapability.toProto(): ProtocolCapabilityMessage = when (this) {
    ProtocolCapability.ZERO_COPY_BINARY_STREAM -> ProtocolCapabilityMessage.PROTOCOL_CAPABILITY_ZERO_COPY_BINARY_STREAM
    ProtocolCapability.ASYNC_COMMAND_PIPELINE -> ProtocolCapabilityMessage.PROTOCOL_CAPABILITY_ASYNC_COMMAND_PIPELINE
    ProtocolCapability.EVENT_SUBSCRIPTION -> ProtocolCapabilityMessage.PROTOCOL_CAPABILITY_EVENT_SUBSCRIPTION
    ProtocolCapability.MODEL_QUERY -> ProtocolCapabilityMessage.PROTOCOL_CAPABILITY_MODEL_QUERY
    ProtocolCapability.BUILD_CACHE_QUERY -> ProtocolCapabilityMessage.PROTOCOL_CAPABILITY_BUILD_CACHE_QUERY
    ProtocolCapability.INCREMENTAL_BUILD -> ProtocolCapabilityMessage.PROTOCOL_CAPABILITY_INCREMENTAL_BUILD
    ProtocolCapability.RESUMABLE_TRANSFER -> ProtocolCapabilityMessage.PROTOCOL_CAPABILITY_RESUMABLE_TRANSFER
  }

  private fun toByteString(payload: BinaryPayloadRef): ByteString = when (payload) {
    is BinaryPayloadRef.HeapBytes -> ByteString.copyFrom(payload.bytes)
    is BinaryPayloadRef.DirectBuffer -> ByteString.copyFrom(payload.buffer.asReadOnlyBuffer())
    is BinaryPayloadRef.ChannelSource -> payload.openChannel().use { channel ->
      val output = ByteString.newOutput(if (payload.sizeHintBytes > 0) payload.sizeHintBytes.toInt() else 8192)
      val buffer = java.nio.ByteBuffer.allocateDirect(64 * 1024)
      while (channel.read(buffer) >= 0) {
        buffer.flip()
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        output.write(bytes)
        buffer.clear()
      }
      output.toByteString()
    }
  }
}
