package com.itsaky.androidide.tooling.impl.transport.integrated

import java.util.concurrent.atomic.AtomicReference
import org.slf4j.LoggerFactory

/**
 * Thread-safe lifecycle holder for integrated transport runtime.
 *
 * This class centralizes transitions so UDS bootstrap, AIDL control bridge, and REAPI gateway can
 * share deterministic startup/shutdown behavior.
 */
class IntegratedTransportLifecycle {

  private val log = LoggerFactory.getLogger(IntegratedTransportLifecycle::class.java)
  private val stateRef = AtomicReference(IntegratedTransportLifecycleState.NEW)

  val state: IntegratedTransportLifecycleState
    get() = stateRef.get()

  fun markStarting(): Boolean = transition(setOf(IntegratedTransportLifecycleState.NEW), IntegratedTransportLifecycleState.STARTING)

  fun markRunning(): Boolean = transition(setOf(IntegratedTransportLifecycleState.STARTING), IntegratedTransportLifecycleState.RUNNING)

  fun markStopping(): Boolean =
      transition(
          setOf(IntegratedTransportLifecycleState.STARTING, IntegratedTransportLifecycleState.RUNNING),
          IntegratedTransportLifecycleState.STOPPING,
      )

  fun markStopped(): Boolean =
      transition(setOf(IntegratedTransportLifecycleState.STOPPING), IntegratedTransportLifecycleState.STOPPED)

  fun markFailed(): Boolean =
      transition(
          setOf(
              IntegratedTransportLifecycleState.NEW,
              IntegratedTransportLifecycleState.STARTING,
              IntegratedTransportLifecycleState.RUNNING,
              IntegratedTransportLifecycleState.STOPPING,
          ),
          IntegratedTransportLifecycleState.FAILED,
      )

  private fun transition(
      allowedFrom: Set<IntegratedTransportLifecycleState>,
      target: IntegratedTransportLifecycleState,
  ): Boolean {
    while (true) {
      val current = stateRef.get()
      if (current !in allowedFrom) {
        log.debug("Ignored lifecycle transition {} -> {} (allowedFrom={})", current, target, allowedFrom)
        return false
      }
      if (stateRef.compareAndSet(current, target)) {
        log.info("Integrated transport lifecycle transition {} -> {}", current, target)
        return true
      }
    }
  }
}
