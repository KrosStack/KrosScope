package io.github.krosstack.kscope.analyzer.gradle.mapping

import io.github.krosstack.kscope.analyzer.gradle.KotlinTargetDocument
import io.github.krosstack.kscope.analyzer.gradle.ProjectDocument
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.Target
import io.github.krosstack.kscope.model.TargetId

/** A target and the names of the source sets that are compiled for it. */
internal class TargetDraft(val target: Target, val sourceSets: Set<String>)

/**
 * Targets come from the Kotlin Multiplatform targets, from the Android plugin, or are the single JVM target of a
 * Kotlin/JVM or Java module. A single JVM target is named `jvm` whatever the Kotlin plugin calls it.
 */
internal class ProjectTargets(private val document: ProjectDocument, private val module: ModuleId) {
    fun drafts(allSourceSets: Set<String>): List<TargetDraft> = when {
        isMultiplatform() -> multiplatformTargets()
        document.android?.isDsl == true -> listOf(draft(ANDROID, Platform.ANDROID, allSourceSets))
        document.kotlin?.targets.orEmpty().isNotEmpty() -> listOf(draft(JVM, Platform.JVM, allSourceSets))
        document.java != null -> listOf(draft(JVM, Platform.JVM, allSourceSets))
        else -> emptyList()
    }

    private fun isMultiplatform() = KOTLIN_MULTIPLATFORM in document.plugins

    private fun multiplatformTargets(): List<TargetDraft> = document.kotlin?.targets.orEmpty()
        .mapNotNull { target -> platformOf(target.platformType)?.let { platform -> draft(target, platform) } }

    private fun draft(target: KotlinTargetDocument, platform: Platform): TargetDraft =
        draft(target.name, platform, target.compilations.flatMap { it.allSourceSets }.toSet())

    private fun draft(name: String, platform: Platform, sourceSets: Set<String>) =
        TargetDraft(Target(TargetId("${module.value}#target/$name"), module, name, platform), sourceSets)

    private fun platformOf(platformType: String): Platform? = when (platformType) {
        "jvm" -> Platform.JVM
        "androidJvm" -> Platform.ANDROID
        "native" -> Platform.NATIVE
        "js" -> Platform.JS
        "wasm" -> Platform.WASM
        else -> null
    }

    private companion object {
        const val KOTLIN_MULTIPLATFORM = "org.jetbrains.kotlin.multiplatform"
        const val ANDROID = "android"
        const val JVM = "jvm"
    }
}
