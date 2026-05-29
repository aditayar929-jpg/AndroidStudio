package com.zerostudio.tooling.buildgrpc

import com.zerostudio.tooling.buildgrpc.proto.InitializeRequest
import com.zerostudio.tooling.buildgrpc.proto.InitializeResponse
import com.zerostudio.tooling.buildgrpc.proto.StartBuildRequest

object BuildProtocolMapper {
  fun toInit(req: InitializeRequest): BuildInit = BuildInit(
    workspaceRoot = req.workspaceRoot,
    clientName = req.clientName,
    clientVersion = req.clientVersion,
    capabilities = req.supportedCapabilitiesList,
  )

  fun toInitResponse(info: BuildServerInfo): InitializeResponse = InitializeResponse.newBuilder()
    .setServerName(info.serverName)
    .setServerVersion(info.serverVersion)
    .addAllSupportedLanguages(info.supportedLanguages)
    .addAllProtocolFeatures(info.protocolFeatures)
    .build()

  fun toStart(req: StartBuildRequest): BuildStart = BuildStart(
    buildId = req.buildId,
    targets = req.targetsList,
    options = req.optionsMap,
  )
}
