package io.github.krosstack.kscope.gradle.model.fake

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePlugin

/** Stands in for the Android library plugin under its id: it applies the base plugin, as that plugin does. */
class FakeAndroidLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.plugins.apply(BasePlugin::class.java)
    }
}
