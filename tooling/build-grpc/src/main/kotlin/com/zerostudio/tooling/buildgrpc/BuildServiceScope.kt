package com.zerostudio.tooling.buildgrpc

/**
 * Supported build systems for this binary protocol.
 *
 * This protocol is specialized for Gradle Tooling API and Bazel integrations,
 * while preserving extension points for future systems.
 */
enum class BuildSystem {
  GRADLE,
  BAZEL,
}

data class BuildSessionContext(
  val buildSystem: BuildSystem,
  /** Logical caller id (component/module/service name). */
  val callerId: String,
  val workspaceRoot: String,
  val featureFlags: Set<String> = emptySet(),
)

object BuildSessionContextResolver {
  private const val CAP_BUILDSYSTEM_GRADLE = "buildSystem:gradle"
  private const val CAP_BUILDSYSTEM_BAZEL = "buildSystem:bazel"
  private const val CAP_CALLER_PREFIX = "caller:"

  fun fromInit(request: BuildInit): BuildSessionContext {
    val caps = request.capabilities.toSet()
    val buildSystem = when {
      CAP_BUILDSYSTEM_BAZEL in caps -> BuildSystem.BAZEL
      else -> BuildSystem.GRADLE
    }
    val callerId = caps.firstOrNull { it.startsWith(CAP_CALLER_PREFIX) }
      ?.removePrefix(CAP_CALLER_PREFIX)
      ?.ifBlank { "unknown" }
      ?: "unknown"

    return BuildSessionContext(
      buildSystem = buildSystem,
      callerId = callerId,
      workspaceRoot = request.workspaceRoot,
      featureFlags = caps - CAP_BUILDSYSTEM_GRADLE - CAP_BUILDSYSTEM_BAZEL,
    )
  }
}
