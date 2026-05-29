package com.zerostudio.tooling.buildgrpc

import com.google.protobuf.ByteString
import com.zerostudio.tooling.buildgrpc.proto.ArtifactKind
import com.zerostudio.tooling.buildgrpc.proto.CompressionKind
import com.zerostudio.tooling.buildgrpc.proto.DataChunk
import com.zerostudio.tooling.buildgrpc.proto.TransferRejectReason
import com.zerostudio.tooling.buildgrpc.proto.FetchDataRequest
import com.zerostudio.tooling.buildgrpc.proto.ExecuteActionRequest
import build.bazel.remote.execution.v2.Digest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class BuildSessionGrpcServiceTest {

  @Test
  fun `publishDataStream returns sequence violation reason`() = runBlocking {
    val service = BuildSessionGrpcService(module = NoopModule())
    val payload = "hello".encodeToByteArray()

    val ack = service.publishDataStream(
      flowOf(
        chunk(seq = 1, payload = payload),
        chunk(seq = 1, payload = payload),
      ),
    )

    assertFalse(ack.accepted)
    assertEquals(TransferRejectReason.TRANSFER_REJECT_REASON_SEQUENCE_VIOLATION, ack.rejectReason)
    assertEquals(2, ack.nextExpectedSequence)
  }

  @Test
  fun `publishDataStream returns checksum mismatch reason`() = runBlocking {
    val service = BuildSessionGrpcService(module = NoopModule())
    val ack = service.publishDataStream(
      flowOf(
        DataChunk.newBuilder()
          .setBuildId("build-1")
          .setTransferId("tx-1")
          .setSequence(1)
          .setPayload(ByteString.copyFromUtf8("bad"))
          .setChecksum(ByteString.copyFromUtf8("not-sha256"))
          .build(),
      ),
    )

    assertFalse(ack.accepted)
    assertEquals(TransferRejectReason.TRANSFER_REJECT_REASON_CHECKSUM_MISMATCH, ack.rejectReason)
    assertEquals(2, ack.nextExpectedSequence)
  }


  @Test
  fun `queryTransferCursor returns next expected sequence after upload`() = runBlocking {
    val service = BuildSessionGrpcService(module = NoopModule())
    val payload = "abc".encodeToByteArray()

    service.publishDataStream(
      flowOf(
        chunk(seq = 1, payload = payload),
        chunk(seq = 2, payload = payload),
      ),
    )

    val cursor = service.queryTransferCursor(
      com.zerostudio.tooling.buildgrpc.proto.QueryTransferCursorRequest.newBuilder()
        .setBuildId("build-1")
        .setTransferId("tx-1")
        .build(),
    )

    assertEquals(true, cursor.found)
    assertEquals(3, cursor.nextExpectedSequence)
    assertNotEquals(0, cursor.committedBytes)
  }


  @Test
  fun `cleanupTransfer removes persisted transfer and cursor`() = runBlocking {
    val service = BuildSessionGrpcService(module = NoopModule())
    val payload = "abc".encodeToByteArray()

    service.publishDataStream(flowOf(chunk(seq = 1, payload = payload)))

    val cleaned = service.cleanupTransfer(
      com.zerostudio.tooling.buildgrpc.proto.CleanupTransferRequest.newBuilder()
        .setBuildId("build-1")
        .setTransferId("tx-1")
        .build(),
    )
    val cursor = service.queryTransferCursor(
      com.zerostudio.tooling.buildgrpc.proto.QueryTransferCursorRequest.newBuilder()
        .setBuildId("build-1")
        .setTransferId("tx-1")
        .build(),
    )

    assertEquals(true, cleaned.removed)
    assertEquals(false, cursor.found)
    assertEquals(0, cursor.nextExpectedSequence)
  }

  @Test
  fun `data transfer event sequence is monotonic per build`() = runBlocking {
    val store = BuildEventStore()
    val service = BuildSessionGrpcService(module = NoopModule(), eventStore = store)
    val payload = "abc".encodeToByteArray()

    service.publishDataStream(flowOf(chunk(seq = 1, payload = payload)))
    service.publishDataStream(flowOf(chunk(seq = 2, payload = payload)))

    val events = service.streamBuildEvents(
      com.zerostudio.tooling.buildgrpc.proto.StreamBuildEventsRequest.newBuilder()
        .setBuildId("build-1")
        .setFromSequence(0)
        .build(),
    ).toList()

    assertEquals(2, events.size)
    assertEquals(1, events[0].sequence)
    assertEquals(2, events[1].sequence)
  }

  @Test
  fun `data transfer event infers artifact kind for jar-like payload`() = runBlocking {
    val service = BuildSessionGrpcService(module = NoopModule())
    val payload = "PK\u0003\u0004jar-content".encodeToByteArray()
    service.publishDataStream(
      flowOf(
        DataChunk.newBuilder()
          .setBuildId("build-jar")
          .setTransferId("libs/app.jar")
          .setSequence(1)
          .setCompression(CompressionKind.COMPRESSION_KIND_NONE)
          .setContentType("application/java-archive")
          .setPayload(ByteString.copyFrom(payload))
          .setChecksum(BuildDataStreamStore.checksumFor(payload))
          .build(),
      ),
    )

    val events = service.streamBuildEvents(
      com.zerostudio.tooling.buildgrpc.proto.StreamBuildEventsRequest.newBuilder()
        .setBuildId("build-jar")
        .setFromSequence(0)
        .build(),
    ).toList()

    assertEquals(1, events.size)
    assertEquals(ArtifactKind.ARTIFACT_KIND_JAR, events.first().transfer.artifactKind)
    assertEquals(CompressionKind.COMPRESSION_KIND_NONE, events.first().transfer.compression)
  }

  @Test
  fun `fetchDataStream sequence aligns with resume offset`() = runBlocking {
    val service = BuildSessionGrpcService(module = NoopModule())
    val payload = ByteArray(70 * 1024) { 'a'.code.toByte() }
    service.publishDataStream(flowOf(chunk(seq = 1, payload = payload)))

    val resumed = service.fetchDataStream(
      FetchDataRequest.newBuilder()
        .setBuildId("build-1")
        .setTransferId("tx-1")
        .setOffset(64 * 1024L)
        .setMaxBytes(8 * 1024L)
        .build(),
    ).toList()

    assertEquals(1, resumed.size)
    assertEquals(2, resumed.first().sequence)
    assertEquals(true, resumed.first().eof)
  }

  @Test
  fun `fetch transfer event chunk count reflects emitted chunks not absolute sequence`() = runBlocking {
    val service = BuildSessionGrpcService(module = NoopModule())
    val payload = ByteArray(70 * 1024) { 'b'.code.toByte() }
    service.publishDataStream(flowOf(chunk(seq = 1, payload = payload)))

    service.fetchDataStream(
      FetchDataRequest.newBuilder()
        .setBuildId("build-1")
        .setTransferId("tx-1")
        .setOffset(64 * 1024L)
        .setMaxBytes(8 * 1024L)
        .build(),
    ).toList()

    val events = service.streamBuildEvents(
      com.zerostudio.tooling.buildgrpc.proto.StreamBuildEventsRequest.newBuilder()
        .setBuildId("build-1")
        .setFromSequence(0)
        .build(),
    ).toList()
    val fetchEvent = events.last().transfer
    assertEquals(1, fetchEvent.chunkCount)
  }

  @Test
  fun `executeAction prefers reapi bridge when digest is provided`() = runBlocking {
    val service = BuildSessionGrpcService(module = NoopModule(), reapiExecutionBridge = BridgeOnlyReapi())
    val response = service.executeAction(
      ExecuteActionRequest.newBuilder()
        .setBuildId("build-1")
        .setReapiActionDigest(Digest.newBuilder().setHash("abc123").setSizeBytes(10).build())
        .setInstanceName("main")
        .setPriority(10)
        .build(),
    )
    assertEquals("reapi/abc123", response.operationName)
  }

  private fun chunk(seq: Long, payload: ByteArray): DataChunk = DataChunk.newBuilder()
    .setBuildId("build-1")
    .setTransferId("tx-1")
    .setSequence(seq)
    .setPayload(ByteString.copyFrom(payload))
    .setChecksum(BuildDataStreamStore.checksumFor(payload))
    .build()
}

private class BridgeOnlyReapi : ReapiExecutionBridge {
  override suspend fun execute(request: ReapiExecuteRequest): ActionExecutionResult? =
    ActionExecutionResult(
      operationName = "reapi/${request.actionDigest.hash}",
      status = com.zerostudio.tooling.buildgrpc.proto.ExecutionStatus.EXECUTION_STATUS_COMPLETED,
      actionResult = "ok".encodeToByteArray(),
    )
}

private class NoopModule : BuildGrpcModule {
  override suspend fun initialize(request: BuildInit): BuildServerInfo =
    BuildServerInfo("noop", "1", emptyList(), emptyList())

  override fun startBuild(request: BuildStart) = kotlinx.coroutines.flow.emptyFlow()

  override suspend fun shutdown(reason: String): Boolean = true
}
