package io.github.krosstack.kscope.analyzer.gradle.statics.android

import io.github.krosstack.kscope.analyzer.gradle.statics.layout.Layout
import io.github.krosstack.kscope.analyzer.gradle.statics.layout.SourceSetDraft
import io.github.krosstack.kscope.analyzer.gradle.statics.layout.TargetDraft
import io.github.krosstack.kscope.analyzer.gradle.statics.layout.VariantDraft
import io.github.krosstack.kscope.analyzer.gradle.statics.layout.upperFirst
import io.github.krosstack.kscope.model.Platform

private const val DEBUG = "debug"
private val testPrefixes = listOf("test", "androidTest", "testFixtures")

/**
 * The layout of an Android application or library: the single Android target, the variants, and the source sets of
 * the Android Gradle plugin. `main` always exists and the others exist when they have a directory. The test source
 * sets belong to the variants of the debug build type, which the tests are built against.
 */
internal class AndroidLayout(private val directoryExists: (String) -> Boolean) {
    fun plan(config: AndroidConfig): Layout {
        val variants = AndroidVariants.of(config)
        val constituents = variants.associateWith { AndroidVariants.constituents(it) }
        val names = constituents.values.flatten().distinct().sorted()
        val candidates = listOf("main" to variants) + names.map { name ->
            name to variants.filter { name in constituents.getValue(it) }
        } + testPrefixes.flatMap { prefix -> testCandidates(prefix, names, variants, constituents) }
        val existing = candidates.filter { (name, _) -> name == "main" || directoryExists(name) }
        val sourceSets = existing.map { (name, members) ->
            SourceSetDraft(name, emptyList(), listOf(ANDROID), members.map { it.name }, declared = false)
        }
        return Layout(listOf(TargetDraft(ANDROID, Platform.ANDROID)), variants, sourceSets)
    }

    private fun testCandidates(
        prefix: String,
        names: List<String>,
        variants: List<VariantDraft>,
        constituents: Map<VariantDraft, List<String>>,
    ): List<Pair<String, List<VariantDraft>>> {
        val tested = if (prefix == "testFixtures") variants else variants.filter { it.buildType == DEBUG }
        return listOf(prefix to tested) + names.map { name ->
            prefix + name.upperFirst() to tested.filter { name in constituents.getValue(it) }
        }
    }

    private companion object {
        const val ANDROID = "android"
    }
}
