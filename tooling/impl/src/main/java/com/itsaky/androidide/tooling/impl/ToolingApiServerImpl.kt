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

package com.itsaky.androidide.tooling.impl

import com.itsaky.androidide.tooling.api.IProject
import com.itsaky.androidide.tooling.api.IToolingApiClient
import com.itsaky.androidide.tooling.api.IToolingApiServer
import com.itsaky.androidide.tooling.api.messages.GradleDistributionParams
import com.itsaky.androidide.tooling.api.messages.GradleDistributionType
import com.itsaky.androidide.tooling.api.messages.ExecutionRequest
import com.itsaky.androidide.tooling.api.messages.InitializeProjectParams
import com.itsaky.androidide.tooling.api.messages.TaskExecutionMessage
import com.itsaky.androidide.tooling.api.messages.result.BuildCancellationRequestResult
import com.itsaky.androidide.tooling.api.messages.result.BuildCancellationRequestResult.Reason.CANCELLATION_ERROR
import com.itsaky.androidide.tooling.api.messages.result.BuildInfo
import com.itsaky.androidide.tooling.api.messages.result.BuildResult
import com.itsaky.androidide.tooling.api.messages.result.ExecutionResult
import com.itsaky.androidide.tooling.api.messages.result.InitializeResult
import com.itsaky.androidide.tooling.api.messages.result.TaskExecutionResult
import com.itsaky.androidide.tooling.api.messages.result.TaskExecutionResult.Failure
import com.itsaky.androidide.tooling.api.messages.result.TaskExecutionResult.Failure.BUILD_CANCELLED
import com.itsaky.androidide.tooling.api.messages.result.TaskExecutionResult.Failure.BUILD_FAILED
import com.itsaky.androidide.tooling.api.messages.result.TaskExecutionResult.Failure.CONNECTION_CLOSED
import com.itsaky.androidide.tooling.api.messages.result.TaskExecutionResult.Failure.CONNECTION_ERROR
import com.itsaky.androidide.tooling.api.messages.result.TaskExecutionResult.Failure.PROJECT_DIRECTORY_INACCESSIBLE
import com.itsaky.androidide.tooling.api.messages.result.TaskExecutionResult.Failure.PROJECT_NOT_DIRECTORY
import com.itsaky.androidide.tooling.api.messages.result.TaskExecutionResult.Failure.PROJECT_NOT_FOUND
import com.itsaky.androidide.tooling.api.messages.result.TaskExecutionResult.Failure.PROJECT_NOT_INITIALIZED
import com.itsaky.androidide.tooling.api.messages.result.TaskExecutionResult.Failure.UNKNOWN
import com.itsaky.androidide.tooling.api.messages.result.TaskExecutionResult.Failure.UNSUPPORTED_BUILD_ARGUMENT
import com.itsaky.androidide.tooling.api.messages.result.TaskExecutionResult.Failure.UNSUPPORTED_CONFIGURATION
import com.itsaky.androidide.tooling.api.messages.result.TaskExecutionResult.Failure.UNSUPPORTED_GRADLE_VERSION
import com.itsaky.androidide.tooling.api.models.ToolingServerMetadata
import com.itsaky.androidide.tooling.impl.internal.ProjectImpl
import com.itsaky.androidide.tooling.impl.net.SimpleHttpProxy
import com.itsaky.androidide.tooling.impl.progress.ForwardingProgressListener
import com.itsaky.androidide.tooling.impl.sync.ModelBuilderException
import com.itsaky.androidide.tooling.impl.sync.RootModelBuilder
import com.itsaky.androidide.tooling.impl.sync.RootProjectModelBuilderParams
import com.itsaky.androidide.utils.StopWatch
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import org.gradle.tooling.BuildCancelledException
import org.gradle.tooling.BuildException
import org.gradle.tooling.CancellationTokenSource
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.tooling.exceptions.UnsupportedBuildArgumentException
import org.gradle.tooling.exceptions.UnsupportedOperationConfigurationException
import org.gradle.tooling.internal.consumer.DefaultGradleConnector
import org.gradle.tooling.events.OperationType
import org.slf4j.LoggerFactory

/**
 * Implementation for the Gradle Tooling API server.
 *
 * @author Akash Yadav
 */
internal class ToolingApiServerImpl(private val project: ProjectImpl) : IToolingApiServer {
  private data class NegotiatedFeatureSupport(
      val modelSnapshot: Boolean,
      val queryService: Boolean,
      val phasedAction: Boolean,
  )

  private var client: IToolingApiClient? = null
  private var connector: GradleConnector? = null
  private var connection: ProjectConnection? = null
  private var lastInitParams: InitializeProjectParams? = null
  private var _buildCancellationToken: CancellationTokenSource? = null
  private var httpProxy: SimpleHttpProxy? = null
  private var negotiatedOperationTypes: Set<String> = emptySet()

  private val cancellationTokenAccessLock = ReentrantLock(/* fair= */ true)
  private var buildCancellationToken: CancellationTokenSource?
    get() = cancellationTokenAccessLock.withLock { _buildCancellationToken }
    set(value) = cancellationTokenAccessLock.withLock { _buildCancellationToken = value }

  /** Whether the project has been initialized or not. */
  var isInitialized: Boolean = false
    private set

  /** Whether a build or project synchronization is in progress. */
  private var isBuildInProgress: Boolean = false

  /** Whether the server has a live connection to Gradle. */
  val isConnected: Boolean
    get() = connector != null || connection != null

  companion object {

    private val log = LoggerFactory.getLogger(ToolingApiServerImpl::class.java)

    /**
     * Time duration for which the the Tooling API server waits after calling
     * [DefaultGradleConnector.close] and before exiting the server's process.
     *
     * This delay should be long enough to let the tooling API stop the daemon but short enough so
     * that the server's process is not kept alive for longer duration.
     */
    const val DELAY_BEFORE_EXIT_MS = 1000L
    private const val SERVER_SUPPORTS_MODEL_SNAPSHOT = false
    private const val SERVER_SUPPORTS_QUERY_SERVICE = false
    private const val SERVER_SUPPORTS_PHASED_ACTION = true
  }

  override fun metadata(): CompletableFuture<ToolingServerMetadata> {
    return CompletableFuture.supplyAsync {
      ToolingServerMetadata(
          pid = ProcessHandle.current().pid().toInt(),
          supportsPhasedBuildAction = SERVER_SUPPORTS_PHASED_ACTION,
          supportsModelSnapshot = SERVER_SUPPORTS_MODEL_SNAPSHOT,
          supportsQueryService = SERVER_SUPPORTS_QUERY_SERVICE,
          supportedOperationTypes = Main.progressUpdateTypes().map { it.name }.toSet(),
          negotiatedOperationTypes = negotiatedOperationTypes,
          maxProgressEventsPerSecond =
              Main.maxProgressEventsPerSecond.takeIf { it != Int.MAX_VALUE },
      )
    }
  }

  override fun initialize(params: InitializeProjectParams): CompletableFuture<InitializeResult> {
    return runBuild {
      try {
        log.debug(
            "Received project initialization request: requestId={} directory={} distribution={}",
            params.requestId,
            params.directory,
            params.gradleDistribution,
        )

        Main.checkGradleWrapper()

        if (buildCancellationToken != null) {
          cancelCurrentBuild().get()
        }

        val projectDirectory = File(params.directory)
        val failureReason = validateProjectDirectory(projectDirectory)

        if (failureReason != null) {
          log.error(
              "Cannot initialize project: requestId={} failure={}",
              params.requestId,
              failureReason,
          )
          return@runBuild InitializeResult(false, failureReason, params.requestId)
        }

        // Ensure Gradle sees UTF-8/locale early via project gradle.properties
        ensureProjectGradleProperties(projectDirectory)

        val stopWatch = StopWatch("Connection to project")
        val isReinitializing = connector != null && connection != null && params == lastInitParams

        if (isReinitializing) {
          log.info("Project is being reinitialized")
          log.info("Reusing connector instance...")
        } else {
          // a new project is being initialized
          // or the project is being initialized with different parameters
          connector?.disconnect()

          connector = GradleConnector.newConnector().forProjectDirectory(projectDirectory)
          setupConnectorForGradleInstallation(this.connector!!, params.gradleDistribution)
          stopWatch.lap("Connector created")
        }

        lastInitParams = params

        val connector =
            checkNotNull(connector) {
              "Unable to create gradle connector for project directory: ${params.directory}"
            }

        notifyBeforeBuild(BuildInfo(emptyList()))

        if (isReinitializing) {
          log.info("Reusing project connection...")
        } else {
          connection = connector.connect()
        }

        val connection =
            checkNotNull(this.connection) {
              "Unable to create project connection for project directory: ${params.directory}"
            }

        stopWatch.lapFromLast("Project connection established")

        this.buildCancellationToken = GradleConnector.newCancellationTokenSource()

        // start local HTTP proxy for Gradle network (before exposing port)
        if (httpProxy == null) {
          httpProxy = SimpleHttpProxy(0).also { it.start() }
        }
        // Expose proxy port to child process via system property
        val proxyPort = httpProxy?.port ?: -1
        if (proxyPort > 0) {
          try {
            System.setProperty("ANDROIDIDE_PROXY_PORT", proxyPort.toString())
          } catch (_: Throwable) {}
        }

        val project =
            try {
              val modelBuilderParams =
                  RootProjectModelBuilderParams(connection, this.buildCancellationToken!!.token())
              val impl =
                  RootModelBuilder(params).build(modelBuilderParams) as? ProjectImpl?
                      ?: throw ModelBuilderException("Failed to build project model")
              impl
            } catch (err: Throwable) {
              throw err
            }

        stopWatch.lapFromLast("Project read successful")
        stopWatch.log()

        this.project.setFrom(project)
        this.isInitialized = true

        Main.configureProgressDispatch(params.clientCapabilities.maxEventsPerSecond)

        negotiatedOperationTypes =
            negotiateOperationTypes(
                    params.clientCapabilities.requestedOperationTypes.toOperationTypes(),
                    Main.progressUpdateTypes(),
                    params.clientCapabilities.preferLightweightSync,
                )
                .map { it.name }
                .toSet()
        val negotiatedFeatures = negotiateFeatureSupport(params)
        log.info(
            "W1_INIT_FEATURE_SUMMARY requestId={} modelSnapshot={} queryService={} phasedAction={}",
            params.requestId,
            negotiatedFeatures.modelSnapshot,
            negotiatedFeatures.queryService,
            negotiatedFeatures.phasedAction,
        )

        log.info(
            "Project initialization succeeded: requestId={} negotiatedOperationTypes={}",
            params.requestId,
            negotiatedOperationTypes,
        )
        notifyBuildSuccess(emptyList())
        return@runBuild InitializeResult(
            isSuccessful = true,
            requestId = params.requestId,
            negotiatedOperationTypes = negotiatedOperationTypes,
            supportsModelSnapshot = negotiatedFeatures.modelSnapshot,
            supportsQueryService = negotiatedFeatures.queryService,
            supportsPhasedAction = negotiatedFeatures.phasedAction,
        )
      } catch (err: Throwable) {
        log.error("Failed to initialize project: requestId={}", params.requestId, err)
        notifyBuildFailure(emptyList())
        return@runBuild InitializeResult(false, getTaskFailureType(err), params.requestId)
      }
    }
  }

  private fun negotiateFeatureSupport(
      params: InitializeProjectParams
  ): NegotiatedFeatureSupport {
    val capabilities = params.clientCapabilities
    return NegotiatedFeatureSupport(
        modelSnapshot = capabilities.requestModelSnapshotSupport && SERVER_SUPPORTS_MODEL_SNAPSHOT,
        queryService = capabilities.requestQueryServiceSupport && SERVER_SUPPORTS_QUERY_SERVICE,
        phasedAction = capabilities.requestPhasedActionSupport && SERVER_SUPPORTS_PHASED_ACTION,
    )
  }

  private fun ensureProjectGradleProperties(projectDir: File) {
    try {
      val propsFile = File(projectDir, "gradle.properties")
      val props = java.util.Properties()
      if (propsFile.exists()) propsFile.inputStream().use { props.load(it) }

      val jvmArgsKey = "org.gradle.jvmargs"
      val enforced =
          "-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -Duser.language=en -Duser.country=US"
      val current = props.getProperty(jvmArgsKey)?.trim().orEmpty()
      if (!current.contains("file.encoding")) {
        props.setProperty(jvmArgsKey, if (current.isBlank()) enforced else "$current $enforced")
      }
      props.setProperty("systemProp.file.encoding", "UTF-8")
      props.setProperty("systemProp.sun.jnu.encoding", "UTF-8")
      props.setProperty("systemProp.user.language", "en")
      props.setProperty("systemProp.user.country", "US")

      propsFile.outputStream().use { out ->
        props.store(out, "AndroidIDE: enforce UTF-8 & locale for Gradle daemon")
      }
    } catch (_: Throwable) {
      // best-effort; ignore
    }
  }

  private fun validateProjectDirectory(projectDirectory: File) =
      when {
        !projectDirectory.exists() -> PROJECT_NOT_FOUND
        !projectDirectory.isDirectory -> PROJECT_NOT_DIRECTORY
        !projectDirectory.canRead() -> PROJECT_DIRECTORY_INACCESSIBLE
        else -> null
      }

  override fun isServerInitialized(): CompletableFuture<Boolean> {
    return CompletableFuture.supplyAsync { isInitialized }
  }

  override fun getRootProject(): CompletableFuture<IProject> {
    return CompletableFuture.supplyAsync {
      assertProjectInitialized()
      this.project
    }
  }

  override fun executeTasks(message: TaskExecutionMessage): CompletableFuture<TaskExecutionResult> {
    return runBuild {
      val execution = executeBuildRequest(message.asExecutionRequest())
      if (execution.isSuccessful) {
        return@runBuild TaskExecutionResult.SUCCESS
      }
      return@runBuild TaskExecutionResult(false, execution.failure, execution.diagnostics)
    }
  }

  override fun execute(request: ExecutionRequest): CompletableFuture<ExecutionResult> {
    return runBuild { executeBuildRequest(request) }
  }

  private fun executeBuildRequest(request: ExecutionRequest): ExecutionResult {
      if (!isServerInitialized().get()) {
        log.error("Cannot execute build request: {}", PROJECT_NOT_INITIALIZED)
        return ExecutionResult(request.requestId, false, PROJECT_NOT_INITIALIZED, "Project is not initialized")
      }

      val effectiveTasks = request.tasks.filter { it.isNotBlank() }
      if (effectiveTasks.isEmpty()) {
        return ExecutionResult(
            request.requestId,
            false,
            UNSUPPORTED_CONFIGURATION,
            "ExecutionRequest must contain at least one non-blank task",
        )
      }

      val lastInitParams = this.lastInitParams
      if (lastInitParams != null) {
        val projectDirectory = File(lastInitParams.directory)
        val failureReason = validateProjectDirectory(projectDirectory)
        if (failureReason != null) {
          log.error("Cannot execute build request: {}", failureReason)
          return ExecutionResult(request.requestId, false, failureReason, "Project directory validation failed")
        }
      }

      log.info("[requestId={}] Received request to run tasks: {}", request.requestId, request.tasks)

      Main.checkGradleWrapper()

      val connection =
          checkNotNull(this.connection) {
            "ProjectConnection has not been initialized. Cannot execute tasks."
          }

      val builder = connection.newBuild()

      // System.in and System.out are used for communication between this server and the
      // client.
      val out = LoggingOutputStream()
      builder.setStandardInput("NoOp".byteInputStream())
      builder.setStandardError(out)
      builder.setStandardOutput(out)
      builder.forTasks(*effectiveTasks.toTypedArray())

      if (request.arguments.isNotEmpty()) {
        builder.addArguments(*request.arguments.filter { it.isNotBlank() }.toTypedArray())
      }

      if (request.jvmArguments.isNotEmpty()) {
        builder.setJvmArguments(*request.jvmArguments.filter { it.isNotBlank() }.toTypedArray())
      }

      val injectedArgs =
          lastInitParams?.androidParams?.injectedProperties
              ?.toGradleArguments()
              ?.filter { it.isNotBlank() }
              ?: emptyList()
      if (injectedArgs.isNotEmpty()) {
        log.debug("Applying Android injected properties: {}", injectedArgs)
        builder.addArguments(*injectedArgs.toTypedArray())
      }

      val effectiveOperationTypes =
          if (request.operationTypes.isEmpty()) {
            negotiatedOperationTypes.toOperationTypes()
          } else {
            negotiateOperationTypes(request.operationTypes.toOperationTypes(), Main.progressUpdateTypes())
          }

      Main.finalizeLauncher(builder, effectiveOperationTypes)

      this.buildCancellationToken = GradleConnector.newCancellationTokenSource()
      builder.withCancellationToken(this.buildCancellationToken!!.token())

      notifyBeforeBuild(BuildInfo(effectiveTasks))

      try {
        builder.run()
        this.buildCancellationToken = null
        notifyBuildSuccess(effectiveTasks)
        return ExecutionResult(requestId = request.requestId, isSuccessful = true)
      } catch (error: Throwable) {
        notifyBuildFailure(effectiveTasks)
        return ExecutionResult(request.requestId, false, getTaskFailureType(error), diagnosticMessage(error))
      }
  }

  private fun diagnosticMessage(error: Throwable): String {
    val cause = error.cause
    val causeSegment =
        if (cause != null && cause !== error) {
          " | cause=${cause::class.java.simpleName}: ${cause.message.orEmpty()}"
        } else {
          ""
        }
    return "${error::class.java.simpleName}: ${error.message.orEmpty()}$causeSegment"
  }

  private fun setupConnectorForGradleInstallation(
      connector: GradleConnector,
      params: GradleDistributionParams,
  ) {
    when (params.type) {
      GradleDistributionType.GRADLE_WRAPPER -> {
        log.info("Using Gradle wrapper for build...")
      }

      GradleDistributionType.GRADLE_INSTALLATION -> {
        val file = File(params.value)
        if (!file.exists() || !file.isDirectory) {
          log.error("Specified Gradle installation does not exist: {}", params)
          return
        }

        log.info("Using Gradle installation: {}", file.canonicalPath)
        connector.useInstallation(file)
      }

      GradleDistributionType.GRADLE_VERSION -> {
        log.info("Using Gradle version '{}'", params.value)
        connector.useGradleVersion(params.value)
      }
    }
  }

  private fun negotiateOperationTypes(
      requested: Set<OperationType>,
      supported: Set<OperationType>,
      preferLightweightSync: Boolean = false,
  ): Set<OperationType> {
    var negotiated =
        if (requested.isEmpty()) {
          if (preferLightweightSync) {
            supported.filterTo(linkedSetOf()) {
              it == OperationType.TASK || it == OperationType.PROJECT_CONFIGURATION
            }
          } else {
            supported
          }
        } else {
          requested.filterTo(linkedSetOf()) { supported.contains(it) }
        }

    if (negotiated.isEmpty() && supported.isNotEmpty()) {
      val fallback = supported.filterTo(linkedSetOf()) {
        it == OperationType.TASK || it == OperationType.PROJECT_CONFIGURATION
      }
      negotiated = if (fallback.isNotEmpty()) fallback else linkedSetOf(supported.first())
      log.warn(
          "Operation negotiation fallback applied. requested={} fallbackNegotiated={}",
          requested,
          negotiated,
      )
    }

    if (requested.isNotEmpty() && negotiated.isEmpty()) {
      log.warn(
          "Operation negotiation yielded empty set. requested={} supported={} preferLightweightSync={}",
          requested,
          supported,
          preferLightweightSync,
      )
    }

    if (requested.isNotEmpty() && negotiated.size < requested.size) {
      val dropped = requested.filterNot { negotiated.contains(it) }.toSet()
      log.info("Operation negotiation dropped unsupported types: {}", dropped)
    }

    return negotiated
  }

  private fun Set<String>.toOperationTypes(): Set<OperationType> {
    return mapNotNullTo(linkedSetOf()) { typeName ->
      runCatching { OperationType.valueOf(typeName) }.getOrNull()
    }
  }

  private fun notifyBuildFailure(tasks: List<String>) {
    logProgressClosureWarningsIfAny("failure")
    client?.onBuildFailed(BuildResult((tasks)))
  }

  private fun notifyBuildSuccess(tasks: List<String>) {
    logProgressClosureWarningsIfAny("success")
    client?.onBuildSuccessful(BuildResult(tasks))
  }

  private fun notifyBeforeBuild(buildInfo: BuildInfo) {
    ForwardingProgressListener.onBuildStart()
    client?.prepareBuild(buildInfo)
  }

  private fun logProgressClosureWarningsIfAny(outcome: String) {
    val summary = ForwardingProgressListener.onBuildEnd()
    log.info(
        "W1_PROGRESS_SUMMARY outcome={} started={} finished={} dangling={}",
        outcome,
        summary.startedEvents,
        summary.finishedEvents,
        summary.danglingByOperation.size,
    )
    log.info(
        "Progress closure summary on build {}: startedEvents={} finishedEvents={} danglingOperationCount={}",
        outcome,
        summary.startedEvents,
        summary.finishedEvents,
        summary.danglingByOperation.size,
    )
    if (summary.danglingByOperation.isNotEmpty()) {
      log.warn(
          "Progress event closure check detected dangling start events on build {}: {}",
          outcome,
          summary.danglingByOperation,
      )
    }
  }

  override fun cancelCurrentBuild(): CompletableFuture<BuildCancellationRequestResult> {
    return CompletableFuture.supplyAsync {
      if (this.buildCancellationToken == null) {
        BuildCancellationRequestResult(false, BuildCancellationRequestResult.Reason.NO_RUNNING_BUILD)
      } else {
        try {
          this.buildCancellationToken!!.cancel()
          this.buildCancellationToken = null
          BuildCancellationRequestResult(true, null)
        } catch (e: Exception) {
          val failureReason = CANCELLATION_ERROR
          failureReason.message = "${failureReason.message}: ${e.message}"
          BuildCancellationRequestResult(false, failureReason)
        }
      }
    }
  }

  override fun shutdown(): CompletableFuture<Void> {
    return CompletableFuture.supplyAsync {
      log.info("Shutting down Tooling API Server...")

      connection?.close()
      connector?.disconnect()
      connection = null
      connector = null

      // Stop all daemons
      log.info("Stopping all Gradle Daemons...")
      DefaultGradleConnector.close()

      // stop proxy
      try {
        httpProxy?.stop()
      } catch (_: Throwable) {}
      httpProxy = null

      // update the initialization flag before cancelling future
      this.isInitialized = false

      // cancelling this future will finish the Tooling API server process
      // see com.itsaky.androidide.tooling.impl.Main.main(String[])
      Main.future?.cancel(true)

      this.client = null
      this.buildCancellationToken = null // connector.disconnect() cancels any running builds
      this.lastInitParams = null
      Main.future = null
      Main.client = null

      null
    }
  }

  private fun getTaskFailureType(error: Throwable): Failure =
      when (error) {
        is BuildException -> BUILD_FAILED
        is BuildCancelledException -> BUILD_CANCELLED
        is UnsupportedOperationConfigurationException -> UNSUPPORTED_CONFIGURATION
        is UnsupportedVersionException -> UNSUPPORTED_GRADLE_VERSION
        is UnsupportedBuildArgumentException -> UNSUPPORTED_BUILD_ARGUMENT
        is GradleConnectionException -> CONNECTION_ERROR
        is java.lang.IllegalStateException -> CONNECTION_CLOSED
        else -> UNKNOWN
      }

  private fun <T : Any?> supplyAsync(action: () -> T): CompletableFuture<T> =
      CompletableFuture.supplyAsync(action)

  private fun <T : Any?> runBuild(action: () -> T): CompletableFuture<T> =
      supplyAsync {
        if (isBuildInProgress) {
          log.error("Cannot run build, build is already in prorgess!")
          throw IllegalStateException("Build is already in progress")
        }

        isBuildInProgress = true
        try {
          action()
        } finally {
          isBuildInProgress = false
        }
      }

  private fun assertProjectInitialized() {
    if (!isServerInitialized().get()) {
      throw CompletionException(IllegalStateException("Project is not initialized!"))
    }
  }

  fun connect(client: IToolingApiClient) {
    this.client = client
  }
}
