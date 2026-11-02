package acme

import org.gradle.api.Plugin
import org.gradle.api.Project

abstract class LibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "com.android.library")
            pluginManager.apply("acme.lint")

            extensions.configure<LibraryExtension> {
                configureAndroidBase(this)
                defaultConfig.targetSdk = 35
                resourcePrefix = "lib_"
            }
            dependencies {
                "implementation"(project(":core:ui"))
                add("testImplementation", libs.findLibrary("junit").get())
            }
        }
    }
}
