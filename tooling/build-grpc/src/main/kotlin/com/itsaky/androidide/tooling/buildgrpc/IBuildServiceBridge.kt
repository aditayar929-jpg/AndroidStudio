package com.itsaky.androidide.tooling.buildgrpc

/**
 * JVM fallback mirror of the AIDL-generated bridge interface for non-Android builds.
 */
interface IBuildServiceBridge {
  fun initialize(initializeBuildRequestPayload: ByteArray): ByteArray
  fun queryTargets(queryBuildTargetsRequestPayload: ByteArray): ByteArray
  fun executeBuild(executeBuildRequestPayload: ByteArray): ByteArray
  fun getBuildResult(executeBuildRequestPayload: ByteArray): ByteArray

  abstract class Stub : IBuildServiceBridge
}
