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

package com.itsaky.androidide.projects.internal

import com.itsaky.androidide.projects.GradleProject
import com.itsaky.androidide.projects.android.AndroidModule
import com.itsaky.androidide.projects.java.JavaModule
import com.itsaky.androidide.tooling.api.IAndroidProject
import com.itsaky.androidide.tooling.api.IGradleProject
import com.itsaky.androidide.tooling.api.IJavaProject
import com.itsaky.androidide.tooling.api.IProject
import com.itsaky.androidide.tooling.api.ProjectType
import com.itsaky.androidide.tooling.api.models.AndroidProjectMetadata
import com.itsaky.androidide.tooling.api.models.BasicProjectMetadata
import com.itsaky.androidide.tooling.api.models.JavaProjectMetadata
import com.itsaky.androidide.tooling.api.models.params.StringParameter
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import org.slf4j.LoggerFactory

/**
 * Transforms project models from tooling API to the projects API.
 *
 * @author Akash Yadav
 */
internal object WorkspaceModelBuilder {

  private val log = LoggerFactory.getLogger(WorkspaceModelBuilder::class.java)

  fun build(projectDir: File, project: IProject): WorkspaceImpl? {
    try {
      val allProjects = project.getProjects().get()
      val selectionResult = project.selectProject(StringParameter("")).get()
      check(selectionResult.isSuccessful) { "Cannot find root project" }

      val rootProject =
          when (project.getType().get()) {
            ProjectType.Gradle -> transform(project.asGradleProject())
            ProjectType.Android -> transform(project.asAndroidProject())
            ProjectType.Java -> transform(project.asJavaProject())
            ProjectType.Unknown -> {
              log.warn(
                  "Root project type is UNKNOWN, falling back to Gradle root model transformation"
              )
              transform(project.asGradleProject())
            }
          }

      return WorkspaceImpl(
          projectDir,
          rootProject,
          CopyOnWriteArrayList(transform(allProjects, project)),
          project.getProjectSyncIssues().get(),
      )
    } catch (error: Throwable) {
      log.error("Unable to transform project", error)
      return null
    }
  }

  private fun transform(rootProject: IGradleProject): GradleProject {
    val metadata = rootProject.getMetadata().get()
    return GradleProject(
        name = metadata.name ?: IProject.PROJECT_UNKNOWN,
        description = metadata.description ?: "",
        path = metadata.projectPath,
        projectDir = metadata.projectDir,
        buildDir = metadata.buildDir,
        buildScript = metadata.buildScript,

        // The list will never change, we could make these thread-safe with
        // CopyOnWriteArrayList
        tasks = CopyOnWriteArrayList(rootProject.getTasks().get() ?: listOf()),
    )
  }

  private fun transform(project: IAndroidProject): AndroidModule {
    val metadata = project.getMetadata().get() as AndroidProjectMetadata
    val libraryMap = project.getLibraryMap().get()
    val variants = project.getVariants().get()
    val configuredVariant = project.getConfiguredVariant().get()
    return AndroidModule(
        name = metadata.name ?: IProject.PROJECT_UNKNOWN,
        description = metadata.description ?: "",
        path = metadata.projectPath,
        projectDir = metadata.projectDir,
        buildDir = metadata.buildDir,
        buildScript = metadata.buildScript,
        tasks = project.getTasks().get(),
        resourcePrefix = metadata.resourcePrefix,
        namespace = metadata.namespace,
        androidTestNamespace = metadata.androidTestNamespace,
        testFixtureNamespace = metadata.testFixtureNamespace,
        projectType = metadata.androidType,
        mainSourceSet = project.getMainSourceSet().get(),
        flags = metadata.flags,
        compilerSettings = metadata.javaCompileOptions,
        viewBindingOptions = metadata.viewBindingOptions,
        bootClassPaths = project.getBootClasspaths().get(),
        libraries = libraryMap.keys,
        libraryMap = libraryMap,
        lintCheckJars = project.getLintCheckJars().get(),
        variants = variants,
        configuredVariant = variants.find { it.name == configuredVariant },
        classesJar = metadata.classesJar,
    )
  }

  private fun transform(project: IJavaProject): JavaModule {
    val metadata = project.getMetadata().get() as JavaProjectMetadata
    return JavaModule(
        name = metadata.name ?: IProject.PROJECT_UNKNOWN,
        description = metadata.description ?: "",
        path = metadata.projectPath,
        projectDir = metadata.projectDir,
        buildDir = metadata.buildDir,
        buildScript = metadata.buildScript,
        tasks = project.getTasks().get(),
        contentRoots = project.getContentRoots().get(),
        dependencies = project.getDependencies().get(),
        compilerSettings = metadata.compilerSettings,
        classesJar = metadata.classesJar,
    )
  }

  private fun transform(modules: List<BasicProjectMetadata>, root: IProject): List<GradleProject> {
    return mutableListOf<GradleProject>().apply {
      for (module in modules) {
        add(createProject(module, root))
      }
    }
  }

  private fun createProject(moduleMetadata: BasicProjectMetadata, root: IProject): GradleProject {
    val selectionResult = root.selectProject(StringParameter(moduleMetadata.projectPath)).get()
    check(selectionResult.isSuccessful) {
      "Selection failed for project '${moduleMetadata.projectPath}' but it is included in all projects."
    }

    val selectedGradleProject = root.asGradleProject()
    val type = selectedGradleProject.getMetadata().get().type

    return when (type) {
      ProjectType.Gradle,
      ProjectType.Unknown -> transform(selectedGradleProject)

      ProjectType.Android ->
          runCatching { transform(root.asAndroidProject()) }
              .getOrElse {
                log.warn(
                    "Failed to transform Android module '{}' as Android model, falling back to Gradle model",
                    moduleMetadata.projectPath,
                    it,
                )
                transform(selectedGradleProject)
              }

      ProjectType.Java ->
          runCatching { transform(root.asJavaProject()) }
              .getOrElse {
                log.warn(
                    "Failed to transform Java module '{}' as Java model, falling back to Gradle model",
                    moduleMetadata.projectPath,
                    it,
                )
                transform(selectedGradleProject)
              }
    }
  }
}
