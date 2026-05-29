package com.zerostudio.tooling.buildgrpc

import com.zerostudio.tooling.buildgrpc.proto.DataChunk
import com.zerostudio.tooling.buildgrpc.proto.TransferRejectReason
import kotlin.test.Test
import kotlin.test.assertEquals
import java.nio.file.Files

class BuildTransferRegistryTest {
  @Test
  fun `accepts strictly increasing chunk sequence`() {
    val registry = BuildTransferRegistry()
    assertEquals(TransferRejectReason.TRANSFER_REJECT_REASON_NONE, registry.validateUploadChunk(chunk(1)).reason)
    assertEquals(TransferRejectReason.TRANSFER_REJECT_REASON_NONE, registry.validateUploadChunk(chunk(2)).reason)
    assertEquals(TransferRejectReason.TRANSFER_REJECT_REASON_NONE, registry.validateUploadChunk(chunk(3)).reason)
  }

  @Test
  fun `rejects duplicate or decreasing sequence`() {
    val registry = BuildTransferRegistry()
    assertEquals(TransferRejectReason.TRANSFER_REJECT_REASON_NONE, registry.validateUploadChunk(chunk(2)).reason)
    assertEquals(TransferRejectReason.TRANSFER_REJECT_REASON_SEQUENCE_VIOLATION, registry.validateUploadChunk(chunk(2)).reason)
    assertEquals(TransferRejectReason.TRANSFER_REJECT_REASON_SEQUENCE_VIOLATION, registry.validateUploadChunk(chunk(1)).reason)
  }

  @Test
  fun `rejects missing identity`() {
    val registry = BuildTransferRegistry()
    val missingBuild = DataChunk.newBuilder().setTransferId("t").setSequence(1).build()
    val missingTransfer = DataChunk.newBuilder().setBuildId("b").setSequence(1).build()
    assertEquals(TransferRejectReason.TRANSFER_REJECT_REASON_MISSING_IDENTITY, registry.validateUploadChunk(missingBuild).reason)
    assertEquals(TransferRejectReason.TRANSFER_REJECT_REASON_MISSING_IDENTITY, registry.validateUploadChunk(missingTransfer).reason)
  }

  @Test
  fun `reports next expected sequence for resume`() {
    val registry = BuildTransferRegistry()
    registry.validateUploadChunk(chunk(1))
    registry.validateUploadChunk(chunk(2))
    assertEquals(3, registry.nextExpectedSequence("build-1", "transfer-1"))
  }


  @Test
  fun `persists cursor state across registry instances`() {
    val dir = Files.createTempDirectory("registry-state")
    val stateFile = dir.resolve("transfer-registry.tsv")

    val writer = BuildTransferRegistry(stateFile = stateFile)
    writer.validateUploadChunk(chunk(1))
    writer.validateUploadChunk(chunk(2))

    val reader = BuildTransferRegistry(stateFile = stateFile)
    assertEquals(3, reader.nextExpectedSequence("build-1", "transfer-1"))
    assertEquals(true, reader.hasTransfer("build-1", "transfer-1"))
  }

  private fun chunk(seq: Long): DataChunk = DataChunk.newBuilder()
    .setBuildId("build-1")
    .setTransferId("transfer-1")
    .setSequence(seq)
    .build()
}
