package com.zerostudio.tooling.buildgrpc

import com.google.protobuf.ByteString
import com.zerostudio.tooling.buildgrpc.proto.CompressionKind
import com.zerostudio.tooling.buildgrpc.proto.DataChunk
import com.zerostudio.tooling.buildgrpc.proto.TransferRejectReason
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream

/**
 * Transfer store used by BuildSessionGrpcService.
 *
 * Phase-B baseline now supports file-backed persistence for large text/binary source payload transport.
 */
class BuildDataStreamStore(
  private val baseDir: Path = Files.createTempDirectory("build-grpc-transfer-store"),
) {
  private val transferFiles = ConcurrentHashMap<String, Path>()

  init {
    Files.createDirectories(baseDir)
  }

  fun append(chunk: DataChunk): AppendResult {
    if (chunk.transferId.isBlank()) {
      return AppendResult(0, TransferRejectReason.TRANSFER_REJECT_REASON_MISSING_IDENTITY)
    }

    val payload = decompressIfRequired(chunk)
      ?: return AppendResult(0, TransferRejectReason.TRANSFER_REJECT_REASON_DECOMPRESSION_FAILED)

    if (chunk.checksum.size() > 0 && !verifySha256(payload, chunk.checksum.toByteArray())) {
      return AppendResult(0, TransferRejectReason.TRANSFER_REJECT_REASON_CHECKSUM_MISMATCH)
    }

    val file = transferFiles.computeIfAbsent(chunk.transferId) { transferIdPath(chunk.transferId) }
    Files.createDirectories(file.parent)
    Files.write(file, payload, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)
    return AppendResult(payload.size, TransferRejectReason.TRANSFER_REJECT_REASON_NONE)
  }

  fun remove(transferId: String): Boolean {
    val file = transferFiles.remove(transferId) ?: transferIdPath(transferId)
    return Files.deleteIfExists(file)
  }

  fun read(transferId: String, offset: Long, maxBytes: Long): ByteArray {
    val file = transferFiles[transferId] ?: transferIdPath(transferId)
    if (!Files.exists(file)) return ByteArray(0)
    val size = Files.size(file)
    if (size <= 0L) return ByteArray(0)
    val from = offset.coerceAtLeast(0).coerceAtMost(size)
    val desired = if (maxBytes <= 0) size - from else maxBytes.coerceAtMost(size - from)
    if (desired <= 0) return ByteArray(0)
    val out = ByteArray(desired.toInt())
    Files.newByteChannel(file, StandardOpenOption.READ).use { channel ->
      channel.position(from)
      val buffer = ByteBuffer.wrap(out)
      while (buffer.hasRemaining()) {
        val read = channel.read(buffer)
        if (read <= 0) break
      }
    }
    return out
  }

  fun sizeOf(transferId: String): Long {
    val file = transferFiles[transferId] ?: transferIdPath(transferId)
    if (!Files.exists(file)) return 0
    return Files.size(file)
  }

  private fun transferIdPath(transferId: String): Path {
    val safe = transferId.replace(Regex("[^A-Za-z0-9._-]"), "_")
    return baseDir.resolve("$safe.bin")
  }

  private fun verifySha256(payload: ByteArray, expected: ByteArray): Boolean {
    val digest = MessageDigest.getInstance("SHA-256").digest(payload)
    return digest.contentEquals(expected)
  }

  private fun decompressIfRequired(chunk: DataChunk): ByteArray? {
    val payload = chunk.payload.toByteArray()
    return when (chunk.compression) {
      CompressionKind.COMPRESSION_KIND_UNSPECIFIED,
      CompressionKind.COMPRESSION_KIND_NONE,
      CompressionKind.UNRECOGNIZED,
      CompressionKind.COMPRESSION_KIND_ZSTD,
      CompressionKind.COMPRESSION_KIND_LZ4,
      -> payload
      CompressionKind.COMPRESSION_KIND_GZIP -> decompressGzip(payload)
    }
  }

  private fun decompressGzip(payload: ByteArray): ByteArray? = try {
    GZIPInputStream(ByteArrayInputStream(payload)).use { it.readBytes() }
  } catch (_: Throwable) {
    null
  }

  data class AppendResult(val acceptedBytes: Int, val reason: TransferRejectReason)

  companion object {
    fun checksumFor(payload: ByteArray): ByteString =
      ByteString.copyFrom(MessageDigest.getInstance("SHA-256").digest(payload))
  }
}
