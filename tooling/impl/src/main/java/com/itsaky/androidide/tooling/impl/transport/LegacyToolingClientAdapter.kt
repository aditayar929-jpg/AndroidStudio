package com.itsaky.androidide.tooling.impl.transport

import com.itsaky.androidide.tooling.api.IToolingApiClient
import com.itsaky.androidide.tooling.api.messages.LogMessageParams
import com.itsaky.androidide.tooling.api.messages.result.BuildInfo
import com.itsaky.androidide.tooling.api.messages.result.BuildResult
import com.itsaky.androidide.tooling.api.messages.result.GradleWrapperCheckResult
import com.itsaky.androidide.tooling.api.transport.ToolingTransportClientObserver
import com.itsaky.androidide.tooling.events.ProgressEvent
import java.util.concurrent.CompletableFuture

/**
 * Legacy JSON-RPC callback adapter that bridges [IToolingApiClient] into
 * transport-neutral [ToolingTransportClientObserver].
 */
class LegacyToolingClientAdapter(private val observer: ToolingTransportClientObserver) :
    IToolingApiClient {

  override fun logMessage(params: LogMessageParams) {
    observer.onLogMessage(params.tag, params.level, params.message)
  }

  override fun logOutput(line: String) {
    observer.onLogMessage("ToolingOutput", 'I', line)
  }

  override fun prepareBuild(buildInfo: BuildInfo) {
    observer.onBuildPrepared(buildInfo)
  }

  override fun onBuildSuccessful(result: BuildResult) {
    observer.onBuildSuccessful(result)
  }

  override fun onBuildFailed(result: BuildResult) {
    observer.onBuildFailed(result)
  }

  override fun onProgressEvent(event: ProgressEvent) {
    observer.onProgressEvent(event)
  }

  override fun getBuildArguments(): CompletableFuture<List<String>> = observer.buildArguments()

  override fun checkGradleWrapperAvailability(): CompletableFuture<GradleWrapperCheckResult> =
      CompletableFuture.completedFuture(GradleWrapperCheckResult(true))
}
