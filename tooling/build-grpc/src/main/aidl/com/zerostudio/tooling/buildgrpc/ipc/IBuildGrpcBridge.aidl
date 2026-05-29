package com.zerostudio.tooling.buildgrpc.ipc;

import com.zerostudio.tooling.buildgrpc.ipc.IBuildEventCallback;

interface IBuildGrpcBridge {
    String initialize(String workspaceRoot, String clientName, String clientVersion);
    void startBuild(in byte[] buildRequestPayload, IBuildEventCallback callback);
    void cancelBuild(String buildId);
    void shutdown(String reason);
}
