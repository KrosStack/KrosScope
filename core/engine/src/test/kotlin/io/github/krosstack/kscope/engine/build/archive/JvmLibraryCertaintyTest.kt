package io.github.krosstack.kscope.engine.build.archive

import io.github.krosstack.kscope.engine.build.ModuleSpec
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SourceSetRole
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class JvmLibraryCertaintyTest {
    private val workspace = JvmLibraryWorkspace()

    @AfterTest
    fun clean() = workspace.close()

    @Test
    fun `a module of heuristic certainty keeps a content match heuristic`() {
        val draft = workspace.draft
        val vague = draft.module("library", ":vague", ModuleSpec(archives = "vague-1.0.0.jar", heuristic = true))
        draft.sources(vague, SourceSetRole.MAIN, "vague/src/main/kotlin", listOf("sample/Vague.kt"))
        JarFixtures.write(workspace.base.resolve("application/client/libs/vague-1.0.0.jar"), "sample/Vague.class")
        draft.dependsOnFile(workspace.client, "client/libs/vague-1.0.0.jar")

        val edge = workspace.links().relationships.single()

        assertEquals(EvidenceClass.CONTENT, edge.evidenceClass)
        assertEquals(Certainty.HEURISTIC, (edge.resolution as Resolution.Resolved).certainty)
    }

    @Test
    fun `a dependency declared with heuristic certainty keeps a content match heuristic`() {
        workspace.textSources("sample/Slugs.kt")
        workspace.consumerJar("sample/Slugs.class")
        workspace.draft.dependsOnFile(workspace.client, "client/libs/text-1.0.0.jar", Certainty.HEURISTIC)

        val edge = workspace.links().relationships.single()

        assertEquals(EvidenceClass.CONTENT, edge.evidenceClass)
        assertEquals(Certainty.HEURISTIC, (edge.resolution as Resolution.Resolved).certainty)
    }
}
