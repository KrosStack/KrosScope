package io.github.krosstack.kscope.service.context

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolKind
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.context.ContextCandidate
import io.github.krosstack.kscope.model.context.ContextRole
import io.github.krosstack.kscope.retrieval.AnalysisResult
import io.github.krosstack.kscope.retrieval.AnalyzedQuery
import io.github.krosstack.kscope.retrieval.ChangeScope
import io.github.krosstack.kscope.retrieval.FileRole
import io.github.krosstack.kscope.retrieval.RankedFile
import io.github.krosstack.kscope.retrieval.RankedSymbol
import io.github.krosstack.kscope.retrieval.SemanticState
import io.github.krosstack.kscope.retrieval.SemanticStatus
import io.github.krosstack.kscope.service.contextInput
import kotlin.math.nextDown
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StructuralContextTailTest {
    private val shared = file("shared", 8.0)
    private val extra = file("extra", 4.0)
    private val sharedTest = file("shared-test", 4.0)
    private val extraTest = file("extra-test", 2.0)
    private val symbol = Symbol(
        SymbolId("extra-symbol"), SymbolKind("kotlin.function", SymbolCategory.CALLABLE), "extra",
        "extra", "fun extra()", null, null, emptySet(), SymbolOrigin.Source,
        SourceLocation(extra.artifact, TextRange(1, 1, 2, 1)), null, null, Scope(),
        EvidenceClass.COMPILER, Certainty.EXACT,
    )
    private val retained = symbol.copy(
        id = SymbolId("retained"),
        declaration = symbol.declaration?.copy(artifact = shared.artifact),
    )
    private val baseline = analysis(
        listOf(shared, extra),
        listOf(RankedSymbol(retained, 2.0), RankedSymbol(symbol, 1.0)),
        listOf(sharedTest, extraTest),
    )
    private val applied = analysis(
        listOf(shared.copy(score = 0.04), file("head-only", 0.02, FileRole.READ)),
        listOf(RankedSymbol(retained, 100.0)),
        listOf(sharedTest),
    ).copy(semantic = SemanticStatus(SemanticState.USED))
    private val head = listOf(
        ContextCandidate(NodeRef.OfArtifact(shared.artifact), 0.04, ContextRole.EDIT),
        ContextCandidate(NodeRef.OfArtifact(applied.files[1].artifact), 0.02, ContextRole.READ),
        ContextCandidate(NodeRef.OfSymbol(retained.id), 0.04, ContextRole.EDIT),
        ContextCandidate(NodeRef.OfArtifact(sharedTest.artifact), 0.04, ContextRole.TEST),
    )

    @Test
    fun `applied head retains every candidate value and applied metadata`() {
        val boundary = Relationship(
            NodeRef.OfArtifact(shared.artifact),
            RelationKind.BINDS,
            null,
            Resolution.Resolved(NodeRef.OfArtifact(extra.artifact), Certainty.EXACT),
            EvidenceClass.STRUCTURAL,
            null,
            Scope(),
        )
        val structural = baseline.copy(boundaries = listOf(boundary), unresolved = listOf(boundary))
        for (state in listOf(SemanticState.USED, SemanticState.BEHIND)) {
            val result = applied.copy(semantic = SemanticStatus(state))
            val input = SemanticContextCandidates.input(result, structural) { shared.artifact }
            assertEquals(head, input.candidates.take(head.size))
            assertEquals(result.query.text, input.task)
            assertEquals(result.boundaries, input.boundaries)
            assertEquals(result.unresolved, input.unresolved)
            assertEquals(input, SemanticContextCandidates.input(result, structural) { shared.artifact })
        }
    }

    @Test
    fun `file symbol and test tail scores preserve structural order and proportions`() {
        val input = SemanticContextCandidates.input(applied, baseline) { shared.artifact }
        val structural = baseline.contextInput().candidates
        val expected = structural.filter { candidate -> head.none { it.node == candidate.node } }
        val tail = input.candidates.drop(head.size)
        assertEquals(
            listOf(
                NodeRef.OfArtifact(extra.artifact),
                NodeRef.OfSymbol(symbol.id),
                NodeRef.OfArtifact(extraTest.artifact),
            ),
            tail.map { it.node },
        )
        assertEquals(expected.map { it.node }, tail.map { it.node })
        assertEquals(expected.map { it.role }, tail.map { it.role })
        val peak = structural.maxOf { it.relevance }
        val floor = applied.files.minOf { it.score }
        expected.zip(tail).forEach { (original, scaled) ->
            assertEquals(floor * original.relevance / peak, scaled.relevance, 1e-15)
            assertTrue(scaled.relevance < floor)
        }
        assertEquals(expected[0].relevance / expected[1].relevance, tail[0].relevance / tail[1].relevance)
    }

    @Test
    fun `peak includes head candidates and a tail candidate at the peak stays below the floor`() {
        val input = SemanticContextCandidates.input(applied, baseline) { shared.artifact }
        val tail = input.candidates.drop(head.size)
        assertEquals(0.01, tail.first().relevance)
        val strongest = baseline.copy(
            files = listOf(extra.copy(score = 8.0)),
            symbols = emptyList(),
            tests = emptyList(),
        )
        val scaled = SemanticContextCandidates.input(applied, strongest) { shared.artifact }
        assertEquals(0.02.nextDown(), scaled.candidates.last().relevance)
    }

    @Test
    fun `shared nodes keep their head score exactly once and only tests of the tail are appended`() {
        val unscored = file("unscored-test", 0.0)
        val headFile = applied.files[1].copy(score = 1.0)
        val structural = baseline.copy(tests = listOf(sharedTest, extraTest, unscored, headFile))
        for (result in listOf(baseline, structural)) {
            val input = SemanticContextCandidates.input(applied, result) { shared.artifact }
            head.forEach { candidate -> assertEquals(candidate, input.candidates.single { it.node == candidate.node }) }
            assertEquals(
                listOf<NodeRef>(NodeRef.OfArtifact(sharedTest.artifact), NodeRef.OfArtifact(extraTest.artifact)),
                input.tests,
            )
            assertTrue(input.candidates.none { it.node == NodeRef.OfArtifact(unscored.artifact) })
        }
    }

    @Test
    fun `a head without a positive finite lowest file score has no tail and gains no test nodes`() {
        val empty = applied.copy(files = emptyList(), symbols = emptyList(), tests = emptyList())
        val headless = SemanticContextCandidates.input(empty, baseline) { null }
        assertTrue(headless.candidates.isEmpty())
        assertTrue(headless.tests.isEmpty())
        for (score in listOf(0.0, -0.02, Double.NaN, Double.NEGATIVE_INFINITY)) {
            val result = applied.copy(files = listOf(applied.files[0], applied.files[1].copy(score = score)))
            val input = SemanticContextCandidates.input(result, baseline) { shared.artifact }
            assertEquals(result.files.size + result.symbols.size + result.tests.size, input.candidates.size)
            assertEquals(listOf<NodeRef>(NodeRef.OfArtifact(sharedTest.artifact)), input.tests)
        }
    }

    @Test
    fun `empty and nonpositive structural mappings have no tail`() {
        val baselines = listOf(
            baseline.copy(files = emptyList(), symbols = emptyList(), tests = emptyList()),
            baseline.copy(files = listOf(extra.copy(score = 0.0)), symbols = emptyList(), tests = emptyList()),
            baseline.copy(files = listOf(extra.copy(score = -1.0)), symbols = emptyList(), tests = emptyList()),
        )
        baselines.forEach { result ->
            assertEquals(head, SemanticContextCandidates.input(applied, result) { shared.artifact }.candidates)
        }
    }

    @Test
    fun `nonfinite and nonpositive structural candidates never enter the tail`() {
        val scores = listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0, -1.0, 4.0)
        val result = baseline.copy(
            files = scores.mapIndexed { index, score -> file("candidate-$index", score) },
            symbols = emptyList(),
            tests = emptyList(),
        )
        val tail = SemanticContextCandidates.input(applied, result) { shared.artifact }.candidates.drop(head.size)
        val valid = NodeRef.OfArtifact(result.files.last().artifact)
        assertEquals(listOf(ContextCandidate(valid, 0.02.nextDown(), ContextRole.EDIT)), tail)
        for (score in scores.dropLast(1)) {
            val invalid = result.copy(files = listOf(extra.copy(score = score)))
            assertEquals(head, SemanticContextCandidates.input(applied, invalid) { shared.artifact }.candidates)
        }
    }

    @Test
    fun `a baseline from a different query is rejected`() {
        val result = baseline.copy(query = baseline.query.copy(text = "another task"))
        assertFailsWith<IllegalArgumentException> { SemanticContextCandidates.input(applied, result) { null } }
    }

    @Test
    fun `an applied baseline is rejected by the structural mapping`() {
        for (state in listOf(SemanticState.USED, SemanticState.BEHIND)) {
            val result = baseline.copy(semantic = SemanticStatus(state))
            val failure = assertFailsWith<IllegalArgumentException> {
                SemanticContextCandidates.input(applied, result) { null }
            }
            assertTrue(failure.message.orEmpty().contains("KrosScope.contextInput"))
        }
    }

    private fun file(name: String, score: Double, role: FileRole = FileRole.EDIT): RankedFile =
        RankedFile(ArtifactRef(RepositoryId("repo"), RepoPath(name)), score, role, emptyList())

    private fun analysis(
        files: List<RankedFile>,
        symbols: List<RankedSymbol>,
        tests: List<RankedFile>,
    ): AnalysisResult = AnalysisResult(
        AnalyzedQuery("change", listOf("change"), emptyList(), emptyList(), emptyList()),
        files, symbols, emptyList(), tests, emptyList(),
        ChangeScope(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), false),
        emptyList(), emptyList(), emptyMap(), "limited",
    )
}
