package io.github.krosstack.kscope.gradle.model

import org.gradle.api.Project
import java.io.File

/** The Android Gradle plugin DSL (`android { }`) and, for the Kotlin Multiplatform library plugin, its target. */
internal class AndroidSection(private val project: Project, private val state: KscopeProjectState) {
    fun describeDsl(android: Any): Map<String, Any?> {
        val defaults = Reflect.get(android, "defaultConfig")
        return linkedMapOf(
            "kind" to "dsl",
            "agpVersion" to agpVersion(),
            "namespace" to Values.text(Reflect.get(android, "namespace")),
            "compileSdk" to Values.text(Reflect.get(android, "compileSdk")),
            "minSdk" to Values.text(Reflect.get(defaults, "minSdk")),
            "targetSdk" to Values.text(Reflect.get(defaults, "targetSdk")),
            "applicationId" to Values.text(Reflect.get(defaults, "applicationId")),
            "buildTypes" to Values.names(Reflect.get(android, "buildTypes")),
            "flavorDimensions" to Values.items(Reflect.get(android, "flavorDimensions")).map { it.toString() },
            "productFlavors" to productFlavors(android),
            "buildFeatures" to enabledBuildFeatures(Reflect.get(android, "buildFeatures")),
            "sourceSets" to sourceSets(android),
            "variants" to AndroidVariants(project, state).describeAll(),
        )
    }

    fun describeLibraryTarget(target: Any): Map<String, Any?> = linkedMapOf(
        "kind" to "kmp-library",
        "agpVersion" to agpVersion(),
        "namespace" to Values.text(Reflect.get(target, "namespace")),
        "compileSdk" to Values.text(Reflect.get(target, "compileSdk")),
        "minSdk" to Values.text(Reflect.get(target, "minSdk")),
    )

    fun bootClasspath(): List<String> {
        val components = project.extensions.findByName("androidComponents")
        val sdk = Reflect.get(components, "sdkComponents")
        return Values.items(Reflect.get(sdk, "bootClasspath")).mapNotNull { Values.text(it) }
    }

    private fun agpVersion(): String? =
        Values.text(Reflect.get(project.extensions.findByName("androidComponents"), "pluginVersion"))

    private fun productFlavors(android: Any): List<Map<String, Any?>> =
        Values.items(Reflect.get(android, "productFlavors")).map {
            linkedMapOf(
                "name" to Values.text(Reflect.get(it, "name")),
                "dimension" to Values.text(Reflect.get(it, "dimension")),
            )
        }.sortedBy { it["name"].toString() }

    private fun enabledBuildFeatures(features: Any?): List<String> =
        BUILD_FEATURES.filter { Reflect.get(features, it) == true }

    private fun sourceSets(android: Any): List<Map<String, Any?>> =
        Values.items(Reflect.get(android, "sourceSets")).filterNotNull().map(::sourceSet)
            .sortedBy { it["name"].toString() }

    private fun sourceSet(sourceSet: Any): Map<String, Any?> {
        val directories = DIRECTORY_KINDS.associateWith { kind ->
            val set = Reflect.get(sourceSet, kind)
            SourceDirs.ofRelative(project, Reflect.get(set, "directories"))
        }
        return linkedMapOf(
            "name" to Values.text(Reflect.get(sourceSet, "name")),
            "dirs" to directories.filterValues { it.isNotEmpty() },
            "manifest" to manifest(sourceSet),
        )
    }

    private fun manifest(sourceSet: Any): String? {
        val file = Reflect.get(Reflect.get(sourceSet, "manifest"), "srcFile") as? File
        return file?.takeIf { it.isFile }?.let(Values::slashPath)
    }

    private companion object {
        val BUILD_FEATURES = listOf("aidl", "buildConfig", "compose", "dataBinding", "viewBinding")
        val DIRECTORY_KINDS = listOf("java", "kotlin", "res", "aidl", "assets", "jniLibs", "resources")
    }
}
