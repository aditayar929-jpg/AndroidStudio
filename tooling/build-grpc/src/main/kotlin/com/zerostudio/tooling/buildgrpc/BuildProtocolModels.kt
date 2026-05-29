package com.zerostudio.tooling.buildgrpc

/**
 * 协议层领域模型：对齐 BSP 初始化/能力协商 + BEP 事件流语义。
 */
data class BuildClientMetadata(
  val clientName: String,
  val clientVersion: String,
  val capabilities: Set<String> = emptySet(),
)

data class BuildServerMetadata(
  val serverName: String,
  val serverVersion: String,
  val supportedLanguages: Set<String>,
  val protocolFeatures: Set<String>,
)

enum class BuildLifecycleState {
  CREATED,
  INITIALIZED,
  RUNNING,
  FINISHED,
  FAILED,
  CANCELLED,
}

data class BuildTargetDescriptor(
  val label: String,
  val language: String? = null,
)

data class BuildExecutionPlan(
  val buildId: String,
  val targets: List<BuildTargetDescriptor>,
  val options: Map<String, String> = emptyMap(),
)

enum class SerializationCodec {
  CODEC_UNSPECIFIED,
  PROTOBUF,
  FLATBUFFERS,
  CAPN_PROTO,
}

enum class TransferCompression {
  COMPRESSION_UNSPECIFIED,
  NONE,
  ZSTD,
  LZ4,
  GZIP,
}

data class ProtocolContext(
  val workspaceRoot: String,
  val buildSystemId: String,
  val buildSystemVersion: String,
  val executionEnvironment: Map<String, String> = emptyMap(),
  val requestedCapabilities: Set<String> = emptySet(),
)

data class ResourceTransferDescriptor(
  val transferId: String,
  val resourceUri: String,
  val totalBytes: Long,
  val digest: String,
  val codec: SerializationCodec,
  val compression: TransferCompression,
  val chunkSizeBytes: Int,
)

data class ResourceChunk(
  val transferId: String,
  val sequence: Long,
  val payload: ByteArray,
  val eof: Boolean,
)
