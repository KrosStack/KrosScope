package io.github.krosstack.kscope.service.context

import io.github.krosstack.kscope.context.ContextCompiler
import io.github.krosstack.kscope.context.TokenEstimators
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.context.ContextCandidate
import io.github.krosstack.kscope.model.context.ContextInput
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.ContextRole
import io.github.krosstack.kscope.retrieval.FileRole
import io.github.krosstack.kscope.retrieval.RankedFile
import io.github.krosstack.kscope.retrieval.RankedSymbol
import io.github.krosstack.kscope.retrieval.SemanticState
import io.github.krosstack.kscope.retrieval.SemanticStatus
import io.github.krosstack.kscope.retrieval.TaskRetrieval
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.ServiceJson
import io.github.krosstack.kscope.service.contextInput
import io.github.krosstack.kscope.service.readWorkspaceFiles
import io.github.krosstack.kscope.service.semantic.query.SemanticQueryFixture
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SemanticContextCandidatesTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `test and boundary supplementation keeps its existing role policy`() = fixture { f, scope ->
        val files = f.graph.artifacts().filter { it.ref.path.value.endsWith(".kt") }.map { it.ref }
        val edit = files[0]
        val test = files[1]
        val boundary = Relationship(
            NodeRef.OfArtifact(edit),
            RelationKind.BINDS,
            null,
            Resolution.Resolved(NodeRef.OfArtifact(test), Certainty.EXACT),
            EvidenceClass.STRUCTURAL,
            null,
            Scope(),
        )
        val baseline = TaskRetrieval(f.graph, scope.searchIndex()).analyze("Lib").copy(
            files = listOf(RankedFile(edit, 0.02, FileRole.EDIT, emptyList())),
            symbols = emptyList(),
            tests = listOf(RankedFile(test, 4.0, FileRole.READ, emptyList())),
            boundaries = listOf(boundary),
        )
        val input = SemanticContextCandidates.input(baseline, baseline) { null }
        assertEquals(listOf(ContextRole.EDIT, ContextRole.TEST), input.candidates.map { it.role })
        assertEquals(listOf(0.02, 0.02), input.candidates.map { it.relevance })
        assertEquals(listOf(boundary), input.boundaries)
        assertEquals(listOf(NodeRef.OfArtifact(test)), input.tests)
        val compiler = ContextCompiler(f.graph, scope.readWorkspaceFiles(), TokenEstimators.create())
        for (level in ContextLevel.entries) {
            val expected = compiler.compile(baseline.contextInput(), level)
            val actual = compiler.compile(input, level)
            assertEquals(ServiceJson.encode(expected), ServiceJson.encode(actual))
            assertTrue(actual.fragments.any { it.artifact == edit && it.role == ContextRole.EDIT })
            val supplementary = actual.fragments.filter { it.artifact == test }
            assertTrue(supplementary.isNotEmpty())
            assertTrue(supplementary.all { it.role == ContextRole.TEST })
        }
        val boundaryOnly = compiler.compile(input.copy(candidates = input.candidates.take(1), tests = emptyList()))
        assertTrue(boundaryOnly.fragments.any { it.artifact == test && it.role == ContextRole.BOUNDARY })
    }

    @Test
    fun `non applied candidate mapping is byte for byte unchanged`() = fixture { f, scope ->
        val baseline = TaskRetrieval(f.graph, scope.searchIndex()).analyze("Lib")
        val symbol = baseline.symbols.first().symbol
        val source = requireNotNull(symbol.declaration).artifact
        val other = f.graph.artifacts().first { it.ref != source }.ref
        val result = baseline.copy(
            files = listOf(RankedFile(source, 0.02, FileRole.EDIT, emptyList())),
            symbols = listOf(RankedSymbol(symbol, 10.0)),
            tests = listOf(RankedFile(other, 4.0, FileRole.READ, emptyList())),
        )
        val expected = ContextInput(
            result.query.text,
            listOf(
                ContextCandidate(NodeRef.OfArtifact(source), 0.02, ContextRole.EDIT),
                ContextCandidate(NodeRef.OfSymbol(symbol.id), 10.0, ContextRole.EDIT),
                ContextCandidate(NodeRef.OfArtifact(other), 10.0, ContextRole.TEST),
            ),
            result.boundaries,
            listOf(NodeRef.OfArtifact(other)),
            result.unresolved,
        )
        for (state in SemanticState.entries.filter { it != SemanticState.USED && it != SemanticState.BEHIND }) {
            val actual = scope.contextInput(result.copy(semantic = SemanticStatus(state)))
            assertEquals(ServiceJson.encode(expected), ServiceJson.encode(actual))
        }
    }

    private fun fixture(test: (SemanticQueryFixture, KrosScope) -> Unit) {
        SemanticQueryFixture(root).use { f ->
            KrosScope.open(root, OpenOptions(), { null }).use { scope -> test(f, scope) }
        }
    }
}
