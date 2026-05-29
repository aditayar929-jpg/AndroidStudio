package com.itsaky.androidide.tooling.api.models

import java.io.File
import java.io.Serializable

data class GradleEnvironmentModel(
    val gradleVersion: String,
    val gradleUserHome: File?,
) : Serializable

data class JavaEnvironmentModel(
    val javaHome: File?,
    val jvmArguments: List<String>,
) : Serializable

data class BuildEnvironmentModel(
    val buildId: String?,
    val gradle: GradleEnvironmentModel,
    val java: JavaEnvironmentModel?,
    val versionInfo: String?,
) : Serializable

data class GradleBuildModel(
    val buildId: String?,
    val rootProjectPath: String,
    val projectPaths: List<String>,
    val includedBuildIds: List<String>,
    val editableBuildIds: List<String>,
) : Serializable
