package com.itsaky.androidide.tooling.reapi.protocol

object ReapiProtoCatalog {
  const val REAPI_EXECUTION_PACKAGE: String = "build.bazel.remote.execution.v2"
  const val REAPI_ASSET_PACKAGE: String = "build.bazel.remote.asset.v1"
  const val REAPI_LOGSTREAM_PACKAGE: String = "build.bazel.remote.logstream.v1"
  const val REAPI_SEMVER_PACKAGE: String = "build.bazel.semver"

  val allPackages: Set<String> =
    setOf(
      REAPI_EXECUTION_PACKAGE,
      REAPI_ASSET_PACKAGE,
      REAPI_LOGSTREAM_PACKAGE,
      REAPI_SEMVER_PACKAGE,
    )
}
