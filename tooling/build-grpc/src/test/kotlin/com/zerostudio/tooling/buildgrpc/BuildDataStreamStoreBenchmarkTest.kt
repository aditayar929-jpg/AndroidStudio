package com.zerostudio.tooling.buildgrpc

import com.google.protobuf.ByteString
import com.zerostudio.tooling.buildgrpc.proto.DataChunk
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertTrue

class BuildDataStreamStoreBenchmarkTest {
  @Test
  fun `benchmark write and range-read throughput smoke`() {
    val store = BuildDataStreamStore()
    val payload = ByteArray(8 * 1024 * 1024) { (it % 255).toByte() }
    val chunk = DataChunk.newBuilder()
      .setBuildId("bench")
      .setTransferId("bench.bin")
      .setSequence(1)
      .setPayload(ByteString.copyFrom(payload))
      .setChecksum(BuildDataStreamStore.checksumFor(payload))
      .build()

    val writeMs = measureTimeMillis { store.append(chunk) }.coerceAtLeast(1)
    val readMs = measureTimeMillis { store.read("bench.bin", 4L * 1024 * 1024, 2L * 1024 * 1024) }.coerceAtLeast(1)
    val writeMbps = (payload.size / 1024.0 / 1024.0) / (writeMs / 1000.0)
    val readMbps = (2.0) / (readMs / 1000.0)
    assertTrue(writeMbps > 0)
    assertTrue(readMbps > 0)
  }
}

