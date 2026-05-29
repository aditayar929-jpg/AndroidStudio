package com.zerostudio.tooling.buildgrpc

import com.zerostudio.tooling.buildgrpc.proto.ContextFrame
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks latest context frame per (buildId, channel).
 */
class BuildContextStateStore {
  private val latest = ConcurrentHashMap<String, ContextFrame>()

  fun update(frame: ContextFrame) {
    if (frame.buildId.isBlank()) {
      return
    }
    latest[key(frame.buildId, frame.channel)] = frame
  }

  fun get(buildId: String, channel: String): ContextFrame? = latest[key(buildId, channel)]

  private fun key(buildId: String, channel: String): String = "$buildId::$channel"
}
