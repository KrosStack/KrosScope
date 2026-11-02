package com.acme.logic

import org.gradle.api.Plugin
import org.gradle.api.Project

class FeaturePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.run {
            plugins.apply("acme.android.library")
            apply<SerializationPlugin>()
            dependencies {
                "implementation"(project(":ui"))
                "api"(project(":core"))
            }
        }
    }
}

class SerializationPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
    }
}
