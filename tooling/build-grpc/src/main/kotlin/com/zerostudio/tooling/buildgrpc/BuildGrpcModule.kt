package com.zerostudio.tooling.buildgrpc

import com.zerostudio.tooling.buildgrpc.proto.BuildEventEnvelope
import kotlinx.coroutines.flow.Flow

/**
 * JVM in-process build service contract:
 * - gRPC/proto carries high-throughput typed payloads.
 * - AIDL bridge supports Android process boundary IPC.
 * - Protocol shape follows BSP style request/response + BEP style event streaming.
 */
interface BuildGrpcModule {
  suspend fun initialize(request: BuildInit): BuildServerInfo

  fun startBuild(request: BuildStart): Flow<BuildEventEnvelope>

  suspend fun shutdown(reason: String): Boolean
}

data class BuildInit(
  val workspaceRoot: String,
  val clientName: String,
  val clientVersion: String,
  val capabilities: List<String> = emptyList(),
)

data class BuildServerInfo(
  val serverName: String,
  val serverVersion: String,
  val supportedLanguages: List<String>,
  val protocolFeatures: List<String>,
)

data class BuildStart(
  val buildId: String,
  val targets: List<String>,
  val options: Map<String, String> = emptyMap(),
)
