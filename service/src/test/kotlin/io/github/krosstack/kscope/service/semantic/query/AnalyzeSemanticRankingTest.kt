package io.github.krosstack.kscope.service.semantic.query

import io.github.krosstack.kscope.context.ContextCompiler
import io.github.krosstack.kscope.context.TokenEstimators
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.ContextRole
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.retrieval.AnalysisResult
import io.github.krosstack.kscope.retrieval.FileRole
import io.github.krosstack.kscope.retrieval.MlMode
import io.github.krosstack.kscope.retrieval.RankedFile
import io.github.krosstack.kscope.retrieval.RankedSymbol
import io.github.krosstack.kscope.retrieval.RetrievalStage
import io.github.krosstack.kscope.retrieval.SemanticState
import io.github.krosstack.kscope.retrieval.SemanticStatus
import io.github.krosstack.kscope.retrieval.SignalKind
import io.github.krosstack.kscope.retrieval.TaskRetrieval
import io.github.krosstack.kscope.retrieval.ranking.FileRankFusion
import io.github.krosstack.kscope.semantic.search.SemanticFileRanking
import io.github.krosstack.kscope.semantic.store.GenerationFile
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.ProgressEvent
import io.github.krosstack.kscope.service.ProgressListener
import io.github.krosstack.kscope.service.QueryStages
import io.github.krosstack.kscope.service.ServiceJson
import io.github.krosstack.kscope.service.context
import io.github.krosstack.kscope.service.contextInput
import io.github.krosstack.kscope.service.readWorkspaceFiles
import io.github.krosstack.kscope.service.semantic.FakeSemanticComponents
import io.github.krosstack.kscope.service.semantic.SemanticComponents
import io.github.krosstack.kscope.service.semantic.SemanticIndexingMode
import io.github.krosstack.kscope.service.semantic.SemanticTestWorkspace
import io.github.krosstack.kscope.service.semantic.WorkerEmbeddingProvider
import io.github.krosstack.kscope.service.withContextEstimates
import io.github.krosstack.kscope.spi.graph.GraphStore
import io.github.krosstack.kscope.spi.graph.SymbolQuery
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AnalyzeSemanticRankingTest {
    @TempDir
    lateinit var root: Path
    private var onProgress: (ProgressEvent) -> Unit = {}

    @Test
    fun `FULL ON applies late fusion after the complete structural list`() = fixture { f, fake, scope ->
        val baseline = structural(scope)
        assertTrue(baseline.files.size > 10)
        val snapshot = assertIs<CurrentSemanticGeneration.Opening.Ready>(f.current.open(f.provider)).snapshot
        val semantic = SemanticFileRanking.rank(snapshot.entries, f.provider.embedQuery("anchor"))
        val expected = FileRankFusion.fuse(baseline.files.map { it.artifact }, semantic).take(10)
        val actual = scope.analyze("anchor", includeContextEstimates = false)
        assertEquals(expected.map { it.artifact }, actual.files.map { it.artifact })
        assertEquals(expected.map { it.score }, actual.files.map { it.score })
        assertNotEquals(baseline.files.take(10).map { it.artifact }, actual.files.map { it.artifact })
        assertEquals(SemanticState.USED, actual.semantic.state)
        assertEquals(f.provider.id, actual.semantic.provider)
        assertEquals(f.provider.identity, actual.semantic.model)
        assertEquals(0, actual.semantic.behindChunks)
        assertEquals(listOf("anchor"), fake.queriesEmbedded)
    }

    @Test
    fun `symbols modules tests scope and nearby facts follow the final files`() = fixture { _, _, scope ->
        val retrieval = TaskRetrieval(scope.graph(), scope.searchIndex())
        val initial = retrieval.analyze("Lib")
        val baseline = initial.copy(
            tests = initial.files.take(1),
            contextEstimates = mapOf(ContextLevel.FULL to TokenCount(1)),
        )
        assertTrue(baseline.symbols.isNotEmpty())
        val selected = scope.graph().artifacts().filter { it.ref.path.value.startsWith("lib/docs/") }.take(10)
        val actual = retrieval.withFileRanking(baseline, FileRankFusion.fuse(emptyList(), selected.map { it.ref }))
        assertEquals(selected.map { it.ref }, actual.files.map { it.artifact })
        assertTrue(actual.symbols.isEmpty())
        assertTrue(actual.tests.isEmpty())
        assertTrue(actual.contextEstimates.isEmpty())
        assertTrue(actual.scope.modules.isEmpty())
        assertTrue(actual.scope.publicSymbols.isEmpty())
        assertTrue(actual.boundaries.isEmpty() && actual.boundaryPaths.isEmpty())
        assertTrue(actual.unresolved.isEmpty() && actual.evidence.isEmpty())
        assertEquals(selected.map { it.ref }.toSet(), actual.risks.map { (it.node as NodeRef.OfArtifact).ref }.toSet())
        assertEquals(actual.files.sumOf { it.score }, actual.modules.sumOf { it.score }, 1e-15)
        assertEquals("limited", actual.confidence)
        assertTrue(actual.files.all { it.role == FileRole.READ })
    }

    @Test
    fun `estimates are computed from finalized candidates`() = fixture { _, fake, scope ->
        val result = scope.analyze("Lib")
        assertEquals(SemanticState.USED, result.semantic.state)
        val input = scope.contextInput(result)
        val head = input.candidates.take(result.files.size + result.symbols.size)
        val artifacts = head.filter { it.node is NodeRef.OfArtifact && it.role != ContextRole.TEST }
        assertEquals(result.files.map { NodeRef.OfArtifact(it.artifact) }, artifacts.map { it.node })
        for (candidate in head.filter { it.node is NodeRef.OfSymbol }) {
            val symbol = result.symbols.single { NodeRef.OfSymbol(it.symbol.id) == candidate.node }.symbol
            val file = result.files.single { it.artifact == symbol.declaration?.artifact }
            assertTrue(candidate.relevance > 0.0 && candidate.relevance <= file.score)
        }
        val compiler = ContextCompiler(scope.graph(), scope.readWorkspaceFiles(), TokenEstimators.create())
        assertEquals(ContextLevel.entries.associateWith { compiler.estimate(input, it) }, result.contextEstimates)
        assertEquals(listOf("Lib"), fake.queriesEmbedded)
        fake.queriesEmbedded.clear()
        scope.context("Lib")
        assertEquals(listOf("Lib"), fake.queriesEmbedded)
    }

    @Test
    fun `OFF absent disabled unusable and failed queries preserve complete baseline JSON`() =
        fixture { f, fake, scope ->
            val baseline = structural(scope)
            val off = TaskRetrieval(scope.graph(), scope.searchIndex()).analyze("anchor", ml = MlMode.OFF)
            assertSame(off, scope.withSemanticRanking(off, RetrievalStage.FULL, MlMode.OFF))
            val actualOff = scope.analyze("anchor", ml = MlMode.OFF, includeContextEstimates = false)
            assertEquals(ServiceJson.encode(off), ServiceJson.encode(actualOff))
            for (state in listOf(SemanticState.NOT_INSTALLED, SemanticState.DISABLED)) {
                fake.problem = SemanticComponents.Opening.Unavailable(state, "Unavailable")
                assertSame(baseline, scope.withSemanticRanking(baseline, RetrievalStage.FULL, MlMode.ON))
                val actual = scope.analyze("anchor", includeContextEstimates = false)
                assertEquals(ServiceJson.encode(baseline), ServiceJson.encode(actual))
            }
            fake.problem = null
            for (reason in WorkerEmbeddingProvider.Refusal.entries) {
                fake.queryRefusal = reason
                assertSame(baseline, scope.withSemanticRanking(baseline, RetrievalStage.FULL, MlMode.ON))
            }
            fake.queryRefusal = null
            assertEquals(SemanticState.USED, scope.analyze("anchor", includeContextEstimates = false).semantic.state)
            scope.semantic.invalidate()
            val manifest = Files.readAllBytes(f.directory.resolve("embeddings.json"))
            Files.delete(f.directory.resolve("embeddings.json"))
            assertSame(baseline, scope.withSemanticRanking(baseline, RetrievalStage.FULL, MlMode.ON))
            Files.write(f.directory.resolve("embeddings.json"), manifest)
            f.cache.write(f.provider.identity, emptyList())
            assertSame(baseline, scope.withSemanticRanking(baseline, RetrievalStage.FULL, MlMode.ON))
        }

    @Test
    fun `lexical and intermediate stages start no semantic query`() = fixture { _, fake, scope ->
        for (stage in RetrievalStage.entries.filter { it != RetrievalStage.FULL }) {
            val expected = TaskRetrieval(scope.graph(), scope.searchIndex()).analyze("anchor", stage)
            assertSame(expected, scope.withSemanticRanking(expected, stage, MlMode.ON))
            assertEquals(ServiceJson.encode(expected), ServiceJson.encode(scope.analyze("anchor", stage, false)))
        }
        assertEquals(0, fake.opened)
        assertEquals(0, fake.starts)
        assertEquals(SemanticState.USED, scope.analyze("anchor", includeContextEstimates = false).semantic.state)
        assertEquals(listOf("anchor"), fake.queriesEmbedded)
    }

    @Test
    fun `a changed graph cannot mix old file ranks with new estimates`() = fixture { _, fake, scope ->
        val baseline = structural(scope)
        fake.onQuery = {
            SemanticTestWorkspace.write(root, "lib/docs/anchor-new.md", "# Anchor new\nAdded example\n")
            scope.scan()
        }
        val actual = scope.analyze("anchor")
        val expected = scope.withContextEstimates(structural(scope))
        assertNotEquals(baseline.files, expected.files)
        assertEquals(ServiceJson.encode(expected), ServiceJson.encode(actual))
        assertTrue(actual.contextEstimates.isNotEmpty())
        assertEquals(listOf("anchor"), fake.queriesEmbedded)
    }

    @Test
    fun `a graph changed during estimates discards the applied result`() = fixture { f, fake, scope ->
        val baseline = structural(scope)
        onProgress = {
            if (it is ProgressEvent.Started && it.stage == QueryStages.ESTIMATE) {
                onProgress = {}
                SemanticTestWorkspace.write(root, "lib/docs/anchor-new.md", "# Anchor new\nAdded example\n")
                scope.scan()
            }
        }
        val actual = scope.analyze("anchor")
        val expected = scope.withContextEstimates(structural(scope))
        assertNotEquals(baseline.files, expected.files)
        assertEquals(ServiceJson.encode(expected), ServiceJson.encode(actual))
        assertTrue(actual.contextEstimates.isNotEmpty())
        assertEquals(listOf("anchor"), fake.queriesEmbedded)
        assertEquals(1, fake.closed)
        f.assertNoLeases()
    }

    @Test
    fun `BEHIND applies the exact published vector deficit`() = fixture { f, _, scope ->
        val generation = f.store.open(f.provider.id).use { requireNotNull(it).generation }
        f.store.publish(GenerationFile(generation.header.copy(chunkCount = 1), generation.chunks.take(1)))
        val result = scope.analyze("anchor", includeContextEstimates = false)
        val keys = f.inventory.flatMap { it.chunks }.map { it.key }.toSet()
        assertEquals(
            SemanticStatus(SemanticState.BEHIND, f.provider.id, f.provider.identity, keys.size - 1),
            result.semantic,
        )
        assertEquals(10, result.files.size)
        assertTrue(result.files.any { file -> file.reasons.any { it.signal == SignalKind.SEMANTIC } })
    }

    @Test
    fun `applied context rebases retained symbols and omits invalid or excluded primaries`() = fixture { _, _, scope ->
        val baseline = TaskRetrieval(scope.graph(), scope.searchIndex()).analyze("Lib")
        val owner = requireNotNull(baseline.symbols.first().symbol.declaration).artifact
        val retained = baseline.symbols.filter { it.symbol.declaration?.artifact == owner }.take(2)
            .mapIndexed { index, symbol -> symbol.copy(score = 100.0 / (index + 1)) }
        assertEquals(2, retained.size)
        val excluded = scope.graph().symbols(SymbolQuery(limit = Int.MAX_VALUE))
            .first { it.declaration?.artifact != null && it.declaration?.artifact != owner }
        val generated = retained.first().symbol.copy(
            id = SymbolId("generated"),
            origin = SymbolOrigin.Generated("test", NodeRef.OfArtifact(owner)),
            declaration = excluded.declaration,
        )
        val invalid = listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).mapIndexed { index, score ->
            RankedSymbol(retained.first().symbol.copy(id = SymbolId("invalid$index")), score)
        }
        val extraSymbols = listOf(generated) + invalid.map { it.symbol }
        val store = scope.engine.graph() as GraphStore
        val batch = store.batches().single { Fact.OfSymbol(retained.first().symbol) in it.facts }
        store.replace(batch.copy(facts = batch.facts + extraSymbols.map { Fact.OfSymbol(it) }))
        scope.memory.forget()
        scope.memory.load()
        val result = baseline.copy(
            files = listOf(RankedFile(owner, 0.02, FileRole.EDIT, emptyList())),
            symbols = retained + RankedSymbol(generated, 25.0) + RankedSymbol(excluded, 1000.0) + invalid,
            tests = emptyList(),
            semantic = SemanticStatus(SemanticState.USED),
        )
        val input = scope.contextInput(result)
        val head = input.candidates.take(4)
        assertEquals(listOf(0.02, 0.02, 0.01, 0.005), head.map { it.relevance })
        assertEquals(
            listOf(NodeRef.OfArtifact(owner)) + retained.map { NodeRef.OfSymbol(it.symbol.id) } +
                NodeRef.OfSymbol(generated.id),
            head.map { it.node },
        )
        assertTrue(head.all { it.role == ContextRole.EDIT })
        assertEquals(listOf(100.0, 50.0), result.symbols.take(2).map { it.score })
    }

    private fun structural(scope: KrosScope): AnalysisResult =
        TaskRetrieval(scope.graph(), scope.searchIndex()).analyze("anchor")

    private fun fixture(test: (SemanticQueryFixture, FakeSemanticComponents, KrosScope) -> Unit) {
        SemanticQueryFixture(root) { directory ->
            SemanticTestWorkspace.create(directory)
            repeat(36) {
                SemanticTestWorkspace.write(directory, "lib/docs/anchor-$it.md", "# Anchor $it\nOriginal example $it\n")
            }
        }.use { f ->
            val fake = FakeSemanticComponents(root.resolve("scratch"))
            val options = OpenOptions(
                semanticIndexing = SemanticIndexingMode.OFF,
                progress = ProgressListener { onProgress(it) },
            )
            KrosScope.open(root, options, { null }, semanticComponents = fake).use { scope ->
                scope.memory.load()
                test(f, fake, scope)
            }
        }
    }
}
