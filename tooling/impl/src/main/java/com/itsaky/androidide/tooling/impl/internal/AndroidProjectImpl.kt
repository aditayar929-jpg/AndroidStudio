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

package com.itsaky.androidide.tooling.impl.internal

import com.android.builder.model.v2.dsl.BuildType
import com.android.builder.model.v2.ide.AndroidArtifact
import com.android.builder.model.v2.ide.GraphItem
import com.android.builder.model.v2.ide.Library
import com.android.builder.model.v2.ide.LibraryType
import com.android.builder.model.v2.ide.ProjectType
import com.android.builder.model.v2.ide.Variant
import com.android.builder.model.v2.models.AndroidDsl
import com.android.builder.model.v2.models.AndroidProject
import com.android.builder.model.v2.models.BasicAndroidProject
import com.android.builder.model.v2.models.VariantDependencies
import com.android.builder.model.v2.models.VariantDependenciesAdjacencyList
import com.android.builder.model.v2.models.Versions
import com.android.builder.model.v2.models.ndk.NativeModule
import com.itsaky.androidide.builder.model.DefaultJavaCompileOptions
import com.itsaky.androidide.builder.model.DefaultLibrary
import com.itsaky.androidide.builder.model.DefaultSourceSetContainer
import com.itsaky.androidide.builder.model.DefaultViewBindingOptions
import com.itsaky.androidide.tooling.events.configuration.ManifestMergerParsedEvent
import com.itsaky.androidide.tooling.events.configuration.ManifestMergerParsedPermission
import com.itsaky.androidide.tooling.events.configuration.VariantContextChangedEvent
import com.itsaky.androidide.tooling.events.internal.DefaultOperationDescriptor
import com.itsaky.androidide.tooling.api.IAndroidProject
import com.itsaky.androidide.tooling.impl.Main
import com.itsaky.androidide.tooling.api.models.AndroidArtifactMetadata
import com.itsaky.androidide.tooling.api.models.AndroidLibraryDataModel
import com.itsaky.androidide.tooling.api.models.AndroidModuleType
import com.itsaky.androidide.tooling.api.models.AndroidProjectMetadata
import com.itsaky.androidide.tooling.api.models.AndroidProjectFlagsModel
import com.itsaky.androidide.tooling.api.models.AndroidProjectModelSnapshot
import com.itsaky.androidide.tooling.api.models.AndroidVariantMetadata
import com.itsaky.androidide.tooling.api.models.BasicAndroidVariantMetadata
import com.itsaky.androidide.tooling.api.models.BuildTypeMatrixModel
import com.itsaky.androidide.tooling.api.models.DependencyGraphModel
import com.itsaky.androidide.tooling.api.models.VariantDependencyAdjacencyModel
import com.itsaky.androidide.tooling.api.models.GraphNodeModel
import com.itsaky.androidide.tooling.api.models.ArtifactDependencyAdjacencyModel
import com.itsaky.androidide.tooling.api.models.ArtifactDependencyAdjacencyListModel
import com.itsaky.androidide.tooling.api.models.FlavorMatrixModel
import com.itsaky.androidide.tooling.api.models.GeneratedSourceModel
import com.itsaky.androidide.tooling.api.models.LibraryGraphEntry
import com.itsaky.androidide.tooling.api.models.LibraryCoordinate
import com.itsaky.androidide.tooling.api.models.ManifestBlameEntry
import com.itsaky.androidide.tooling.api.models.ManifestMergerReport
import com.itsaky.androidide.tooling.api.models.MergedPermissionSource
import com.itsaky.androidide.tooling.api.models.NativeVariantModel
import com.itsaky.androidide.tooling.api.models.NativeModuleModel
import com.itsaky.androidide.tooling.api.models.NativeAbiModel
import com.itsaky.androidide.tooling.api.models.ProjectInfoNodeModel
import com.itsaky.androidide.tooling.api.models.VariantDependencyAdjacencyListModel
import com.itsaky.androidide.tooling.api.models.ProjectMetadata
import com.itsaky.androidide.tooling.api.models.ProjectVariantResolutionModel
import com.itsaky.androidide.tooling.api.models.SourceSpaceModel
import com.itsaky.androidide.tooling.api.models.TestArtifactModel
import com.itsaky.androidide.tooling.api.models.TestSuiteTargetModel
import com.itsaky.androidide.tooling.api.models.TestedTargetVariantModel
import com.itsaky.androidide.tooling.api.models.TestSuiteAdjacencyModel
import com.itsaky.androidide.tooling.api.models.TestSuiteSourceAdjacencyModel
import com.itsaky.androidide.tooling.api.models.TestSuiteSourceDependenciesAdjacencyListModel
import com.itsaky.androidide.tooling.api.models.TestSuiteDependenciesAdjacencyListModel
import com.itsaky.androidide.tooling.api.models.DependencyEdgeModel
import com.itsaky.androidide.tooling.api.models.ArtifactDependenciesAdjacencyListModel
import com.itsaky.androidide.tooling.api.models.TestSuiteInfoModel
import com.itsaky.androidide.tooling.api.models.VariantCapabilitiesModel
import com.itsaky.androidide.tooling.api.models.VariantMatrixModel
import com.itsaky.androidide.tooling.api.models.VariantContextModel
import com.itsaky.androidide.tooling.api.models.params.StringParameter
import com.itsaky.androidide.tooling.api.util.AndroidModulePropertyCopier
import com.itsaky.androidide.tooling.api.util.AndroidModulePropertyCopier.copy
import com.itsaky.androidide.utils.AndroidPluginVersion
import com.itsaky.androidide.utils.capitalizeString
import java.io.File
import java.io.Serializable
import java.util.regex.Pattern
import java.util.concurrent.CompletableFuture
import org.gradle.tooling.model.GradleProject

/** @author Akash Yadav */
internal class AndroidProjectImpl(
    gradleProject: GradleProject,
    private val configuredVariant: String,
    private val basicAndroidProject: BasicAndroidProject,
    private val androidProject: AndroidProject,
    private val variantDependencies: VariantDependencies,
    private val variantDependenciesAdjacency: VariantDependenciesAdjacencyList?,
    private val versions: Versions,
    private val androidDsl: AndroidDsl,
    private val resolvedProjectVariants: Map<String, String>,
    private val resolvedProjectVariantDetails: List<ProjectVariantResolutionModel>,
    private val nativeModule: NativeModule?,
) : GradleProjectImpl(gradleProject), IAndroidProject, Serializable {

  private val serialVersionUID = 1L

  override fun getConfiguredVariant(): CompletableFuture<String> {
    return CompletableFuture.completedFuture(this.configuredVariant)
  }

  override fun getVariants(): CompletableFuture<List<BasicAndroidVariantMetadata>> {
    return CompletableFuture.supplyAsync {
      androidProject.variants.map {
        BasicAndroidVariantMetadata(it.name, it.mainArtifact.toMetadata(it.name))
      }
    }
  }

  private fun AndroidArtifact.toMetadata(variantName: String): AndroidArtifactMetadata {
    val sourceSpace = computeSourceSpace(variantName)
    return AndroidArtifactMetadata(
        name = variantName,
        applicationId = computeApplicationId(variantName),
        resGenTaskName = resGenTaskName ?: "",
        assembleTaskOutputListingFile = assembleTaskOutputListingFile,
        generatedResourceFolders = generatedResourceFolders,
        generatedAssetsFolders = generatedAssetsFolders,
        generatedSourceFolders = generatedSourceFolders,
        maxSdkVersion = maxSdkVersion,
        minSdkVersion = minSdkVersion.apiLevel,
        signingConfigName = signingConfigName ?: "",
        sourceGenTaskName = sourceGenTaskName ?: "",
        assembleTaskName = assembleTaskName ?: "", // Line 88 - ADD ?: ""
        classJars = classesFolders.filter { it.name.endsWith(".jar") },
        desugaredMethodsFiles = desugaredMethodsFiles,
        compileTaskName = compileTaskName ?: "", // Line 90 - ADD ?: ""
        mappingR8TextFile = mappingR8TextFile,
        mappingR8PartitionFile = mappingR8PartitionFile,
        targetSdkVersionOverride = targetSdkVersionOverride?.apiLevel ?: -1,
        sourceSpace = sourceSpace,
        dependencyGraph = computeDependencyGraph(),
        manifestMergerReport = parseManifestMergerReport(variantName),
    )
  }

  override fun getVariant(param: StringParameter): CompletableFuture<AndroidVariantMetadata?> {
    return CompletableFuture.supplyAsync {
      androidProject.variants.find { it.name == param.value }?.toMetadata()
    }
  }

  private fun Variant.toMetadata(): AndroidVariantMetadata {
    val moduleType = mapModuleType(basicAndroidProject.projectType)
    val buildTypeModel =
        basicAndroidProject.variants.firstOrNull { it.name == name }?.buildType?.let { bt ->
          androidDsl.buildTypes.find { it.name == bt }?.let {
            BuildTypeMatrixModel(
                name = it.name,
                isDebuggable = it.isDebuggable,
                isMinifyEnabled = it.isMinifyEnabled,
                signingConfig = it.signingConfig,
            )
          }
        }

    return AndroidVariantMetadata(
        name = name,
        mainArtifact = mainArtifact.toMetadata(name),
        otherArtifacts = mutableMapOf(),
        moduleType = moduleType,
        productFlavors =
            (basicAndroidProject.variants.firstOrNull { it.name == name }?.productFlavors ?: emptyList()).map { flavorName ->
              androidDsl.productFlavors.find { it.name == flavorName }?.let {
                FlavorMatrixModel(
                    name = it.name,
                    dimension = it.dimension,
                    minSdkVersion = it.minSdkVersion?.apiLevel,
                    targetSdkVersion = it.targetSdkVersion?.apiLevel,
                    resourceConfigurations = it.resourceConfigurations.toList(),
                )
              }
            }.filterNotNull(),
        buildType = buildTypeModel,
        capabilities =
            VariantCapabilitiesModel(
                isInstantAppCompatible = isInstantAppCompatible,
                runTestInSeparateProcess = runTestInSeparateProcess,
                desugaredMethods = desugaredMethods,
                experimentalProperties = experimentalProperties,
                deviceTestArtifacts =
                    deviceTestArtifacts.map {
                      TestArtifactModel(
                          name = it.key,
                          assembleTaskName = it.value.assembleTaskName,
                          compileTaskName = it.value.compileTaskName,
                      )
                    },
                hostTestArtifacts =
                    hostTestArtifacts.map {
                      TestArtifactModel(
                          name = it.key,
                          assembleTaskName = null,
                          compileTaskName = it.value.compileTaskName,
                      )
                    },
                testSuiteArtifacts =
                    testSuiteArtifacts.map {
                      TestArtifactModel(
                          name = it.key,
                          assembleTaskName = it.value.assembleTaskName,
                          compileTaskName = it.value.compileTaskName,
                      )
                    },
                testSuiteInfos =
                    testSuiteArtifacts.mapValues { entry ->
                      val info = entry.value.testInfo
                      TestSuiteInfoModel(
                          includedEngines = info.junitInfo.includedEngines,
                          targets =
                              info.targets.values.map { target ->
                                TestSuiteTargetModel(
                                    name = target.name,
                                    testTaskName = target.testTaskName,
                                    targetedDevices = target.targetedDevices,
                                )
                              },
                      )
                    },
                testedTargetVariant =
                    testedTargetVariant?.let {
                      TestedTargetVariantModel(
                          targetProjectPath = it.targetProjectPath,
                          targetVariant = it.targetVariant,
                      )
                    },
            ),
    )
  }

  private fun computeSourceSpace(variantName: String): SourceSpaceModel? {
    val sourceSet = sourceProviderForVariant(variantName)
    val generatedBase = File(gradleProject.buildDirectory, "generated")
    return sourceSet?.let { source ->
      SourceSpaceModel(
          javaDirectories = source.javaDirectories.toList(),
          kotlinDirectories = source.kotlinDirectories.toList(),
          manifestFiles = listOfNotNull(source.manifestFile),
          resourceDirectories = source.resDirectories?.toList() ?: emptyList(),
          assetsDirectories = source.assetsDirectories?.toList() ?: emptyList(),
          aidlDirectories = source.aidlDirectories?.toList() ?: emptyList(),
          resourcesDirectories = source.resourcesDirectories.toList(),
          renderscriptDirectories = source.renderscriptDirectories?.toList() ?: emptyList(),
          baselineProfileDirectories = source.baselineProfileDirectories?.toList() ?: emptyList(),
          keepRulesDirectories = source.keepRulesDirectories?.toList() ?: emptyList(),
          aarKeepRulesDirectories = source.aarKeepRulesDirectories?.toList() ?: emptyList(),
          shadersDirectories = source.shadersDirectories?.toList() ?: emptyList(),
          mlModelsDirectories = source.mlModelsDirectories?.toList() ?: emptyList(),
          jniLibsDirectories = source.jniLibsDirectories.toList(),
          customDirectories = source.customDirectories?.map { custom -> custom.directory } ?: emptyList(),
          generatedSources =
              GeneratedSourceModel(
                  annotationProcessorSources =
                      (listOf(File(generatedBase, "ap_generated_sources")) + androidProject.variants.firstOrNull { it.name == variantName }?.mainArtifact?.generatedSourceFolders.orEmpty())
                          .distinct()
                          .filter(File::exists),
                  buildConfigSources =
                      listOf(File(generatedBase, "source/buildConfig")).filter(File::exists),
                  viewBindingSources =
                      listOf(File(generatedBase, "view_binding_base_class_source_out")).filter(
                          File::exists
                      ),
                  dataBindingSources =
                      listOf(File(generatedBase, "data_binding_base_class_source_out")).filter(
                          File::exists
                      ),
              ),
      )
    }
  }

  private fun computeDependencyGraph(): DependencyGraphModel {
    val libraries = variantDependencies.libraries.values
    return DependencyGraphModel(
        artifactDependencies =
            libraries.mapNotNull {
              val artifactAddress = it.libraryInfo ?: return@mapNotNull null
              "${artifactAddress.group}:${artifactAddress.name}:${artifactAddress.version}"
            },
        localJarDependencies = emptyList(),
        aarExplodedFolders = emptyList(),
        aarClassesJars =
            libraries.mapNotNull { lib ->
              lib.artifact
                  ?.takeIf { lib.type == LibraryType.ANDROID_LIBRARY }
                  ?.parentFile
                  ?.resolve("jars/classes.jar")
                  ?.takeIf(File::exists)
            },
        projectDependencies =
            libraries.mapNotNull { lib ->
              lib.projectInfo?.let { "${it.buildId}:${it.projectPath}" }
            },
        testSuiteSourceDependencies =
            variantDependencies.testSuiteArtifacts.flatMap { (suiteName, suiteDeps) ->
              suiteDeps.sourcesDependencies.map { sourceDeps ->
                "$suiteName:${sourceDeps.name}(${sourceDeps.type})" to
                    sourceDeps.artifactDependencies.compileDependencies.map { it.key }
              }
            }.toMap(),
        variantAdjacency =
            VariantDependencyAdjacencyModel(
                mainArtifact = variantDependencies.mainArtifact.toAdjacency(),
                deviceTestArtifacts =
                    variantDependencies.deviceTestArtifacts.mapValues { (_, deps) -> deps.toAdjacency() },
                hostTestArtifacts =
                    variantDependencies.hostTestArtifacts.mapValues { (_, deps) -> deps.toAdjacency() },
            ),
        testSuiteAdjacency =
            variantDependencies.testSuiteArtifacts.map { (suiteName, suiteDeps) ->
              TestSuiteAdjacencyModel(
                  suiteName = suiteName,
                  sources =
                      suiteDeps.sourcesDependencies.map { sourceDeps ->
                        TestSuiteSourceAdjacencyModel(
                            sourceType = sourceDeps.type.name,
                            sourceName = sourceDeps.name,
                            dependencies = sourceDeps.artifactDependencies.toAdjacency(),
                        )
                      },
              )
            },
        variantAdjacencyList =
            variantDependenciesAdjacency?.let { adjacency ->
              VariantDependencyAdjacencyListModel(
                  mainArtifact = adjacency.mainArtifact.toAdjacencyList(),
                  deviceTestArtifacts =
                      adjacency.deviceTestArtifacts.mapValues { (_, deps) -> deps.toAdjacencyList() },
                  hostTestArtifacts =
                      adjacency.hostTestArtifacts.mapValues { (_, deps) -> deps.toAdjacencyList() },
              )
            },
        testSuiteAdjacencyLists =
            variantDependenciesAdjacency?.testSuiteArtifacts?.map { (suiteName, suiteDeps) ->
              TestSuiteDependenciesAdjacencyListModel(
                  suiteName = suiteName,
                  sourcesDependencies =
                      suiteDeps.sourcesDependencies.map { sourceDeps ->
                        TestSuiteSourceDependenciesAdjacencyListModel(
                            sourceType = sourceDeps.type.name,
                            sourceName = sourceDeps.name,
                            artifactDependencies = sourceDeps.artifactDependencies.toAdjacencyEdges(),
                        )
                      },
              )
            } ?: emptyList(),
        projectInfoNodes = buildProjectInfoNodes(libraries),
        libraries =
            libraries.map { lib ->
              LibraryGraphEntry(
                  key = lib.key,
                  type = lib.type.name,
                  artifact = lib.artifact,
                  lintJar = lib.lintJar,
                  srcJars = lib.srcJars,
                  docJar = lib.docJar,
                  projectPath = lib.projectInfo?.projectPath,
                  buildId = lib.projectInfo?.buildId,
                  attributes = lib.libraryInfo?.attributes ?: lib.projectInfo?.attributes ?: emptyMap(),
                  buildType = lib.libraryInfo?.buildType ?: lib.projectInfo?.buildType,
                  capabilities = lib.libraryInfo?.capabilities ?: lib.projectInfo?.capabilities ?: emptyList(),
                  isTestFixtures = lib.libraryInfo?.isTestFixtures ?: lib.projectInfo?.isTestFixtures ?: false,
                  productFlavors = lib.libraryInfo?.productFlavors ?: lib.projectInfo?.productFlavors ?: emptyMap(),
                  coordinate =
                      lib.libraryInfo?.let {
                        LibraryCoordinate(
                            group = it.group,
                            artifact = it.name,
                            version = it.version,
                        )
                      },
                  androidLibraryData =
                      lib.androidLibraryData?.let {
                        AndroidLibraryDataModel(
                            manifest = it.manifest,
                            compileJarFiles = it.compileJarFiles,
                            runtimeJarFiles = it.runtimeJarFiles,
                            resFolder = it.resFolder,
                            resStaticLibrary = it.resStaticLibrary,
                            assetsFolder = it.assetsFolder,
                            jniFolder = it.jniFolder,
                            aidlFolder = it.aidlFolder,
                            renderscriptFolder = it.renderscriptFolder,
                            proguardRules = it.proguardRules,
                            externalAnnotations = it.externalAnnotations,
                            publicResources = it.publicResources,
                            symbolFile = it.symbolFile,
                        )
                      },
              )
            },
    )
  }

  private fun buildProjectInfoNodes(libraries: Collection<Library>): List<ProjectInfoNodeModel> {
    return libraries
        .mapNotNull { lib ->
          val info = lib.projectInfo ?: return@mapNotNull null
          ProjectInfoNodeModel(
              buildId = info.buildId,
              projectPath = info.projectPath,
              selectedVariant = resolvedProjectVariants["${info.buildId}:${info.projectPath}"],
              attributes = info.attributes,
              buildType = info.buildType,
              productFlavors = info.productFlavors,
              capabilities = info.capabilities,
              isTestFixtures = info.isTestFixtures,
          )
        }
        .distinctBy { "${it.buildId}:${it.projectPath}" }
        .sortedWith(compareBy<ProjectInfoNodeModel>({ it.buildId }, { it.projectPath }))
  }

  private fun parseManifestMergerReport(variantName: String): ManifestMergerReport? {
    val report =
        File(
            gradleProject.buildDirectory,
            "outputs/logs/manifest-merger-$variantName-report.txt",
        )
    if (!report.exists()) return null
    val text = report.readText()

    val permissionPattern =
        Pattern.compile(
            "(uses-permission(?:-sdk-[0-9]+)?)#([a-zA-Z0-9_.]+).*?(ADDED|MERGED|REPLACED|REMOVED) from (.+?)(?:\n|$)",
            Pattern.DOTALL,
        )
    val permissionMatcher = permissionPattern.matcher(text)
    val mergedPermissions = mutableListOf<MergedPermissionSource>()
    while (permissionMatcher.find()) {
      mergedPermissions.add(
          MergedPermissionSource(
              permission = permissionMatcher.group(2),
              source = permissionMatcher.group(4).trim(),
              tagName = permissionMatcher.group(1),
          )
      )
    }

    val genericPattern =
        Pattern.compile(
            "(<[a-zA-Z0-9._:-]+(?:\\s+[^>]*)?>|[a-zA-Z0-9._:-]+#[a-zA-Z0-9._:-]+).*?(ADDED|MERGED|REPLACED|REMOVED) from (.+?)(?:\\n|$)",
            Pattern.DOTALL,
        )
    val genericMatcher = genericPattern.matcher(text)
    val blameEntries = mutableListOf<ManifestBlameEntry>()
    while (genericMatcher.find()) {
      val qualified = genericMatcher.group(1).trim()
      val tagName =
          qualified.substringAfter('<').substringBefore('#').substringBefore(' ').removeSuffix(">")
      blameEntries.add(
          ManifestBlameEntry(
              tagName = if (tagName.isBlank()) "unknown" else tagName,
              qualifiedName = qualified,
              action = genericMatcher.group(2),
              source = genericMatcher.group(3).trim(),
          )
      )
    }

    return ManifestMergerReport(
        reportFile = report,
        mergedPermissions = mergedPermissions,
        blameEntries = blameEntries,
    )
  }

  override fun getBootClasspaths(): CompletableFuture<Collection<File>> {
    return CompletableFuture.supplyAsync { basicAndroidProject.bootClasspath }
  }

  override fun getLibraryMap(): CompletableFuture<Map<String, DefaultLibrary>> {
    return CompletableFuture.supplyAsync {
      val seen = HashMap<String, DefaultLibrary>()
      val compileDependencies = variantDependencies.mainArtifact.compileDependencies
      val libraries = variantDependencies.libraries
      for (dependency in compileDependencies) {
        seen[dependency.key] ?: fillLibrary(dependency, libraries, seen)
      }
      seen
    }
  }

  private fun fillLibrary(
      item: GraphItem,
      libraries: Map<String, Library>,
      seen: HashMap<String, DefaultLibrary>,
  ): DefaultLibrary? {

    val lib = libraries[item.key] ?: return null
    val library = copy(lib)

    for (dependency in item.dependencies) {
      val dep = fillLibrary(dependency, libraries, seen)!!
      library.dependencies.add(dep.key)
    }

    seen[item.key] = library

    return library
  }

  override fun getMainSourceSet(): CompletableFuture<DefaultSourceSetContainer?> {
    return CompletableFuture.supplyAsync {
      basicAndroidProject.mainSourceSet?.let(AndroidModulePropertyCopier::copy)
    }
  }

  override fun getLintCheckJars(): CompletableFuture<List<File>> {
    return CompletableFuture.supplyAsync { androidProject.lintChecksJars }
  }

  private fun getClassesJar(): File {
    val intermediatesDir = File(gradleProject.buildDirectory, IAndroidProject.FD_INTERMEDIATES)
    val direct =
        File(
            intermediatesDir,
            "compile_library_classes_jar/$configuredVariant/classes.jar",
        )
    if (direct.exists()) return direct

    val fallback =
        intermediatesDir
            .resolve("compile_library_classes_jar")
            .walkTopDown()
            .firstOrNull { candidate ->
              candidate.isFile &&
                  candidate.name == "classes.jar" &&
                  candidate.path.contains(configuredVariant, ignoreCase = true)
            }
    return fallback ?: direct
  }

  override fun getClasspaths(): CompletableFuture<List<File>> {
    return CompletableFuture.supplyAsync {
      mutableListOf<File>().apply {
        add(getClassesJar())
        getVariant(StringParameter(configuredVariant))
            .get()
            ?.mainArtifact
            ?.classJars
            ?.let(this::addAll)
      }
    }
  }

  override fun getMetadata(): CompletableFuture<ProjectMetadata> {
    return CompletableFuture.supplyAsync {
      val gradleMetadata = super.getMetadata().get()

      val viewBindingOptions =
          androidProject.viewBindingOptions?.let(AndroidModulePropertyCopier::copy)
              ?: DefaultViewBindingOptions()

      AndroidProjectMetadata(
          gradleMetadata,
          basicAndroidProject.projectType,
          copy(androidProject.flags),
          androidProject.javaCompileOptions?.let { copy(it) }
              ?: DefaultJavaCompileOptions(), // Line 174 - ADD ?: DefaultJavaCompileOptions()
          viewBindingOptions,
          androidProject.resourcePrefix,
          androidProject.namespace,
          androidProject.androidTestNamespace,
          androidProject.testFixturesNamespace,
          computeInterpretedFlags(),
          computeProjectSnapshot(),
          getClassesJar(),
      )
          .also { metadata -> metadata.modelSnapshot?.let(::publishModelDerivedEvents) }
    }
  }

  private fun publishModelDerivedEvents(snapshot: AndroidProjectModelSnapshot) {
    val client = Main.client ?: return
    val now = System.currentTimeMillis()
    val descriptor =
        DefaultOperationDescriptor(
            name = "android-model-derived-event",
            displayName = "Android model derived event",
        )

    snapshot.variantContexts[configuredVariant]?.let { context ->
      client.onProgressEvent(
          VariantContextChangedEvent(
              projectPath = gradleProject.path,
              oldVariant = null,
              newVariant = context.variantName,
              clearedGeneratedSources = context.clearOnSwitchGeneratedSources.isNotEmpty(),
              displayName = "Variant context changed: ${context.variantName}",
              eventTime = now,
              descriptor = descriptor,
          )
      )
    }

    androidProject.variants
        .firstOrNull { it.name == configuredVariant }
        ?.mainArtifact
        ?.toMetadata(configuredVariant)
        ?.manifestMergerReport
        ?.let { report ->
          client.onProgressEvent(
              ManifestMergerParsedEvent(
                  projectPath = gradleProject.path,
                  variant = configuredVariant,
                  reportFile = report.reportFile,
                  permissions =
                      report.mergedPermissions.map {
                        ManifestMergerParsedPermission(permission = it.permission, source = it.source)
                      },
                  displayName = "Manifest merger parsed: $configuredVariant",
                  eventTime = now,
                  descriptor = descriptor,
              )
          )
        }
  }

  private fun computeInterpretedFlags(): AndroidProjectFlagsModel {
    val interpreted =
        com.android.builder.model.v2.ide.AndroidGradlePluginProjectFlags.BooleanFlag.values()
            .associate { flag ->
              flag.name to flag.getValue(androidProject.flags, null)
            }
    return AndroidProjectFlagsModel(values = interpreted)
  }

  private fun computeProjectSnapshot(): AndroidProjectModelSnapshot {
    val moduleType = mapModuleType(basicAndroidProject.projectType)

    val buildTypes =
        androidDsl.buildTypes.map {
          BuildTypeMatrixModel(
              name = it.name,
              isDebuggable = it.isDebuggable,
              isMinifyEnabled = it.isMinifyEnabled,
              signingConfig = it.signingConfig,
          )
        }

    val flavors =
        androidDsl.productFlavors.map {
          FlavorMatrixModel(
              name = it.name,
              dimension = it.dimension,
              minSdkVersion = it.minSdkVersion?.apiLevel,
              targetSdkVersion = it.targetSdkVersion?.apiLevel,
              resourceConfigurations = it.resourceConfigurations.toList(),
          )
        }

    return AndroidProjectModelSnapshot(
        agpVersion = versions.agp,
        modelVersion = versions.versions[com.android.builder.model.v2.models.Versions.MODEL_PRODUCER]?.humanReadable ?: "",
        moduleType = moduleType,
        buildTypes = buildTypes,
        productFlavors = flavors,
        availableVariants = basicAndroidProject.variants.map { it.name },
        variantMatrix =
            basicAndroidProject.variants.map {
              VariantMatrixModel(
                  name = it.name,
                  buildType = it.buildType,
                  productFlavors = it.productFlavors,
                  deviceTestArtifacts = it.deviceTestArtifacts.keys.toList(),
                  hostTestArtifacts = it.hostTestArtifacts.keys.toList(),
                  testSuiteArtifacts = it.testSuiteArtifacts.keys.toList(),
                  hasTestFixturesArtifact = it.testFixturesArtifact != null,
              )
            },
        variantContexts =
            androidProject.variants.associate { variant ->
              val artifactMetadata = variant.mainArtifact.toMetadata(variant.name)
              variant.name to
                  VariantContextModel(
                      variantName = variant.name,
                      classpath = artifactMetadata.classJars,
                      generatedSources =
                          artifactMetadata.sourceSpace?.generatedSources
                              ?: GeneratedSourceModel(
                                  annotationProcessorSources = emptyList(),
                                  buildConfigSources = emptyList(),
                                  viewBindingSources = emptyList(),
                                  dataBindingSources = emptyList(),
                              ),
                      clearOnSwitchGeneratedSources =
                          artifactMetadata.generatedSourceFolders.toList(),
                      clearOnSwitchSourceDirectories =
                          sourceProviderDirectoriesExcept(variant.name),
                  )
            },
        resolvedProjectVariants = resolvedProjectVariants,
        resolvedProjectVariantDetails = resolvedProjectVariantDetails,
        projectInfoNodes = buildProjectInfoNodes(variantDependencies.libraries.values),
        nativeModule = nativeModule?.let { module ->
          NativeModuleModel(
              name = module.name,
              nativeBuildSystem = module.nativeBuildSystem.name,
              ndkVersion = module.ndkVersion,
              defaultNdkVersion = module.defaultNdkVersion,
              externalNativeBuildFile = module.externalNativeBuildFile,
              variants =
                  module.variants.map { variant ->
                    NativeVariantModel(
                        name = variant.name,
                        abis =
                            variant.abis.map { abi ->
                              NativeAbiModel(
                                  name = abi.name,
                                  sourceFlagsFile = abi.sourceFlagsFile,
                                  symbolFolderIndexFile = abi.symbolFolderIndexFile,
                                  buildFileIndexFile = abi.buildFileIndexFile,
                                  additionalProjectFilesIndexFile = abi.additionalProjectFilesIndexFile,
                              )
                            },
                    )
                  },
          )
        },
    )
  }

  private fun mapModuleType(type: ProjectType): AndroidModuleType =
      when (type) {
        ProjectType.APPLICATION -> AndroidModuleType.APPLICATION
        ProjectType.LIBRARY -> AndroidModuleType.LIBRARY
        ProjectType.TEST -> AndroidModuleType.TEST
        ProjectType.DYNAMIC_FEATURE -> AndroidModuleType.DYNAMIC_FEATURE
        else -> AndroidModuleType.UNKNOWN
      }

  private fun AndroidArtifact.computeApplicationId(variantName: String): String? {
    val minAgpForAppId = AndroidPluginVersion(7, 4, 0)
    return if (minAgpForAppId <= AndroidPluginVersion.parse(versions.agp)) {
      applicationId
    } else {
      computeApplicationIdLegacy(variantName)
    }
  }

  // Adapted from the following :
  // https://cs.android.com/android-studio/platform/tools/base/+/mirror-goog-studio-main:build-system/gradle-core/src/main/java/com/android/build/gradle/internal/core/dsl/impl/ComponentDslInfoImpl.kt;drc=6a5551bdea55c0c991f1ccf1e3f8f6f3d2cd2cb7;l=107
  // https://cs.android.com/android-studio/platform/tools/base/+/mirror-goog-studio-main:build-system/gradle-core/src/main/java/com/android/build/gradle/internal/core/dsl/impl/VariantDslInfoImpl.kt;drc=d44f5b98cd5530eceb230e0d151ad96c4277f78d;l=109

  protected fun computeApplicationIdLegacy(variantName: String): String {
    val basicVariant = basicAndroidProject.variants.firstOrNull { it.name == variantName }
    val buildType =
        basicVariant?.buildType?.let { buildTypeName ->
          androidDsl.buildTypes.find { buildType -> buildType.name == buildTypeName }
        }!!

    val appIdFromFlavor =
        if (basicAndroidProject.projectType == ProjectType.APPLICATION) {
          androidDsl.productFlavors
              .find { flavor ->
                "${flavor.name}${buildType.name.capitalizeString()}" == variantName
              }
              ?.applicationId
        } else {
          androidDsl.defaultConfig.applicationId
        }

    return if (appIdFromFlavor == null) {
      // No appId value set from DSL; use the namespace value from the DSL.
      "${androidProject.namespace}${computeApplicationIdSuffix(variantName, buildType)}"
    } else {
      // use value from flavors/defaultConfig
      // needed to make nullability work in kotlinc
      val finalAppIdFromFlavors: String = appIdFromFlavor
      "$finalAppIdFromFlavors${computeApplicationIdSuffix(variantName, buildType)}"
    }
  }

  /**
   * Combines all the appId suffixes into a single one.
   *
   * The suffixes are separated by '.' whether their first char is a '.' or not.
   */
  protected fun computeApplicationIdSuffix(variantName: String, buildType: BuildType): String {
    // for the suffix we combine the suffix from all the flavors. However, we're going to
    // want the higher priority one to be last.
    val suffixes = mutableListOf<String>()
    androidDsl.defaultConfig.applicationIdSuffix?.let { suffixes.add(it) }

    if (basicAndroidProject.projectType == ProjectType.APPLICATION) {

      val flavorSuffix =
          androidDsl.productFlavors
              .find { flavor ->
                "${flavor.name}${buildType.name.capitalizeString()}" == variantName
              }
              ?.applicationIdSuffix

      flavorSuffix?.also { suffixes.add(flavorSuffix) }

      // then we add the build type after.
      buildType.applicationIdSuffix?.also { suffixes.add(it) }
    }

    val nonEmptySuffixes = suffixes.filter { it.isNotEmpty() }
    return if (nonEmptySuffixes.isNotEmpty()) {
      ".${nonEmptySuffixes.joinToString(separator = ".", transform = { it.removePrefix(".") })}"
    } else {
      ""
    }
  }


  private fun sourceProviderForVariant(variantName: String) =
      basicAndroidProject.variants.firstOrNull { it.name == variantName }?.mainArtifact?.variantSourceProvider

  private fun sourceProviderDirectoriesExcept(activeVariantName: String): List<File> =
      basicAndroidProject.variants
          .filterNot { it.name == activeVariantName }
          .flatMap { it.mainArtifact.variantSourceProvider?.allDirectories().orEmpty() }
          .distinct()

  private fun com.android.builder.model.v2.ide.SourceProvider.allDirectories(): List<File> =
      javaDirectories +
          kotlinDirectories +
          resourcesDirectories +
          (resDirectories ?: emptyList()) +
          (assetsDirectories ?: emptyList())

  private fun com.android.builder.model.v2.ide.ArtifactDependencies.toAdjacency(): ArtifactDependencyAdjacencyModel =
      ArtifactDependencyAdjacencyModel(
          compile = compileDependencies.map { GraphNodeModel(it.key, null, it.dependencies.map { dep -> dep.key }) },
          runtime = runtimeDependencies?.map { GraphNodeModel(it.key, null, it.dependencies.map { dep -> dep.key }) } ?: emptyList(),
      )

  private fun com.android.builder.model.v2.ide.ArtifactDependenciesAdjacencyList.toAdjacencyList(): ArtifactDependencyAdjacencyListModel =
      ArtifactDependencyAdjacencyListModel(
          compile = ArtifactDependenciesAdjacencyListModel(edges = compileDependencies.map { DependencyEdgeModel(it.from, it.to) }),
          runtime = ArtifactDependenciesAdjacencyListModel(edges = runtimeDependencies?.map { DependencyEdgeModel(it.from, it.to) } ?: emptyList()),
      )

  private fun com.android.builder.model.v2.ide.ArtifactDependenciesAdjacencyList.toAdjacencyEdges(): ArtifactDependenciesAdjacencyListModel =
      ArtifactDependenciesAdjacencyListModel(
          edges =
              (compileDependencies + (runtimeDependencies ?: emptyList()))
                  .map { DependencyEdgeModel(it.from, it.to) }
                  .distinct(),
      )

}
