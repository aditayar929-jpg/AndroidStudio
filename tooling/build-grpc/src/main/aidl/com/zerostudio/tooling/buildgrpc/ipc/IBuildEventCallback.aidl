package com.zerostudio.tooling.buildgrpc.ipc;

interface IBuildEventCallback {
    oneway void onBuildEvent(in byte[] eventPayload);
    oneway void onBuildFinished(String buildId, boolean success, String summary);
}
