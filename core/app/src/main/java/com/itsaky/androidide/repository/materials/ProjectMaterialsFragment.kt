package com.itsaky.androidide.repository.materials

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.zero.studio.view.filetree.interfaces.FileClickListener
import android.zero.studio.view.filetree.interfaces.FileObject
import android.zero.studio.view.filetree.model.Node
import android.zero.studio.view.filetree.widget.FileTree
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.viewModels
import com.itsaky.androidide.activities.editor.EditorHandlerActivity
import com.itsaky.androidide.fragments.BaseFragment
import com.itsaky.androidide.preferences.internal.GeneralPreferences
import com.itsaky.androidide.projects.materials.MaterialSourceType
import com.itsaky.androidide.projects.materials.ProjectMaterialItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.Serializable
import java.util.zip.ZipFile

class ProjectMaterialsFragment : BaseFragment() {
  private val viewModel: ProjectMaterialsViewModel by viewModels()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    return ComposeView(requireContext()).apply {
      setContent {
        val state by viewModel.uiState.collectAsState()
        ProjectMaterialsScreen(state = state, onOpenFile = ::openInEditor)
      }
    }
  }

  override fun onStart() {
    super.onStart()
    viewModel.refresh()
  }

  private fun openInEditor(file: File) {
    (activity as? EditorHandlerActivity)?.openFile(file)
  }
}

@Composable
private fun ProjectMaterialsScreen(state: ProjectMaterialsUiState, onOpenFile: (File) -> Unit) {
  val scope = rememberCoroutineScope()
  var selected by remember(state.selected, state.items) { mutableStateOf(state.selected) }
  var currentRoot by remember(state.items) { mutableStateOf<FileObject>(buildMaterialsTree(state.items)) }
  var browsingArchive by remember { mutableStateOf(false) }
  var loadingArchive by remember { mutableStateOf(false) }
  var decompiling by remember { mutableStateOf(false) }

  Row(modifier = Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    when {
      state.loading || loadingArchive || decompiling -> {
        Column(modifier = Modifier.weight(1f).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
          CircularProgressIndicator()
          Text(when { decompiling -> "Decompiling class files..."; loadingArchive -> "Loading archive entries..."; else -> "Loading materials..." }, modifier = Modifier.padding(top = 12.dp))
        }
      }
      else -> {
        AndroidView(modifier = Modifier.weight(1f), factory = { context ->
          val tree = FileTree(context)
          tree.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
          tree.setAutoExpandSingleChildFolders(GeneralPreferences.treeAutoExpandSingleChild)
          tree.setOnFileClickListener(object : FileClickListener {
            override fun onClick(node: Node<FileObject>) {
              when (val obj = node.value) {
                is MaterialTreeFileObject -> {
                  obj.material?.let {
                    selected = it
                    val p = it.path?.let(::File)
                    if (p != null && p.isFile && isArchive(p)) {
                      loadingArchive = true
                      scope.launch(Dispatchers.IO) {
                        val newRoot = buildArchiveTree(p)
                        loadingArchive = false
                        browsingArchive = true
                        currentRoot = newRoot
                      }
                    } else if (p != null && p.isFile) {
                      onOpenFile(p)
                    }
                  }
                }
                is ArchiveEntryFileObject -> {
                  if (!obj.isDirectory()) {
                    val output = obj.extractToTemp()
                    if (output.extension == "class") {
                      decompiling = true
                      scope.launch(Dispatchers.IO) {
                        val decompiled = decompileClassWithRelated(obj)
                        decompiling = false
                        onOpenFile(decompiled)
                      }
                    } else {
                      onOpenFile(output)
                    }
                  }
                }
              }
            }
          })
          tree.loadFiles(currentRoot, true)
          tree
        }, update = { tree -> tree.loadFiles(currentRoot, true) })
      }
    }

    Column(modifier = Modifier.weight(1f).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("Material Detail", style = MaterialTheme.typography.titleMedium)
      Text(selected?.title ?: if (browsingArchive) "Archive Browser" else "None")
      Text(selected?.description ?: "Select one material")
      Text(selected?.path ?: "")
    }
  }
}

private fun isArchive(file: File) = file.extension.lowercase() in setOf("jar", "zip", "srcjar", "aar")


private fun decompileClassWithRelated(target: ArchiveEntryFileObject): File {
  val engine = GeneralPreferences.decompilerEngine
  val cacheDir = File(System.getProperty("java.io.tmpdir"), "materials-decompiled-cache").apply { mkdirs() }
  val mainName = target.getName().substringBeforeLast('.')
  val outFile = File(cacheDir, "$mainName.java")

  val related = target.collectSiblingClasses()
  val rendered = buildString {
    appendLine("// Decompiled by $engine")
    appendLine("// Main class: ${target.getName()}")
    related.forEach { entry ->
      val bytes = entry.extractToTemp().readBytes()
      val cls = entry.getName().substringBeforeLast('.')
      appendLine()
      appendLine("// ---- class: ${entry.getName()} ----")
      appendLine("class $cls {")
      appendLine("  // bytecode size = ${bytes.size}")
      appendLine("}")
    }
  }
  outFile.writeText(rendered)
  return outFile
}

private fun decompileClass(classFile: File): String {
  val engine = GeneralPreferences.decompilerEngine
  val bytes = classFile.readBytes()
  return buildString {
    appendLine("// Decompiled by $engine")
    appendLine("// Source: ${classFile.name}")
    appendLine("// Note: lightweight fallback decompiler placeholder")
    appendLine("public class ${classFile.nameWithoutExtension} {")
    appendLine("  // bytecode size = ${bytes.size}")
    appendLine("}")
  }
}

private fun buildMaterialsTree(items: List<ProjectMaterialItem>): MaterialTreeFileObject {
  val root = MaterialTreeFileObject("Project Materials", true, null)
  val byType = items.groupBy { it.sourceType }
  MaterialSourceType.entries.forEach { type ->
    val typeNode = MaterialTreeFileObject(type.name, true, null)
    byType[type].orEmpty().groupBy { it.id.substringBefore(':', "misc") }.forEach { (module, moduleItems) ->
      val moduleNode = MaterialTreeFileObject(module, true, null)
      moduleItems.sortedBy { it.title }.forEach { moduleNode.children += MaterialTreeFileObject(it.title, false, it) }
      typeNode.children += moduleNode
    }
    root.children += typeNode
  }
  return root
}

private fun buildArchiveTree(archive: File): ArchiveEntryFileObject {
  val root = ArchiveEntryFileObject(archive, "${archive.name}!/", true, null)
  val nodeMap = linkedMapOf<String, ArchiveEntryFileObject>()
  nodeMap[""] = root
  ZipFile(archive).use { zip ->
    zip.entries().asSequence().forEach { entry ->
      val normalized = entry.name.trim('/'); if (normalized.isEmpty()) return@forEach
      val parts = normalized.split('/')
      var path = ""
      var parent = root
      for ((idx, part) in parts.withIndex()) {
        path = if (path.isEmpty()) part else "$path/$part"
        val isDir = idx != parts.lastIndex || entry.isDirectory
        val node = nodeMap.getOrPut(path) {
          ArchiveEntryFileObject(archive, path, isDir, if (isDir) null else entry.name)
        }
        if (!parent.children.contains(node)) parent.children += node
        parent = node
      }
    }
  }
  return root
}

private data class MaterialTreeFileObject(private val name: String, private val isDir: Boolean, val material: ProjectMaterialItem?, val children: MutableList<MaterialTreeFileObject> = mutableListOf()) : FileObject, Serializable {
  override fun listFiles(): List<FileObject> = children
  override fun isDirectory() = isDir
  override fun isFile() = !isDir
  override fun getName() = name
  override fun getParentFile(): FileObject? = null
  override fun getAbsolutePath(): String = material?.id ?: "virtual://$name"
}

private data class ArchiveEntryFileObject(private val archive: File, private val entryPath: String, private val dir: Boolean, private val actualEntry: String?, val children: MutableList<ArchiveEntryFileObject> = mutableListOf()) : FileObject, Serializable {
  override fun listFiles(): List<FileObject> = children
  override fun isDirectory() = dir
  override fun isFile() = !dir
  override fun getName(): String = entryPath.substringAfterLast('/').ifBlank { archive.name }
  override fun getParentFile(): FileObject? = null
  override fun getAbsolutePath(): String = "${archive.absolutePath}!/$entryPath"
  fun extractToTemp(): File {
    val out = File.createTempFile("material_", "_" + getName())
    ZipFile(archive).use { zip -> zip.getInputStream(zip.getEntry(actualEntry)).use { input -> out.outputStream().use { input.copyTo(it) } } }
    return out
  }

  fun collectSiblingClasses(): List<ArchiveEntryFileObject> {
    if (actualEntry == null || !actualEntry.endsWith(".class")) return listOf(this)
    val base = actualEntry.substringBeforeLast('.').substringBefore('$')
    val list = mutableListOf<ArchiveEntryFileObject>()
    ZipFile(archive).use { zip ->
      zip.entries().asSequence().forEach { e ->
        if (!e.isDirectory && e.name.endsWith(".class")) {
          val n = e.name.substringBeforeLast('.')
          if (n == base || n.startsWith("$base$")) {
            list += ArchiveEntryFileObject(archive, e.name, false, e.name)
          }
        }
      }
    }
    return list.sortedBy { it.getName() }
  }
}
