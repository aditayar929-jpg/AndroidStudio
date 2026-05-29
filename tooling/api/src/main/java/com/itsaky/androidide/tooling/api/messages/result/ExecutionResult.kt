/*
 *  This file is part of AndroidIDE.
 */

package com.itsaky.androidide.tooling.api.messages.result

import java.io.Serializable

/**
 * Unified execution result for build requests.
 */
data class ExecutionResult(
    val requestId: String? = null,
    val isSuccessful: Boolean,
    val failure: TaskExecutionResult.Failure? = null,
    val diagnostics: String? = null,
) : Serializable {

  companion object {
    @JvmStatic val SUCCESS = ExecutionResult(isSuccessful = true)
  }
}

