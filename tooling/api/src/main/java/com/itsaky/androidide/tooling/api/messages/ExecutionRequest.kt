/*
 *  This file is part of AndroidIDE.
 */

package com.itsaky.androidide.tooling.api.messages

import java.io.Serializable
import java.util.UUID

/**
 * Generic execution request for build invocations.
 */
data class ExecutionRequest(
    val requestId: String = UUID.randomUUID().toString(),
    val tasks: List<String> = emptyList(),
    val arguments: List<String> = emptyList(),
    val jvmArguments: List<String> = emptyList(),
    val operationTypes: Set<String> = emptySet(),
) : Serializable

