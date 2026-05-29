package com.zerostudio.tooling.buildgrpc

import com.zerostudio.tooling.buildgrpc.proto.DataChunk
import com.zerostudio.tooling.buildgrpc.proto.TransferRejectReason
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks transfer-level identity and sequencing constraints.
 */
class BuildTransferRegistry(
  private val stateFile: Path? = null,
) {
  private val uploads = ConcurrentHashMap<String, UploadState>()

  init {
    loadState()
  }

  fun validateUploadChunk(chunk: DataChunk): ValidationResult {
    if (chunk.buildId.isBlank() || chunk.transferId.isBlank()) {
      return ValidationResult(
        reason = TransferRejectReason.TRANSFER_REJECT_REASON_MISSING_IDENTITY,
        nextExpectedSequence = 0,
      )
    }

    val key = key(chunk.buildId, chunk.transferId)
    val state = uploads.computeIfAbsent(key) {
      UploadState(buildId = chunk.buildId, transferId = chunk.transferId, lastSequence = -1L)
    }

    if (state.buildId != chunk.buildId || state.transferId != chunk.transferId) {
      return ValidationResult(
        reason = TransferRejectReason.TRANSFER_REJECT_REASON_MISSING_IDENTITY,
        nextExpectedSequence = state.lastSequence + 1,
      )
    }

    if (chunk.sequence < 0 || chunk.sequence <= state.lastSequence) {
      return ValidationResult(
        reason = TransferRejectReason.TRANSFER_REJECT_REASON_SEQUENCE_VIOLATION,
        nextExpectedSequence = state.lastSequence + 1,
      )
    }

    state.lastSequence = chunk.sequence
    persistState()
    return ValidationResult(
      reason = TransferRejectReason.TRANSFER_REJECT_REASON_NONE,
      nextExpectedSequence = state.lastSequence + 1,
    )
  }

  fun nextExpectedSequence(buildId: String, transferId: String): Long {
    val state = uploads[key(buildId, transferId)] ?: return 0
    return state.lastSequence + 1
  }

  fun hasTransfer(buildId: String, transferId: String): Boolean =
    uploads.containsKey(key(buildId, transferId))

  fun removeTransfer(buildId: String, transferId: String): Boolean {
    val removed = uploads.remove(key(buildId, transferId)) != null
    if (removed) persistState()
    return removed
  }

  private fun key(buildId: String, transferId: String): String = "$buildId::$transferId"

  private fun persistState() {
    val path = stateFile ?: return
    Files.createDirectories(path.parent)
    val content = buildString {
      uploads.values.forEach { state ->
        append(state.buildId)
        append('\t')
        append(state.transferId)
        append('\t')
        append(state.lastSequence)
        append('\n')
      }
    }
    Files.writeString(
      path,
      content,
      StandardOpenOption.CREATE,
      StandardOpenOption.TRUNCATE_EXISTING,
      StandardOpenOption.WRITE,
    )
  }

  private fun loadState() {
    val path = stateFile ?: return
    if (!Files.exists(path)) return
    Files.readAllLines(path).forEach { line ->
      val parts = line.split('\t')
      if (parts.size == 3) {
        val buildId = parts[0]
        val transferId = parts[1]
        val lastSequence = parts[2].toLongOrNull() ?: return@forEach
        uploads[key(buildId, transferId)] = UploadState(buildId, transferId, lastSequence)
      }
    }
  }

  private data class UploadState(
    val buildId: String,
    val transferId: String,
    @Volatile var lastSequence: Long,
  )

  data class ValidationResult(
    val reason: TransferRejectReason,
    val nextExpectedSequence: Long,
  )
}
