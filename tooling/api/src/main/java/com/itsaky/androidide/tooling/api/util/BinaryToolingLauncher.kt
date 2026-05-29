package com.itsaky.androidide.tooling.api.util

import kotlinx.coroutines.flow.Flow

/**
 * Binary launcher abstraction that keeps tooling-api module free from direct
 * build-grpc module dependency to avoid circular project dependencies.
 */
class BinaryToolingLauncher(
  private val bridge: BinaryBridge,
) {
  fun initialize(requestBytes: ByteArray): ByteArray = bridge.initialize(requestBytes)

  fun startBuild(requestBytes: ByteArray): Flow<ByteArray> = bridge.startBuild(requestBytes)

  interface BinaryBridge {
    fun initialize(requestBytes: ByteArray): ByteArray
    fun startBuild(requestBytes: ByteArray): Flow<ByteArray>
  }
}
