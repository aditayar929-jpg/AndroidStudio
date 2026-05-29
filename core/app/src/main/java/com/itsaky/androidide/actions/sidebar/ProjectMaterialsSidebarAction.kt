package com.itsaky.androidide.actions.sidebar

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.itsaky.androidide.R
import com.itsaky.androidide.repository.materials.ProjectMaterialsFragment
import kotlin.reflect.KClass

class ProjectMaterialsSidebarAction(context: Context, override val order: Int) : AbstractSidebarAction() {
  companion object { const val ID = "ide.editor.sidebar.projectMaterials" }

  override val id: String = ID
  override val fragmentClass: KClass<out Fragment> = ProjectMaterialsFragment::class

  init {
    label = "Project Materials"
    icon = ContextCompat.getDrawable(context, R.drawable.ic_folder)
  }
}
