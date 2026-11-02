package io.github.krosstack.kscope.analyzer.gradle.statics.convention

import io.github.krosstack.kscope.analyzer.gradle.statics.module
import io.github.krosstack.kscope.analyzer.gradle.statics.problems
import io.github.krosstack.kscope.model.DiagnosticSeverity
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClassPluginDiagnosticsStaticTest {
    private val tree = ClassConventionTree()
    private val provided = tree.provided
    private val partial = provided.problems("gradle.static.convention-plugin-partially-analysed")

    @AfterTest
    fun cleanUp() {
        tree.close()
    }

    @Test
    fun `a conditional block is not read and is reported once however many modules apply the plugin`() {
        val lint = partial.single { "LintPlugin" in it.message }
        assertEquals(DiagnosticSeverity.INFO, lint.severity)
        assertTrue("'when' block" in lint.message)
        assertEquals("${ClassConventionTree.LOGIC}/LintPlugin.kt", lint.location?.artifact?.path?.value)
        assertFalse("com.android.lint" in provided.module(":lib").plugins)
    }

    @Test
    fun `what is computed stays unknown and is named in one diagnostic for the plugin`() {
        val dynamic = partial.single { "DynamicPlugin" in it.message }
        assertTrue("whose id is computed" in dynamic.message)
        assertTrue("only if the plugin com.android.base is applied" in dynamic.message)
        assertTrue("'forEach' block" in dynamic.message)
        assertTrue("dependencies whose configuration or notation is computed" in dynamic.message)
    }

    @Test
    fun `what is computed is not guessed`() {
        assertEquals(listOf("acme.dynamic"), provided.module(":dynamic").plugins)
        assertEquals(listOf(":core" to "implementation"), tree.dependenciesOf(":dynamic"))
    }

    @Test
    fun `a plugin class outside the build and classes that apply each other are reported and not followed`() {
        val aggregate = partial.single { "AggregatePlugin" in it.message }
        assertTrue("the plugin class AbsentPlugin, which is not part of the build" in aggregate.message)
        assertTrue("the plugin class PingPlugin beyond the nesting limit" in aggregate.message)
    }

    @Test
    fun `extensions other than the android and kotlin ones are ignored without a diagnostic`() {
        assertFalse(partial.any { "AppPlugin" in it.message })
    }

    @Test
    fun `only the plugins with something unread are reported`() {
        val names = partial.map { it.message.split("'")[1] }.sorted()
        assertEquals(listOf("AggregatePlugin", "DynamicPlugin", "LintPlugin"), names)
    }
}
