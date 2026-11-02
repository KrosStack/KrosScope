package acme

import org.gradle.api.Plugin
import org.gradle.api.Project

class AppConventionPlugin : Plugin<Project> {
    override fun apply(
        project: Project,
    ) {
        project.run {
            val android = extensions.getByType<ApplicationExtension>()
            configureAndroidBase(android)
            apply<LibraryConventionPlugin>()
            if (hasProperty("withFirebase")) {
                apply(plugin = "acme.firebase")
            }
            listOf("a", "b").forEach { dependencies { "implementation"(project(":feature:$it")) } }
            val bom = libs.findLibrary("compose-bom").get()
            dependencies {
                "implementation"(platform(bom))
            }
        }
    }

    class Nested : Plugin<Project> {
        override fun apply(target: Project) = target.pluginManager.apply("acme.nested")
    }
}

class NotAPlugin {
    fun apply(target: Project) {
        target.pluginManager.apply("acme.never")
    }
}
