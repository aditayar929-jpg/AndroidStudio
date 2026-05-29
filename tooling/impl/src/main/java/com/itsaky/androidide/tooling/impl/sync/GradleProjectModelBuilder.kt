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
package com.itsaky.androidide.tooling.impl.sync

import com.itsaky.androidide.tooling.api.IGradleProject
import com.itsaky.androidide.tooling.api.models.BuildEnvironmentModel
import com.itsaky.androidide.tooling.api.models.GradleBuildModel
import com.itsaky.androidide.tooling.api.models.GradleEnvironmentModel
import com.itsaky.androidide.tooling.api.models.JavaEnvironmentModel
import com.itsaky.androidide.tooling.api.messages.InitializeProjectParams
import com.itsaky.androidide.tooling.impl.internal.GradleProjectImpl
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.gradle.GradleBuild

/**
 * Builds model for root Gradle project (represented with [IGradleProject].
 *
 * @author Akash Yadav
 */
class GradleProjectModelBuilder(initializationParams: InitializeProjectParams) :
    AbstractModelBuilder<GradleProjectModelBuilderParams, IGradleProject>(initializationParams) {

  @Throws(ModelBuilderException::class)
  override fun build(param: GradleProjectModelBuilderParams): IGradleProject {
    val buildEnvironment = param.controller.findModel(BuildEnvironment::class.java)
    val gradleBuild = param.controller.findModel(GradleBuild::class.java)

    val envModel =
        buildEnvironment?.let { env ->
          BuildEnvironmentModel(
              buildId = env.buildIdentifier.rootDir?.absolutePath,
              gradle =
                  GradleEnvironmentModel(
                      gradleVersion = env.gradle.gradleVersion,
                      gradleUserHome = env.gradle.gradleUserHome,
                  ),
              java =
                  runCatching {
                        JavaEnvironmentModel(
                            javaHome = env.java.javaHome,
                            jvmArguments = env.java.jvmArguments ?: emptyList(),
                        )
                      }
                      .getOrNull(),
              versionInfo = runCatching { env.versionInfo }.getOrNull(),
          )
        }

    val buildModel =
        gradleBuild?.let { gb ->
          GradleBuildModel(
              buildId = gb.buildIdentifier.rootDir?.absolutePath,
              rootProjectPath = gb.rootProject.path,
              projectPaths = gb.projects.map { it.path },
              includedBuildIds = gb.includedBuilds.map { it.buildIdentifier.rootDir?.absolutePath ?: "" },
              editableBuildIds = gb.editableBuilds.map { it.buildIdentifier.rootDir?.absolutePath ?: "" },
          )
        }

    return GradleProjectImpl(param.gradleProject, envModel, buildModel)
  }
}
