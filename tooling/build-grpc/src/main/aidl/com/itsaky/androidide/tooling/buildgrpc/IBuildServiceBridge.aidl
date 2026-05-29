package com.itsaky.androidide.tooling.buildgrpc;

interface IBuildServiceBridge {
  byte[] initialize(in byte[] initializeBuildRequestPayload);
  byte[] queryTargets(in byte[] queryBuildTargetsRequestPayload);
  byte[] executeBuild(in byte[] executeBuildRequestPayload);
  byte[] getBuildResult(in byte[] executeBuildRequestPayload);
}
