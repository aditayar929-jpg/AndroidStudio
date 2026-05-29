package com.itsaky.androidide.projects.materials

import com.itsaky.androidide.builder.model.DefaultLibrary
import com.itsaky.androidide.lookup.Lookup
import com.itsaky.androidide.projects.builder.BuildService
import com.itsaky.androidide.tooling.api.IProject
import com.itsaky.androidide.tooling.api.ProjectType
import com.itsaky.androidide.tooling.api.models.params.StringParameter
import java.io.File

class ProjectMaterialsRepository {

  fun loadMaterials(): List<ProjectMaterialItem> {
    val proxy = Lookup.getDefault().lookup(BuildService.KEY_PROJECT_PROXY) ?: return emptyList()
    val items = LinkedHashMap<String, ProjectMaterialItem>()

    fun add(item: ProjectMaterialItem) {
      items.putIfAbsent(item.id, item)
    }

    val projects = runCatching { proxy.getProjects().get() }.getOrDefault(emptyList())

    projects.forEach { meta ->
      runCatching { proxy.selectProject(StringParameter(meta.projectPath)).get() }
      add(
          ProjectMaterialItem(
              id = "module:${meta.projectPath}",
              title = meta.name ?: meta.projectPath,
              sourceType = MaterialSourceType.GRADLE_TOOLING_API,
              apiName = "org.gradle.tooling.model.GradleProject",
              description = "Module from Tooling API model path=${meta.projectPath}",
              path = meta.projectDir.absolutePath,
          ))
      addFileItem(add = ::add, file = meta.buildDir, label = "Build directory")
      collectBuildCacheMaterials(meta.projectPath, meta.buildDir, ::add)

      when (runCatching { proxy.getType().get() }.getOrNull()) {
        ProjectType.Android -> collectAndroidMaterials(proxy, ::add)
        ProjectType.Java -> collectJavaMaterials(proxy, ::add)
        else -> collectGradleMaterials(proxy, ::add)
      }
    }

    return items.values.toList()
  }

  private fun collectGradleMaterials(proxy: IProject, add: (ProjectMaterialItem) -> Unit) {
    val gradle = runCatching { proxy.asGradleProject() }.getOrNull() ?: return
    val buildEnv = runCatching { gradle.getBuildEnvironment().get() }.getOrNull()
    val gradleBuild = runCatching { gradle.getGradleBuild().get() }.getOrNull()

    buildEnv?.java?.javaHome?.let {
      addFileItem(add, it, "JDK Home")
      collectJdkAndToolSources(it, add)
    }
    buildEnv?.gradle?.gradleUserHome?.let {
      addFileItem(add, it, "Gradle user home")
      collectGradleCachedSources(it, add)
    }
    gradleBuild?.includedBuildIds?.forEach { included ->
      add(ProjectMaterialItem("included:$included", included, MaterialSourceType.GRADLE_TOOLING_API, "GradleBuildModel", "Included build id"))
    }
  }

  private fun collectAndroidMaterials(proxy: IProject, add: (ProjectMaterialItem) -> Unit) {
    val android = runCatching { proxy.asAndroidProject() }.getOrNull() ?: return

    runCatching { android.getBootClasspaths().get() }.getOrDefault(emptyList()).forEach {
      addFileItem(add, it, "Android boot classpath")
    }

    runCatching { android.getLintCheckJars().get() }.getOrDefault(emptyList()).forEach {
      addFileItem(add, it, "Lint checks jar")
    }

    runCatching { android.getLibraryMap().get() }.getOrDefault(emptyMap()).forEach { (key, lib) ->
      collectLibraryFiles(key, lib, add)
    }


    runCatching { android.getMainSourceSet().get() }.getOrNull()?.let { main ->
      addFileItem(add, main.sourceProvider.manifestFile, "Android manifest source")
      main.sourceProvider.javaDirectories.forEach { addFileItem(add, it, "Android Java source dir") }
      main.sourceProvider.kotlinDirectories.forEach { addFileItem(add, it, "Android Kotlin source dir") }
      main.sourceProvider.resDirectories?.forEach { addFileItem(add, it, "Android res source dir") }
      main.sourceProvider.assetsDirectories?.forEach { addFileItem(add, it, "Android assets source dir") }
      main.sourceProvider.aidlDirectories?.forEach { addFileItem(add, it, "Android aidl source dir") }
    }
  }

  private fun collectJavaMaterials(proxy: IProject, add: (ProjectMaterialItem) -> Unit) {
    val java = runCatching { proxy.asJavaProject() }.getOrNull() ?: return

    runCatching { java.getContentRoots().get() }.getOrDefault(emptyList()).forEach { root ->
      root.sourceDirectories.forEach { addFileItem(add, it.directory, "Java source dir") }
      root.testDirectories.forEach { addFileItem(add, it.directory, "Java test source dir") }
    }

    runCatching { java.getDependencies().get() }.getOrDefault(emptyList()).forEach { dep ->
      dep.jarFile?.let { addFileItem(add, it, "Java dependency jar") }
    }
  }

  private fun collectLibraryFiles(key: String, lib: DefaultLibrary, add: (ProjectMaterialItem) -> Unit) {
    lib.artifact?.let { addFileItem(add, it, "Library artifact [$key]") }
    if (lib.artifact?.extension?.equals("aar", true) == true) {
      addFileItem(add, lib.artifact!!, "AAR package [$key]")
    }
    lib.srcJar?.let { addFileItem(add, it, "Library source jar [$key]") }
    lib.docJar?.let { addFileItem(add, it, "Library docs jar [$key]") }
    lib.samplesJar?.let { addFileItem(add, it, "Library samples jar [$key]") }
    lib.lintJar?.let { addFileItem(add, it, "Library lint jar [$key]") }
    lib.srcJars.forEach { addFileItem(add, it, "Library source jar [$key]") }

    lib.androidLibraryData?.let { androidLib ->
      addFileItem(add, androidLib.manifest, "AAR manifest [$key]")
      addFileItem(add, androidLib.resFolder, "AAR res folder [$key]")
      addFileItem(add, androidLib.assetsFolder, "AAR assets folder [$key]")
      addFileItem(add, androidLib.symbolFile, "AAR R symbols [$key]")
      androidLib.compileJarFiles.forEach { addFileItem(add, it, "AAR compile jar [$key]") }
      androidLib.runtimeJarFiles.forEach { addFileItem(add, it, "AAR runtime jar [$key]") }
    }
  }



  private fun collectGradleCachedSources(gradleUserHome: File, add: (ProjectMaterialItem) -> Unit) {
    val cacheRoot = File(gradleUserHome, "caches/modules-2/files-2.1")
    if (!cacheRoot.exists()) return

    cacheRoot.walkTopDown()
        .filter { it.isFile && (it.name.endsWith("-sources.jar") || it.name.endsWith("-javadoc.jar")) }
                .forEach { addFileItem(add, it, "Gradle dependency source/doc archive") }
  }

  private fun collectJdkAndToolSources(javaHome: File, add: (ProjectMaterialItem) -> Unit) {
    val candidates = listOf(
        File(javaHome, "src.zip"),
        File(javaHome, "lib/src.zip"),
        File(javaHome, "lib/source.zip"),
    )
    candidates.filter { it.exists() }.forEach { addFileItem(add, it, "JDK API source archive") }

    val kotlinHome = System.getenv("KOTLIN_HOME")?.let(::File)
    kotlinHome?.takeIf { it.exists() }?.let {
      addFileItem(add, it, "Kotlin home")
      File(it, "lib").takeIf(File::exists)?.listFiles()?.forEach { child ->
        if (child.name.endsWith("-sources.jar") || child.name == "kotlin-stdlib-sources.jar") {
          addFileItem(add, child, "Kotlin API source archive")
        }
      }
    }
  }


  private fun collectBuildCacheMaterials(modulePath: String, buildDir: File, add: (ProjectMaterialItem) -> Unit) {
    if (!buildDir.exists() || !buildDir.isDirectory) return
    val moduleKey = modulePath.replace(':', '_').ifBlank { "root" }
    buildDir.walkTopDown().forEach { f ->
      if (f == buildDir) return@forEach
      val rel = f.relativeTo(buildDir).path
      add(
          ProjectMaterialItem(
              id = "buildcache:${moduleKey}:${f.absolutePath}",
              title = rel,
              sourceType = MaterialSourceType.BUILD_CACHE,
              apiName = if (f.isDirectory) "directory" else f.extension.ifBlank { "file" },
              description = "Build cache/output resource for module $modulePath",
              path = f.absolutePath,
          ))
    }
  }

  private fun addFileItem(add: (ProjectMaterialItem) -> Unit, file: File, label: String) {
    add(
        ProjectMaterialItem(
            id = "file:${file.absolutePath}",
            title = file.name.ifBlank { file.absolutePath },
            sourceType = MaterialSourceType.PROJECT_FILE,
            apiName = file.extension.ifBlank { "file" },
            description = label,
            path = file.absolutePath,
        ))
  }
}
