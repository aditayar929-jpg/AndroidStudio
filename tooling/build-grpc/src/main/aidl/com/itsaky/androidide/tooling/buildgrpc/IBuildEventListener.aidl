package com.itsaky.androidide.tooling.buildgrpc;

interface IBuildEventListener {
  void onBuildEvent(in byte[] buildEventPayload);
  void onBuildStreamClosed(String requestId, boolean successful);
}
