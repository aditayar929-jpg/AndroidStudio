package com.itsaky.androidide.tooling.buildgrpc.model

data class BuildBridgeRequest(
  val requestId: String,
  val workspaceRoot: String,
  val targetIds: List<String> = emptyList(),
  val arguments: List<String> = emptyList(),
)

data class BuildBridgeEvent(
  val requestId: String,
  val eventId: String,
  val category: String,
  val payload: ByteArray,
)
