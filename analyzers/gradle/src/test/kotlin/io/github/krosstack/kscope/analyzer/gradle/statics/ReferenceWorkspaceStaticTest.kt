package io.github.krosstack.kscope.analyzer.gradle.statics

import io.github.krosstack.kscope.analyzer.gradle.GradleStaticProvider
import io.github.krosstack.kscope.analyzer.gradle.WorkspaceRequests
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.model.ModuleTrait
import io.github.krosstack.kscope.spi.BuildModelResult
import io.github.krosstack.kscope.spi.ScriptOutliner
import io.github.krosstack.kscope.spi.WorkspaceFiles
import io.github.krosstack.kscope.spi.worker.ScriptOutline
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Outlines of the scripts of the reference workspace, generated with the Kotlin outliner and checked in. */
private object ReferenceOutlines : ScriptOutliner {
    override fun outline(scripts: List<Artifact>, files: WorkspaceFiles): Map<ArtifactRef, ScriptOutline> =
        scripts.mapNotNull { script ->
            val name = "/reference-outlines/${script.ref.path.value}.outline.json"
            val resource = ReferenceOutlines::class.java.getResource(name)
            resource?.let { script.ref to KscopeJson.decodeFromString(ScriptOutline.serializer(), it.readText()) }
        }.toMap()
}

class ReferenceWorkspaceStaticTest {
    private val fixture = Path.of(checkNotNull(System.getProperty("kscope.fixture")))

    private val provided: BuildModelResult.Provided by lazy {
        val request = WorkspaceRequests.of(fixture, trusted = false)
        assertIs<BuildModelResult.Provided>(GradleStaticProvider(ReferenceOutlines).provide(request))
    }

    @Test
    fun `the thirteen modules of the reference workspace and its build logic are found`() {
        assertEquals(
            listOf(
                ":app", ":core:legacy", ":core:model", ":data:db", ":data:settings", ":desktop", ":feature:settings",
                ":feature:tasks", ":native:bridge", ":sync:engine", ":sync:protocol", ":sync:service", ":ui:shared",
            ),
            provided.model.modules.filter { it.buildRoot.value == "tasktide//" }.map { it.path }.sorted(),
        )
        val logic = provided.module(":", "tasktide//build-logic")
        assertEquals(setOf(ModuleTrait.BUILD_LOGIC), logic.traits)
    }

    @Test
    fun `nothing in the reference workspace needs more than an information`() {
        val serious = provided.diagnostics.filter { it.severity != DiagnosticSeverity.INFO }
        assertEquals(emptyList(), serious.map { it.code.value + ": " + it.message })
    }

    @Test
    fun `the model survives serialization and is written for the comparison with the gold set`() {
        val json = KscopeJson.encodeToString(BuildModelResult.serializer(), provided)
        assertTrue(provided.facts.any { it is Fact.OfSymbol })
        assertTrue(provided.model.modules.any { it.certainty == Certainty.HEURISTIC })
        val directory = Path.of(System.getProperty("kscope.static.reference", "build/static-reference"))
        val target = directory.resolve("build-model.json")
        Files.createDirectories(target.parent)
        Files.writeString(target, json)
    }
}
