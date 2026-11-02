package io.github.krosstack.kscope.analyzer.gradle.statics.android

import io.github.krosstack.kscope.analyzer.gradle.statics.ROOT
import io.github.krosstack.kscope.analyzer.gradle.statics.convention.ClassConventionTree
import io.github.krosstack.kscope.analyzer.gradle.statics.module
import io.github.krosstack.kscope.analyzer.gradle.statics.problems
import io.github.krosstack.kscope.analyzer.gradle.statics.sourceSets
import io.github.krosstack.kscope.analyzer.gradle.statics.variantsOf
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.DiagnosticSeverity
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlavorDirectoriesStaticTest {
    private val tree = ClassConventionTree()
    private val provided = tree.provided

    @AfterTest
    fun cleanUp() {
        tree.close()
    }

    private fun variantsOf(path: String) = provided.model.variants.filter { it.module.value == ROOT + path }

    @Test
    fun `computed flavors are taken from the source set directories that are not main, tests or build types`() {
        val all = listOf("demoDebug", "demoRelease", "prodDebug", "prodRelease")
        assertEquals(all, variantsOf(":flavored").map { it.name })
        assertEquals(
            listOf("androidTest", "debug", "demo", "demoDebug", "main", "prod", "test", "testDemo"),
            provided.sourceSets(":flavored").keys.toList(),
        )
        assertEquals(all, provided.variantsOf(":flavored", "main"))
        assertEquals(listOf("demoDebug", "demoRelease"), provided.variantsOf(":flavored", "demo"))
        assertEquals(Certainty.HEURISTIC, provided.module(":flavored").certainty)
    }

    @Test
    fun `flavors computed in the script of the module itself are inferred the same way`() {
        val expected = listOf("freeDebug", "freeRelease", "freeStaging", "paidDebug", "paidRelease", "paidStaging")
        assertEquals(expected, variantsOf(":scripted").map { it.name })
        assertEquals(listOf("freeStaging", "paidStaging"), provided.variantsOf(":scripted", "staging"))
    }

    @Test
    fun `inferred flavors are reported once per module`() {
        val inferred = provided.problems("gradle.static.flavors-inferred-from-directories")
        assertEquals(2, inferred.size)
        assertTrue(inferred.any { "'demo', 'prod'" in it.message })
        assertTrue(inferred.any { "'free', 'paid'" in it.message })
        assertEquals(DiagnosticSeverity.INFO, inferred.first().severity)
    }

    @Test
    fun `modules whose flavors are not computed have none inferred`() {
        assertTrue(variantsOf(":lib").all { it.flavors.isEmpty() })
    }
}
