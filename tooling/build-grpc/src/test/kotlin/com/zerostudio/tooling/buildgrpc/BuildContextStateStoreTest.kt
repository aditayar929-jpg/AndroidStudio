package com.zerostudio.tooling.buildgrpc

import com.zerostudio.tooling.buildgrpc.proto.ContextFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BuildContextStateStoreTest {
  @Test
  fun `update and get latest frame by build and channel`() {
    val store = BuildContextStateStore()
    val frame = ContextFrame.newBuilder()
      .setBuildId("build-1")
      .setChannel("env")
      .setSequence(3)
      .build()

    store.update(frame)

    val saved = store.get("build-1", "env")
    assertNotNull(saved)
    assertEquals(3, saved.sequence)
  }

  @Test
  fun `blank build id is ignored`() {
    val store = BuildContextStateStore()
    val frame = ContextFrame.newBuilder()
      .setBuildId("")
      .setChannel("env")
      .setSequence(1)
      .build()

    store.update(frame)

    assertNull(store.get("", "env"))
  }
}
