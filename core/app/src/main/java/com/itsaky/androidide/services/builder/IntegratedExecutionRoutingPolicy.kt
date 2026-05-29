package com.itsaky.androidide.services.builder

import com.itsaky.androidide.tooling.api.messages.result.InitializeResult
import com.itsaky.androidide.tooling.api.transport.ToolingTransportMode
import com.itsaky.androidide.tooling.impl.transport.IntegratedCapabilityPolicy

/**
 * Centralized client-side routing policy for deciding whether build requests should be executed
 * through Tooling transport execute(request) or through local gradlew shell invocation.
 */
class IntegratedExecutionRoutingPolicy {

  data class RoutingContext(
      val executeEnabled: Boolean,
      val transportMode: ToolingTransportMode,
      val initializeResult: InitializeResult?,
      val capabilitySnapshot: IntegratedCapabilityPolicy.CapabilitySnapshot,
      val tasks: List<String>,
  )

  data class RoutingDecision(
      val useToolingExecute: Boolean,
      val reason: String,
      val integratedMode: Boolean,
      val capabilityReady: Boolean,
  )

  fun decide(context: RoutingContext): RoutingDecision {
    if (!context.executeEnabled) {
      return RoutingDecision(
          useToolingExecute = false,
          reason = "tooling_execute_disabled",
          integratedMode = false,
          capabilityReady = false,
      )
    }

    val integratedMode = context.transportMode == ToolingTransportMode.INTEGRATED_AIDL_GRPC_REAPI
    val init = context.initializeResult
    val hasNegotiatedOps =
        (init?.negotiatedOperationTypes?.isNotEmpty() == true) ||
            context.capabilitySnapshot.negotiatedOperationTypes.isNotEmpty()

    // Integrated mode requires initialize negotiation to be available first, preventing blind
    // request dispatch before capability convergence.
    if (integratedMode && init == null) {
      return RoutingDecision(
          useToolingExecute = false,
          reason = "integrated_waiting_initialize_negotiation",
          integratedMode = true,
          capabilityReady = false,
      )
    }

    if (integratedMode && !hasNegotiatedOps) {
      return RoutingDecision(
          useToolingExecute = false,
          reason = "integrated_missing_negotiated_operation_types",
          integratedMode = true,
          capabilityReady = false,
      )
    }

    // If cleaning is requested, prefer local shell path as current tooling execute contract does
    // not fully model global daemon/process cleanup semantics yet.
    if (containsCleanupTask(context.tasks)) {
      return RoutingDecision(
          useToolingExecute = false,
          reason = "cleanup_task_prefers_shell_path",
          integratedMode = integratedMode,
          capabilityReady = hasNegotiatedOps,
      )
    }

    return RoutingDecision(
        useToolingExecute = true,
        reason = if (integratedMode) "integrated_capability_ready" else "legacy_execute_enabled",
        integratedMode = integratedMode,
        capabilityReady = hasNegotiatedOps,
    )
  }

  private fun containsCleanupTask(tasks: List<String>): Boolean {
    return tasks.any {
      val t = it.lowercase()
      t.contains("clean") || t.contains("stop")
    }
  }
}
