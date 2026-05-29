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

import com.itsaky.androidide.tooling.api.models.JavaContentRoot
import com.itsaky.androidide.tooling.api.models.JavaModuleDependency
import java.util.concurrent.CompletableFuture
import com.zerostudio.tooling.buildgrpc.customapi.rpc.BinaryRpcRequest
import com.zerostudio.tooling.buildgrpc.customapi.rpc.BinaryRpcSegment

/**
 * Model for a Java library project.
 *
 * @author Akash Yadav
 */
@BinaryRpcSegment("java")
interface IJavaProject : IModuleProject {

  /** Get the content roots for this Java project. */
  @BinaryRpcRequest fun getContentRoots(): CompletableFuture<List<JavaContentRoot>>

  /** Get the dependencies of this Java project. */
  @BinaryRpcRequest fun getDependencies(): CompletableFuture<List<JavaModuleDependency>>
}
