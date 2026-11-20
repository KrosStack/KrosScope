package io.github.krosstack.kscope.service.semantic.query

import io.github.krosstack.kscope.context.ContextCompiler
import io.github.krosstack.kscope.context.TokenEstimators
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.GeneratedInfo
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.context.ContextCandidate
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.ContextRole
import io.github.krosstack.kscope.retrieval.FileRole
import io.github.krosstack.kscope.retrieval.RankedSymbol
import io.github.krosstack.kscope.retrieval.SemanticState
import io.github.krosstack.kscope.retrieval.SemanticStatus
import io.github.krosstack.kscope.retrieval.TaskRetrieval
import io.github.krosstack.kscope.retrieval.ranking.FileRankFusion
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.contextInput
import io.github.krosstack.kscope.service.readWorkspaceFiles
import io.github.krosstack.kscope.service.semantic.FakeSemanticComponents
import io.github.krosstack.kscope.service.semantic.SemanticTestWorkspace
import io.github.krosstack.kscope.service.withContextEstimates
import io.github.krosstack.kscope.spi.graph.GraphStore
import io.github.krosstack.kscope.spi.graph.SymbolQuery
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AnalyzeSemanticGeneratedContextTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `applied context retains symbols through generated artifacts in estimates`() = fixture { scope ->
        val source = scope.graph().artifacts().single { it.ref.path.value == SemanticTestWorkspace.LIB }.ref
        val symbol = generatedSymbol(scope, source)
        val retrieval = TaskRetrieval(scope.graph(), scope.searchIndex())
        val baseline = retrieval.analyze("Lib").copy(symbols = listOf(RankedSymbol(symbol, 100.0)))
        val result = retrieval.withFileRanking(baseline, FileRankFusion.fuse(emptyList(), listOf(source)))
            .copy(semantic = SemanticStatus(SemanticState.USED))
        assertEquals(listOf(symbol), result.symbols.map { it.symbol })
        val file = result.files.single()
        val role = if (file.role == FileRole.EDIT) ContextRole.EDIT else ContextRole.READ
        val node = NodeRef.OfSymbol(symbol.id)
        val expected = scope.contextInput(result.copy(symbols = emptyList())).let {
            it.copy(
                candidates = it.candidates.take(1) + ContextCandidate(node, file.score, role) +
                    it.candidates.drop(1).filterNot { candidate -> candidate.node == node },
            )
        }
        val compiler = ContextCompiler(scope.graph(), scope.readWorkspaceFiles(), TokenEstimators.create())
        val estimates = ContextLevel.entries.associateWith { compiler.estimate(expected, it) }
        val withoutSymbol = ContextLevel.entries.associateWith {
            compiler.estimate(scope.contextInput(result.copy(symbols = emptyList())), it)
        }
        assertNotEquals(withoutSymbol, estimates)
        for (state in listOf(SemanticState.USED, SemanticState.BEHIND)) {
            val applied = result.copy(semantic = SemanticStatus(state))
            val input = scope.contextInput(applied)
            assertEquals(expected, input)
            assertEquals(ContextCandidate(node, file.score, role), input.candidates.single { it.node == node })
            assertEquals(estimates, scope.withContextEstimates(applied).contextEstimates)
        }
    }

    private fun generatedSymbol(scope: KrosScope, source: ArtifactRef): Symbol {
        val artifact = scope.graph().artifacts().single { it.ref.path.value == SemanticTestWorkspace.OTHER }
        val original = scope.graph().symbols(SymbolQuery(artifact = source)).first()
        val symbol = original.copy(
            origin = SymbolOrigin.Generated("test", NodeRef.OfArtifact(artifact.ref)),
            declaration = requireNotNull(original.declaration).copy(artifact = artifact.ref),
        )
        val replacements = mapOf<Fact, Fact>(
            Fact.OfArtifact(artifact) to Fact.OfArtifact(
                artifact.copy(generated = GeneratedInfo("test", NodeRef.OfArtifact(source))),
            ),
            Fact.OfSymbol(original) to Fact.OfSymbol(symbol),
        )
        val store = scope.engine.graph() as GraphStore
        store.batches().filter { batch -> batch.facts.any { it in replacements } }.forEach { batch ->
            store.replace(batch.copy(facts = batch.facts.map { replacements[it] ?: it }))
        }
        scope.memory.forget()
        scope.memory.load()
        return symbol
    }

    private fun fixture(test: (KrosScope) -> Unit) {
        SemanticQueryFixture(root) { directory ->
            SemanticTestWorkspace.create(directory)
            repeat(36) {
                SemanticTestWorkspace.write(directory, "lib/docs/anchor-$it.md", "# Anchor $it\nOriginal example $it\n")
            }
        }.use {
            val fake = FakeSemanticComponents(root.resolve("scratch"))
            KrosScope.open(root, OpenOptions(), { null }, semanticComponents = fake).use { scope ->
                scope.memory.load()
                test(scope)
            }
        }
    }
}
