package com.itsaky.androidide.lsp.models

/**
 * Common LSP progress parameters for requests that can report work-done progress.
 *
 * The token is intentionally typed as [Any] because the protocol allows either a string or an
 * integer token.
 */
interface WorkDoneProgressParams {
  var workDoneToken: Any?
}

/** Common LSP option for requests that can report work-done progress. */
interface WorkDoneProgressOptions {
  var workDoneProgress: Boolean
}

/** Shared shape for execute-command server capability and dynamic registration options. */
interface ExecuteCommandOptionsBase : WorkDoneProgressOptions {
  var commands: List<String>
}

/** Client capability for workspace/executeCommand. */
data class ExecuteCommandClientCapabilities(
    var dynamicRegistration: Boolean = false,
)

/** Server capability for workspace/executeCommand. */
data class ExecuteCommandOptions(
    override var commands: List<String> = emptyList(),
    override var workDoneProgress: Boolean = false,
) : ExecuteCommandOptionsBase

/** Registration options for dynamic workspace/executeCommand registration. */
data class ExecuteCommandRegistrationOptions(
    override var commands: List<String> = emptyList(),
    override var workDoneProgress: Boolean = false,
) : ExecuteCommandOptionsBase

/** Request params for workspace/executeCommand. */
data class ExecuteCommandParams(
    var command: String,
    var arguments: List<Any?>? = null,
    override var workDoneToken: Any? = null,
) : WorkDoneProgressParams {
  constructor(command: String) : this(command, null, null)

  constructor(command: String, arguments: List<Any?>?) : this(command, arguments, null)
}

/** Response wrapper for workspace/executeCommand. */
data class ExecuteCommandResult(
    var result: Any? = null,
)
