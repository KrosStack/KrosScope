package io.github.krosstack.kscope.analyzer.gradle.statics.convention

import io.github.krosstack.kscope.analyzer.gradle.statics.module
import io.github.krosstack.kscope.analyzer.gradle.statics.problems
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.DiagnosticSeverity
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClassPluginRegistrationStaticTest {
    private val tree = ClassConventionTree()
    private val provided = tree.provided

    @AfterTest
    fun cleanUp() {
        tree.close()
    }

    @Test
    fun `a plugin registered by a catalog alias is applied by that alias and keeps its own id`() {
        val lib = provided.module(":lib")
        assertEquals(listOf("acme.android.library", "acme.lint", "com.android.library"), lib.plugins)
        assertEquals(Certainty.HEURISTIC, lib.certainty)
    }

    @Test
    fun `a plugin registered by an asProvider alias of a longer alias is expanded`() {
        val ui = provided.module(":ui")
        val compose = "org.jetbrains.kotlin.plugin.compose"
        assertEquals(listOf("acme.android.library.compose", "com.android.library", compose), ui.plugins)
    }

    @Test
    fun `a plugin registered by create with a string id is expanded and applies plugins by id and by class`() {
        val feature = provided.module(":feature")
        val expected = listOf(
            "acme.android.library",
            "acme.feature",
            "acme.lint",
            "com.android.library",
            "org.jetbrains.kotlin.plugin.serialization",
        )
        assertEquals(expected, feature.plugins)
        assertEquals("true", feature.attributes["kotlin.plugin.serialization"])
    }

    @Test
    fun `a plugin registered by a descriptor file is read and its class applies by plugin manager`() {
        val plain = provided.module(":plain")
        assertEquals(listOf("acme.broken", "acme.described", "java", "java-library"), plain.plugins)
    }

    @Test
    fun `a registered plugin whose class cannot be found is reported as not analysed`() {
        val info = provided.problems("gradle.static.plugin-not-analysed").single()
        assertEquals(DiagnosticSeverity.INFO, info.severity)
        assertTrue("acme.broken" in info.message)
        assertEquals("plain/build.gradle.kts", info.location?.artifact?.path?.value)
    }

    @Test
    fun `apply with a class literal applies the class of the same build`() {
        val aggregate = provided.module(":aggregate")
        assertEquals(listOf("acme.aggregate", "org.jetbrains.kotlin.plugin.serialization"), aggregate.plugins)
    }
}
