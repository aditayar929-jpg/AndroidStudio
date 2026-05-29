package com.itsaky.androidide.tooling.buildgrpc.service

/**
 * Observable runtime status for build service lifecycle.
 */
enum class BuildServiceRuntimeState {
  STOPPED,
  STARTING,
  RUNNING,
  STOPPING,
}
