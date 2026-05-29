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

package com.itsaky.androidide.tooling.api

import com.itsaky.androidide.tooling.api.models.GradleTask
import com.itsaky.androidide.tooling.api.models.BuildEnvironmentModel
import com.itsaky.androidide.tooling.api.models.GradleBuildModel
import com.itsaky.androidide.tooling.api.models.ProjectMetadata
import java.util.concurrent.CompletableFuture
import com.zerostudio.tooling.buildgrpc.customapi.rpc.BinaryRpcRequest
import com.zerostudio.tooling.buildgrpc.customapi.rpc.BinaryRpcSegment

/**
 * A model for representing a project which is not an Android or Java project.
 *
 * @author Akash Yadav
 */
@BinaryRpcSegment("gradle")
interface IGradleProject {

  /**
   * Get the metadata about the project. This includes basic information about the project.
   *
   * @see [ProjectMetadata].
   */
  @BinaryRpcRequest fun getMetadata(): CompletableFuture<ProjectMetadata>

  /** Get this project's tasks. */
  @BinaryRpcRequest fun getTasks(): CompletableFuture<List<GradleTask>>

  /** Built-in Tooling API BuildEnvironment model snapshot. */
  @BinaryRpcRequest fun getBuildEnvironment(): CompletableFuture<BuildEnvironmentModel>

  /** Built-in Tooling API GradleBuild model snapshot (composite/included builds). */
  @BinaryRpcRequest fun getGradleBuild(): CompletableFuture<GradleBuildModel>
}
