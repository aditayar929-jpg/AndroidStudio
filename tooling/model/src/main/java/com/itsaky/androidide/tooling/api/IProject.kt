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

import com.itsaky.androidide.builder.model.DefaultProjectSyncIssues
import com.itsaky.androidide.tooling.api.models.BasicProjectMetadata
import java.util.concurrent.CompletableFuture
import com.zerostudio.tooling.buildgrpc.customapi.rpc.BinaryRpcRequest
import com.zerostudio.tooling.buildgrpc.customapi.rpc.BinaryRpcSegment

/**
 * A service providing access to the whole Gradle project and its structure.
 *
 * This class
 *
 * @author Akash Yadav
 */
@BinaryRpcSegment("root")
interface IProject : IProjectQueries {

  /** Get all the projects included in this root project. */
  @BinaryRpcRequest fun getProjects(): CompletableFuture<List<BasicProjectMetadata>>

  /** Get the project sync issues. */
  @BinaryRpcRequest fun getProjectSyncIssues(): CompletableFuture<DefaultProjectSyncIssues>

  companion object {

    /** Name that can be used for project whose [BasicProjectMetadata.name] property is null. */
    const val PROJECT_UNKNOWN = "Unknown"
  }
}
