package com.itsaky.androidide.tooling.impl.transport

import com.itsaky.androidide.tooling.api.messages.ExecutionRequest
import com.itsaky.androidide.tooling.api.messages.result.InitializeResult

/**
 * Session capability policy for integrated transport stack.
 *
 * Keeps a normalized snapshot of initialize negotiation and provides deterministic filtering for
 * outgoing execution requests.
 */
class IntegratedCapabilityPolicy {

  data class CapabilitySnapshot(
      val supportsPhasedAction: Boolean,
      val supportsModelSnapshot: Boolean,
      val supportsQueryService: Boolean,
      val negotiatedOperationTypes: Set<String>,
  ) {
    companion object {
      val DEFAULT = CapabilitySnapshot(false, false, false, emptySet())

      fun fromInitialize(result: InitializeResult): CapabilitySnapshot {
        return CapabilitySnapshot(
            supportsPhasedAction = result.supportsPhasedAction,
            supportsModelSnapshot = result.supportsModelSnapshot,
            supportsQueryService = result.supportsQueryService,
            negotiatedOperationTypes = result.negotiatedOperationTypes,
        )
      }
    }
  }

  @Volatile private var snapshot: CapabilitySnapshot = CapabilitySnapshot.DEFAULT

  fun reset() {
    snapshot = CapabilitySnapshot.DEFAULT
  }

  fun updateFromInitialize(result: InitializeResult) {
    snapshot = CapabilitySnapshot.fromInitialize(result)
  }

  fun current(): CapabilitySnapshot = snapshot

  fun applyToExecutionRequest(
      request: ExecutionRequest,
      defaultOps: Set<String>,
  ): ExecutionRequest {
    val cap = snapshot
    val effectiveOps =
        when {
          request.operationTypes.isNotEmpty() -> request.operationTypes
          cap.negotiatedOperationTypes.isNotEmpty() -> cap.negotiatedOperationTypes
          else -> defaultOps
        }

    return request.copy(
        operationTypes = effectiveOps,
        // phased action is not directly a field in request today; retain args/tasks unchanged.
        tasks = request.tasks.filter { it.isNotBlank() },
        arguments = request.arguments.filter { it.isNotBlank() },
        jvmArguments = request.jvmArguments.filter { it.isNotBlank() },
    )
  }
}
