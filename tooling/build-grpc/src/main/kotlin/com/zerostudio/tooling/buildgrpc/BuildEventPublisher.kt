package com.zerostudio.tooling.buildgrpc

import com.zerostudio.tooling.buildgrpc.proto.BuildDiagnosticEvent
import com.zerostudio.tooling.buildgrpc.proto.BuildEventEnvelope
import com.zerostudio.tooling.buildgrpc.proto.BuildEventKind
import com.zerostudio.tooling.buildgrpc.proto.BuildFinishedEvent
import com.zerostudio.tooling.buildgrpc.proto.BuildProgressEvent
import com.zerostudio.tooling.buildgrpc.proto.BuildTargetEvent
import com.zerostudio.tooling.buildgrpc.proto.BuildTargetStatus
import com.zerostudio.tooling.buildgrpc.proto.DiagnosticSeverity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.concurrent.atomic.AtomicLong

class BuildEventPublisher(
  private val buildId: String,
  private val clockMillis: () -> Long = { System.currentTimeMillis() },
) {
  private val seq = AtomicLong(0)
  private val eventBus = MutableSharedFlow<BuildEventEnvelope>(extraBufferCapacity = 256)

  val events: SharedFlow<BuildEventEnvelope> = eventBus

  fun progress(message: String, percent: Float) {
    emit(
      kind = BuildEventKind.BUILD_EVENT_KIND_PROGRESS,
      envelope = BuildEventEnvelope.newBuilder().setProgress(
        BuildProgressEvent.newBuilder().setMessage(message).setPercent(percent).build(),
      ),
    )
  }

  fun diagnostic(origin: String, message: String, severity: DiagnosticSeverity) {
    emit(
      kind = BuildEventKind.BUILD_EVENT_KIND_DIAGNOSTIC,
      envelope = BuildEventEnvelope.newBuilder().setDiagnostic(
        BuildDiagnosticEvent.newBuilder()
          .setOrigin(origin)
          .setMessage(message)
          .setSeverity(severity)
          .build(),
      ),
    )
  }

  fun target(label: String, status: BuildTargetStatus) {
    emit(
      kind = BuildEventKind.BUILD_EVENT_KIND_TARGET,
      envelope = BuildEventEnvelope.newBuilder().setTarget(
        BuildTargetEvent.newBuilder().setLabel(label).setStatus(status).build(),
      ),
    )
  }

  fun finished(success: Boolean, durationMillis: Long, summary: String) {
    emit(
      kind = BuildEventKind.BUILD_EVENT_KIND_FINISHED,
      envelope = BuildEventEnvelope.newBuilder().setFinished(
        BuildFinishedEvent.newBuilder().setSuccess(success).setDurationMillis(durationMillis).setSummary(summary).build(),
      ),
    )
  }

  private fun emit(kind: BuildEventKind, envelope: BuildEventEnvelope.Builder) {
    val event = envelope
      .setBuildId(buildId)
      .setSequence(seq.incrementAndGet())
      .setTimestampMillis(clockMillis())
      .setKind(kind)
      .build()
    eventBus.tryEmit(event)
  }
}
