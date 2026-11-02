package io.github.krosstack.kscope.analyzer.gradle.statics

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.spi.BuildModelResult
import io.github.krosstack.kscope.spi.ScriptOutliner
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StaticFallbackTest {
    private val groovy = StaticWorkspace("settings-groovy")
    private val kotlin = StaticWorkspace("settings-forms")
    private val dependencies = StaticWorkspace("dependencies-groovy")
    private val conventions = StaticWorkspace("conventions")

    @AfterTest
    fun cleanUp() {
        listOf(groovy, kotlin, dependencies, conventions).forEach(StaticWorkspace::close)
    }

    private fun outliningOnly(accept: (String) -> Boolean) = ScriptOutliner { scripts, files ->
        CheckedInOutlines.outline(scripts.filter { accept(it.ref.path.value) }, files)
    }

    @Test
    fun `a project without a known plugin gets the source sets of its directories and is heuristic`() {
        groovy.write("app/build.gradle", "// nothing is applied here\n")
        groovy.write("app/src/test/java/AppTest.java", "class AppTest {}\n")
        val provided = groovy.provided()
        val app = provided.module(":app")
        assertEquals(emptyList(), app.plugins)
        assertEquals(Certainty.HEURISTIC, app.certainty)
        assertEquals(listOf("main", "test"), provided.sourceSets(":app").keys.toList())
        val roots = provided.sourceSets(":app").getValue("main").roots
        assertEquals(listOf("app/src/main/java" to "java"), roots.map { it.directory.value to it.kind.value })
        val info = provided.problems("gradle.static.layout-inferred").single()
        assertEquals(DiagnosticSeverity.INFO, info.severity)
    }

    @Test
    fun `a project that applies nothing and has no source directory is not a module`() {
        groovy.write("empty/build.gradle", "")
        groovy.write("settings.gradle", "include ':app', ':empty'\n")
        val provided = groovy.provided()
        assertTrue(provided.model.modules.none { it.path == ":empty" })
    }

    @Test
    fun `configuration of other projects is reported`() {
        groovy.write("build.gradle", "subprojects {\n    apply plugin: 'java'\n}\n")
        val info = groovy.provided().problems("gradle.static.cross-project-configuration-not-analysed").single()
        assertEquals(DiagnosticSeverity.INFO, info.severity)
        assertEquals("build.gradle", info.location?.artifact?.path?.value)
        assertEquals(1, info.location?.range?.startLine)
    }

    @Test
    fun `a build script that cannot be outlined is a warning and its project keeps the layout of its directories`() {
        val provided = kotlin.provided(outliningOnly { it.endsWith("settings.gradle.kts") })
        val warnings = provided.problems("gradle.static.script-not-analysed").map { it.location?.artifact?.path?.value }
        assertTrue("app/build.gradle.kts" in warnings)
        assertEquals(DiagnosticSeverity.WARNING, provided.problems("gradle.static.script-not-analysed")[0].severity)
        assertEquals(Certainty.HEURISTIC, provided.module(":app").certainty)
        assertEquals(listOf("main"), provided.sourceSets(":app").keys.toList())
    }

    @Test
    fun `a convention plugin script that cannot be outlined is a warning`() {
        val provided = conventions.provided(outliningOnly { !it.contains("acme.jvm.library") })
        val warning = provided.problems("gradle.static.script-not-analysed").single()
        assertTrue("acme.jvm.library.gradle.kts" in warning.message)
        assertEquals(listOf("acme.jvm.library"), provided.module(":lib").plugins)
    }

    @Test
    fun `a catalog with a syntax error is a warning and does not stop the model`() {
        groovy.write("gradle/libs.versions.toml", "[versions]\nkotlin = \n")
        val provided = groovy.provided()
        val warning = provided.problems("gradle.static.catalog-unparsable").single()
        assertEquals(DiagnosticSeverity.WARNING, warning.severity)
        assertEquals(4, provided.model.modules.size)
    }

    @Test
    fun `an included build outside the workspace is a warning and not a build root`() {
        groovy.write("settings.gradle", "includeBuild '../outside'\ninclude ':app'\n")
        val provided = groovy.provided()
        val warning = provided.problems("gradle.static.build-outside-workspace").single()
        assertEquals(DiagnosticSeverity.WARNING, warning.severity)
        assertEquals(listOf("tasktide//"), provided.model.buildRoots.map { it.id.value })
    }

    @Test
    fun `a dependency on a project that is no module stays in the model as an unresolved edge`() {
        dependencies.write("settings.gradle", "include ':app', ':core', ':empty'\n")
        dependencies.write("empty/build.gradle", "")
        val script = "plugins { id 'java-library' }\ndependencies { implementation project(':empty') }\n"
        dependencies.write("app/build.gradle", script)
        val edge = dependencies.provided().edgesOf(":app").single()
        val resolution = assertIs<Resolution.Unresolved>(edge.resolution)
        assertEquals("gradle.static.project-not-a-module", resolution.reason.value)
    }

    @Test
    fun `an unreadable workspace is a failure and not a crash`() {
        val failing = ScriptOutliner { _, _ -> throw java.io.IOException("worker gone") }
        val result = kotlin.provide(failing)
        assertIs<BuildModelResult.Failed>(result)
        assertEquals("gradle.static.io-failed", result.reason.value)
    }
}
