package io.github.krosstack.kscope.analyzer.gradle.statics.layout

import io.github.krosstack.kscope.model.Platform

/**
 * The layout of a Kotlin/JVM or Java project: the single JVM target and the source sets `main`, which always exists,
 * `test` and `testFixtures`, which exist when they have a directory, and the source sets that scripts declare.
 */
internal class JvmLayout(private val directoryExists: (String) -> Boolean) {
    fun plan(blocks: List<SourceSetBlock>, hasTestFixturesPlugin: Boolean): Layout {
        val declared = blocks.filter { it.creates }.map { it.name }
        val standard = listOf("main", "test", "testFixtures").filter { name ->
            name == "main" || directoryExists(name) || (name == "testFixtures" && hasTestFixturesPlugin)
        }
        val edges = blocks.groupBy { it.name }.mapValues { (_, same) -> same.flatMap(SourceSetBlocks::dependsOn) }
        val sourceSets = (standard + declared).distinct().map { name ->
            SourceSetDraft(name, edges[name].orEmpty(), listOf(JVM), emptyList(), declared = name in declared)
        }
        return Layout(listOf(TargetDraft(JVM, Platform.JVM)), emptyList(), sourceSets)
    }

    private companion object {
        const val JVM = "jvm"
    }
}
