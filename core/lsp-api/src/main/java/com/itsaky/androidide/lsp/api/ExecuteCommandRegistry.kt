/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.lsp.api

import com.itsaky.androidide.lsp.models.ExecuteCommandOptions
import com.itsaky.androidide.lsp.models.ExecuteCommandParams
import com.itsaky.androidide.lsp.models.WorkspaceEdit
import java.util.concurrent.ConcurrentHashMap

/** Handles one server-side workspace/executeCommand command. */
fun interface ExecuteCommandHandler {
  suspend fun execute(params: ExecuteCommandParams): Any?
}

/**
 * Small server-side command dispatcher for the core LSP API.
 *
 * Language servers can keep an instance of this registry, register every command they advertise in
 * [ExecuteCommandOptions.commands], and delegate [ILanguageServer.executeCommand] to [execute].
 * The registry validates command ids, returns the handler result to the client, and can apply a
 * returned [WorkspaceEdit] through the connected [ILanguageClient] to match the usual LSP command
 * flow.
 */
class ExecuteCommandRegistry {

  private val handlers = ConcurrentHashMap<String, ExecuteCommandHandler>()

  val commands: List<String>
    get() = handlers.keys.toList().sorted()

  fun options(workDoneProgress: Boolean = false): ExecuteCommandOptions {
    return ExecuteCommandOptions(commands = commands, workDoneProgress = workDoneProgress)
  }

  fun register(command: String, handler: ExecuteCommandHandler): ExecuteCommandRegistry {
    require(command.isNotBlank()) { "Command id must not be blank." }
    handlers[command] = handler
    return this
  }

  fun unregister(command: String): ExecuteCommandRegistry {
    handlers.remove(command)
    return this
  }

  fun canExecute(command: String): Boolean {
    return handlers.containsKey(command)
  }

  suspend fun execute(params: ExecuteCommandParams, client: ILanguageClient? = null): Any? {
    val command = params.command
    require(command.isNotBlank()) { "Command id must not be blank." }

    val handler = handlers[command] ?: throw UnsupportedOperationException(
        "Unsupported workspace/executeCommand command: $command"
    )
    return applyWorkspaceEditResult(handler.execute(params), client)
  }

  private fun applyWorkspaceEditResult(result: Any?, client: ILanguageClient?): Any? {
    if (result is WorkspaceEdit) {
      client?.applyWorkspaceEdit(result)
    }
    return result
  }
}
