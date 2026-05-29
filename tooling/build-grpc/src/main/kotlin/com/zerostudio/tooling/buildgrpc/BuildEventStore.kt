package com.zerostudio.tooling.buildgrpc

import com.zerostudio.tooling.buildgrpc.proto.BuildEventEnvelope
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe BEP event store keyed by buildId.
 */
class BuildEventStore {
  private val eventsByBuildId = ConcurrentHashMap<String, MutableList<BuildEventEnvelope>>()

  fun append(event: BuildEventEnvelope) {
    val bucket = eventsByBuildId.computeIfAbsent(event.buildId) { mutableListOf() }
    synchronized(bucket) {
      bucket.add(event)
    }
  }

  fun getFromSequence(buildId: String, fromSequenceExclusive: Long): List<BuildEventEnvelope> {
    val bucket = eventsByBuildId[buildId] ?: return emptyList()
    synchronized(bucket) {
      return bucket.filter { it.sequence > fromSequenceExclusive }
    }
  }

  fun clear(buildId: String) {
    eventsByBuildId.remove(buildId)
  }

  fun clearAll() {
    eventsByBuildId.clear()
  }
}
