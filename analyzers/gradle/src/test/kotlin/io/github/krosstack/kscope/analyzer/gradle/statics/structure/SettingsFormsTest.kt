package io.github.krosstack.kscope.analyzer.gradle.statics.structure

import io.github.krosstack.kscope.analyzer.gradle.statics.StaticWorkspace
import io.github.krosstack.kscope.analyzer.gradle.statics.module
import io.github.krosstack.kscope.analyzer.gradle.statics.problems
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.ModuleTrait
import io.github.krosstack.kscope.spi.BuildModelResult
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsFormsTest {
    private val kotlinForms = StaticWorkspace("settings-forms")
    private val dynamic = StaticWorkspace("settings-dynamic")
    private val groovy = StaticWorkspace("settings-groovy")

    @AfterTest
    fun cleanUp() {
        listOf(kotlinForms, dynamic, groovy).forEach(StaticWorkspace::close)
    }

    @Test
    fun `every include form and every included build become modules and build roots`() {
        val provided = kotlinForms.provided()
        assertEquals(
            listOf("tasktide//", "tasktide//build-logic", "tasktide//tools"),
            provided.model.buildRoots.map { it.id.value },
        )
        assertEquals(
            listOf(
                "tasktide//:app",
                "tasktide//:core:legacy",
                "tasktide//:core:model",
                "tasktide//:feature:settings",
                "tasktide//:feature:tasks",
                "tasktide//build-logic:",
                "tasktide//tools:",
            ),
            provided.model.modules.map { it.id.value },
        )
    }

    @Test
    fun `a project directory that is assigned a literal replaces the conventional one`() {
        val provided = kotlinForms.provided()
        assertEquals("legacy-impl", provided.module(":core:legacy").directory.value)
        assertEquals("core/model", provided.module(":core:model").directory.value)
        assertEquals("feature/tasks", provided.module(":feature:tasks").directory.value)
    }

    @Test
    fun `included builds hold build logic modules and the root project name is kept`() {
        val provided = kotlinForms.provided()
        val logic = provided.module(":", "tasktide//build-logic")
        assertEquals(setOf(ModuleTrait.BUILD_LOGIC), logic.traits)
        assertEquals(listOf("org.gradle.kotlin.kotlin-dsl"), logic.plugins)
        assertEquals("build-logic", logic.attributes["gradle.rootProject.name"])
        assertEquals(setOf(ModuleTrait.JAVA, ModuleTrait.BUILD_LOGIC), provided.module(":", "tasktide//tools").traits)
        assertEquals(setOf(ModuleTrait.JAVA), provided.module(":app").traits)
    }

    @Test
    fun `a root project that only declares plugins without applying them is not a module`() {
        val provided = kotlinForms.provided()
        assertTrue(provided.model.modules.none { it.path == ":" && it.buildRoot.value == "tasktide//" })
    }

    @Test
    fun `literal settings and plugins are exact`() {
        val provided = kotlinForms.provided()
        assertEquals(Certainty.EXACT, provided.module(":app").certainty)
        assertEquals(Certainty.EXACT, provided.module(":core:model").certainty)
    }

    @Test
    fun `dynamic includes are reported and the projects are found from their build scripts`() {
        val provided = dynamic.provided()
        val warning = provided.problems("gradle.static.dynamic-include").single()
        assertEquals(DiagnosticSeverity.WARNING, warning.severity)
        assertEquals(4, warning.location?.range?.startLine)
        assertEquals(
            listOf("tasktide//:fixed", "tasktide//:modules:alpha", "tasktide//:modules:beta"),
            provided.model.modules.map { it.id.value },
        )
        assertEquals(Certainty.EXACT, provided.module(":fixed").certainty)
        assertEquals(Certainty.HEURISTIC, provided.module(":modules:alpha").certainty)
    }

    @Test
    fun `groovy settings are read like kotlin settings`() {
        val provided = groovy.provided()
        assertEquals(
            listOf(
                "tasktide//:app",
                "tasktide//:core:model",
                "tasktide//:feature:tasks",
                "tasktide//build-logic:",
            ),
            provided.model.modules.map { it.id.value },
        )
        assertEquals("model-impl", provided.module(":core:model").directory.value)
        assertEquals(listOf("java"), provided.module(":core:model").plugins)
        assertEquals(listOf("org.jetbrains.kotlin.jvm"), provided.module(":feature:tasks").plugins)
        assertEquals(setOf(ModuleTrait.BUILD_LOGIC), provided.module(":", "tasktide//build-logic").traits)
    }

    @Test
    fun `a workspace without a settings script is left to the structural provider`() {
        StaticWorkspace("settings-forms").use { empty ->
            empty.root.resolve("settings.gradle.kts").toFile().delete()
            empty.root.resolve("build-logic/settings.gradle.kts").toFile().delete()
            assertEquals(BuildModelResult.NotApplicable, empty.provide())
        }
    }

    @Test
    fun `settings scripts that cannot be outlined leave the workspace to the structural provider`() {
        assertEquals(BuildModelResult.NotApplicable, kotlinForms.provide { _, _ -> emptyMap() })
    }
}
