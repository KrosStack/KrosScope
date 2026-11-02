package io.github.krosstack.kscope.retrieval.analysis

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.SourceSetRole
import io.github.krosstack.kscope.retrieval.FileRole
import io.github.krosstack.kscope.retrieval.RetrievalFixture
import io.github.krosstack.kscope.retrieval.RetrievalGraph
import io.github.krosstack.kscope.retrieval.RetrievalIndex
import io.github.krosstack.kscope.retrieval.TaskRetrieval
import io.github.krosstack.kscope.retrieval.artifact
import io.github.krosstack.kscope.retrieval.node
import io.github.krosstack.kscope.retrieval.ranking.FileRanking
import io.github.krosstack.kscope.retrieval.relationship
import io.github.krosstack.kscope.spi.search.SearchHit
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class AnalysisEvidenceTest {
    @Test
    fun `resolved evidence distinguishes exact probable and heuristic support`() {
        val file = artifact("Root.kt")
        val root = NodeRef.OfArtifact(file.ref)
        val edges = Certainty.entries.map { certainty ->
            relationship(root, node("${certainty.name}.kt"), certainty = certainty)
        }
        val hit = SearchHit(root, 1.0, listOf("root"), emptyMap())
        val result = TaskRetrieval(RetrievalFixture(listOf(file), edges), RetrievalIndex(hits = listOf(hit)))
            .analyze("root")

        assertEquals(
            mapOf("compiler:exact" to 1, "compiler:probable" to 1, "compiler:heuristic" to 1),
            result.evidence,
        )
    }

    @Test
    fun `internal relationships do not inflate file fan out`() {
        val file = artifact("Root.kt")
        val root = NodeRef.OfArtifact(file.ref)
        val graph = RetrievalGraph(RetrievalFixture(listOf(file), listOf(relationship(root, root))))
        val risks = AnalysisFacts(graph).risks(setOf(file.ref))

        assertEquals(0.0, risks.single { it.name == "fan-out" }.value)
        assertEquals(0.0, risks.single { it.name == "fan-in" }.value)
    }

    @Test
    fun `a task targeting only tests can recommend editing tests`() {
        val sourceSet = SourceSet(
            SourceSetId("tests"), ModuleId("module"), "test", SourceSetRole.UNIT_TEST,
            emptyList(), emptyList(), emptyList(), emptyList(), EvidenceClass.BUILD_MODEL, Certainty.EXACT,
        )
        val file = artifact("RootTest.kt").copy(sourceSet = sourceSet.id)
        val graph = RetrievalGraph(RetrievalFixture(listOf(file), sourceSets = listOf(sourceSet)))
        val query = QueryAnalyzer(RetrievalIndex()).analyze("Fix RootTest assertions")
        val hit = SearchHit(NodeRef.OfArtifact(file.ref), 1.0, listOf("roottest"), emptyMap())
        val files = FileRanking(graph).rank(query, emptyList(), listOf(hit))

        assertEquals(listOf(file.ref), files.map { it.artifact })
        assertEquals(FileRole.EDIT, files.single().role)
    }
}
