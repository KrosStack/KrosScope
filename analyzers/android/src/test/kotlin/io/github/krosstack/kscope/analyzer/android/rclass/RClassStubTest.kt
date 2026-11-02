package io.github.krosstack.kscope.analyzer.android.rclass

import io.github.krosstack.kscope.analyzer.android.DroidModel
import io.github.krosstack.kscope.analyzer.android.Golden
import io.github.krosstack.kscope.analyzer.android.SampleTree
import io.github.krosstack.kscope.analyzer.android.facts
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.spi.SyntheticSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class RClassStubTest : RClassTestBase() {

    @Test
    fun `each android module with a namespace and resources gets a java stub in its main source set`() {
        val result = run()
        val namespaces = result.sources.map(::namespaceOf)
        assertEquals(listOf("sample.droid.app", "sample.droid.lib", "sample.droid.util"), namespaces)
        val app = result.source("sample.droid.app")
        assertEquals("java", app.language)
        assertEquals(DroidModel.sourceSetId(":app", "main"), app.sourceSet)
        assertEquals("android.r", app.generator)
        assertEquals("android-r/sample/droid/app/R.java", app.virtualPath)
    }

    private fun namespaceOf(source: SyntheticSource) =
        source.text.lineSequence().first().removePrefix("package ").removeSuffix(";")

    @Test
    fun `the stub of the app matches the golden text`() {
        val result = run()
        Golden.assertMatches("rclass-app.java", result.source("sample.droid.app").text)
        Golden.assertMatches("rclass-lib.java", result.source("sample.droid.lib").text)
    }

    @Test
    fun `fields are public static final ints named after the resources with dots turned into underscores`() {
        val text = run().source("sample.droid.app").text
        assertTrue("public static final int app_name = 0;" in text)
        assertTrue("public static final int Theme_Droid_Dark = 0;" in text)
        assertTrue("public static final int[] BadgeView = {};" in text)
        assertTrue("public static final int BadgeView_accent = 0;" in text)
        assertTrue("public static final int BadgeView_android_textColor = 0;" in text)
        assertTrue("public static final class layout {" in text)
        assertTrue("public static final class array {" in text)
        assertEquals(
            1,
            Regex("int app_name = ").findAll(text).count(),
            "One field for a resource defined in many files",
        )
    }

    @Test
    fun `resources of a variant source set and of every configuration are in the class of the module`() {
        val text = run().source("sample.droid.app").text
        assertTrue("int debug_banner = 0;" in text)
        assertTrue("int only_german = 0;" in text)
        assertTrue("int land_only = 0;" in text)
        assertTrue("int ic_logo = 0;" in text && "int ic_launcher = 0;" in text && "int license = 0;" in text)
    }

    @Test
    fun `by default the class holds the resources of its own module only`() {
        val text = run().source("sample.droid.app").text
        assertTrue("lib_title" !in text && "in_two_libs" !in text)
    }

    @Test
    fun `with transitive classes asked for the class holds the resources of the dependencies too`() {
        val tree = SampleTree("droid", overrides = mapOf("gradle.properties" to "android.nonTransitiveRClass=false\n"))
        val text = run(tree).source("sample.droid.app").text
        assertTrue("int lib_title = 0;" in text)
        assertTrue("int lib_marker = 0;" in text)
        assertEquals(1, Regex("int in_two_libs = ").findAll(text).count())
        val explicit = SampleTree(
            "droid",
            overrides = mapOf("gradle.properties" to "android.nonTransitiveRClass=true\n"),
        )
        assertTrue("lib_title" !in run(explicit).source("sample.droid.app").text)
    }

    @Test
    fun `resource names that are no java names have no field and are counted`() {
        val tree = SampleTree(
            "droid",
            overrides = mapOf(
                "lib/src/main/res/values/keywords.xml" to
                    "<resources><string name=\"new\">x</string><string name=\"fine\">y</string></resources>",
            ),
        )
        val result = run(tree)
        val text = result.source("sample.droid.lib").text
        assertTrue("int fine = 0;" in text && "int new " !in text)
        assertTrue(
            result.facts.filterIsInstance<Fact.OfDiagnostic>().any { it.value.code.value == "android.r.name-skipped" },
        )
    }
}
