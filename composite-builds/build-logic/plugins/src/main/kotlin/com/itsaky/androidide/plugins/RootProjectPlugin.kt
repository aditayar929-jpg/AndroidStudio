package com.itsaky.androidide.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project

class RootProjectPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    require(target == target.rootProject) { "This plugin must be applied to the root project." }
    // Root project plugin configuration can be added here if needed
  }
}
