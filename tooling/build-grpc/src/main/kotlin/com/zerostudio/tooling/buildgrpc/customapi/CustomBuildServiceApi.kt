package com.zerostudio.tooling.buildgrpc.customapi

import com.zerostudio.tooling.buildgrpc.ActionExecutionRequest
import com.zerostudio.tooling.buildgrpc.ActionExecutionResult
import com.zerostudio.tooling.buildgrpc.BinaryBuildServiceApi
import com.zerostudio.tooling.buildgrpc.BuildCancelRequest
import com.zerostudio.tooling.buildgrpc.BuildCancelResponse
import com.zerostudio.tooling.buildgrpc.BuildInitializeRequest
import com.zerostudio.tooling.buildgrpc.BuildInitializeResponse
import com.zerostudio.tooling.buildgrpc.BuildShutdownRequest
import com.zerostudio.tooling.buildgrpc.BuildShutdownResponse
import com.zerostudio.tooling.buildgrpc.BuildTargetCompileRequest
import com.zerostudio.tooling.buildgrpc.BuildTargetDependencyModulesRequest
import com.zerostudio.tooling.buildgrpc.BuildTargetDependencyModulesResponse
import com.zerostudio.tooling.buildgrpc.BuildTargetRunRequest
import com.zerostudio.tooling.buildgrpc.BuildTargetTestRequest
import com.zerostudio.tooling.buildgrpc.WorkspaceBuildTargetsRequest
import com.zerostudio.tooling.buildgrpc.WorkspaceBuildTargetsResponse
import com.zerostudio.tooling.buildgrpc.proto.BuildEventEnvelope
import kotlinx.coroutines.flow.Flow

/**
 * Project-specific API layer on top of the standardized binary build protocol.
 */
interface CustomBuildServiceApi {
  suspend fun buildInitialize(request: BuildInitializeRequest): BuildInitializeResponse

  suspend fun workspaceBuildTargets(request: WorkspaceBuildTargetsRequest): WorkspaceBuildTargetsResponse

  fun buildTargetCompile(request: BuildTargetCompileRequest): Flow<BuildEventEnvelope>

  fun buildTargetTest(request: BuildTargetTestRequest): Flow<BuildEventEnvelope>

  fun buildTargetRun(request: BuildTargetRunRequest): Flow<BuildEventEnvelope>

  suspend fun buildTargetDependencyModules(
    request: BuildTargetDependencyModulesRequest,
  ): BuildTargetDependencyModulesResponse

  suspend fun executeAction(request: ActionExecutionRequest): ActionExecutionResult

  suspend fun cancelBuild(request: BuildCancelRequest): BuildCancelResponse

  suspend fun shutdown(request: BuildShutdownRequest): BuildShutdownResponse
}

class DefaultCustomBuildServiceApi(
  private val delegate: BinaryBuildServiceApi,
) : CustomBuildServiceApi {
  override suspend fun buildInitialize(request: BuildInitializeRequest): BuildInitializeResponse =
    delegate.buildInitialize(request)

  override suspend fun workspaceBuildTargets(request: WorkspaceBuildTargetsRequest): WorkspaceBuildTargetsResponse =
    delegate.workspaceBuildTargets(request)

  override fun buildTargetCompile(request: BuildTargetCompileRequest): Flow<BuildEventEnvelope> =
    delegate.buildTargetCompile(request)

  override fun buildTargetTest(request: BuildTargetTestRequest): Flow<BuildEventEnvelope> =
    delegate.buildTargetTest(request)

  override fun buildTargetRun(request: BuildTargetRunRequest): Flow<BuildEventEnvelope> =
    delegate.buildTargetRun(request)

  override suspend fun buildTargetDependencyModules(
    request: BuildTargetDependencyModulesRequest,
  ): BuildTargetDependencyModulesResponse = delegate.buildTargetDependencyModules(request)

  override suspend fun executeAction(request: ActionExecutionRequest): ActionExecutionResult =
    delegate.executeAction(request)

  override suspend fun cancelBuild(request: BuildCancelRequest): BuildCancelResponse =
    delegate.cancelBuild(request)

  override suspend fun shutdown(request: BuildShutdownRequest): BuildShutdownResponse =
    delegate.shutdown(request)
}
