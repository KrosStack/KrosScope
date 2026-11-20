package io.github.krosstack.kscope.service.context

import io.github.krosstack.kscope.context.ContextCompiler
import io.github.krosstack.kscope.context.TokenEstimators
import io.github.krosstack.kscope.model.context.ContextFragment
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.ContextPackage
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.model.context.TokenizerId
import io.github.krosstack.kscope.retrieval.MlMode
import io.github.krosstack.kscope.retrieval.RetrievalStage
import io.github.krosstack.kscope.retrieval.SemanticState
import io.github.krosstack.kscope.retrieval.SemanticStatus
import io.github.krosstack.kscope.retrieval.TaskRetrieval
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.ProgressEvent
import io.github.krosstack.kscope.service.ProgressListener
import io.github.krosstack.kscope.service.QueryStages
import io.github.krosstack.kscope.service.ServiceJson
import io.github.krosstack.kscope.service.context
import io.github.krosstack.kscope.service.contextInput
import io.github.krosstack.kscope.service.contextResult
import io.github.krosstack.kscope.service.readWorkspaceFiles
import io.github.krosstack.kscope.service.semantic.FakeSemanticComponents
import io.github.krosstack.kscope.service.semantic.SemanticComponents
import io.github.krosstack.kscope.service.semantic.SemanticIndexingMode
import io.github.krosstack.kscope.service.semantic.SemanticTestWorkspace
import io.github.krosstack.kscope.service.semantic.WorkerEmbeddingProvider
import io.github.krosstack.kscope.service.semantic.query.SemanticQueryFixture
import io.github.krosstack.kscope.service.semantic.semanticStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SemanticContextServiceTest {
    @TempDir
    lateinit var root: Path
    private val started = CopyOnWriteArrayList<String>()
    private val estimator = TokenEstimators.create()

    @Test
    fun `unbudgeted applied context includes structural sources beyond the fused ten`() {
        fixture { _, _, scope ->
            val applied = scope.analyze("anchor", includeContextEstimates = false)
            val baseline = TaskRetrieval(scope.graph(), scope.searchIndex()).analyze("anchor", ml = MlMode.OFF)
            val head = applied.files.map { it.artifact }.toSet()
            val tail = baseline.files.map { it.artifact }.filter { it !in head }.toSet()
            assertEquals(SemanticState.USED, applied.semantic.state)
            assertEquals(10, head.size)
            assertTrue(tail.isNotEmpty())
            val actual = scope.context(ContextRequest("anchor"))
            assertTrue(actual.fragments.any { it.sourceBacked && it.artifact in tail })
        }
    }

    @Test
    fun `public applied input matches compilation at every level with and without a budget`() {
        fixture { _, _, scope ->
            val applied = scope.analyze("anchor")
            val input = scope.contextInput(applied)
            assertEquals(10, applied.files.size)
            assertEquals(
                ContextLevel.entries.associateWith { compiler(scope).estimate(input, it) },
                applied.contextEstimates,
            )
            for (level in ContextLevel.entries) {
                for (budget in listOf(null, TokenCount(8_000))) {
                    val request = ContextRequest("anchor", budget, level)
                    assertPackageBytes(compiler(scope).compile(input, level, budget), scope.context(request))
                }
            }
        }
    }

    @Test
    fun `ON context uses the same finalized file candidates as analyze`() = fixture { _, fake, scope ->
        val analysis = scope.analyze("anchor", includeContextEstimates = false)
        assertEquals(SemanticState.USED, analysis.semantic.state)
        assertEquals(10, analysis.files.size)
        val request = ContextRequest("anchor", TokenCount(4_000))
        val expected = compiler(scope).compile(scope.contextInput(analysis), request.level, request.budget)
        fake.queriesEmbedded.clear()
        started.clear()
        val actual = scope.contextResult(request)
        assertEquals(ServiceJson.encode(expected), ServiceJson.encode(actual.context))
        assertEquals(analysis.semantic, actual.semantic)
        assertEquals(listOf("anchor"), fake.queriesEmbedded)
        assertEquals(listOf(QueryStages.RANK.id, QueryStages.COMPILE.id), started)
    }

    @Test
    fun `OFF and unavailable context packages retain complete baseline bytes`() = fixture { f, fake, scope ->
        val request = ContextRequest("anchor", TokenCount(4_000), ml = MlMode.OFF)
        val off = TaskRetrieval(scope.graph(), scope.searchIndex()).analyze("anchor", ml = MlMode.OFF)
        val baseline = compiler(scope).compile(off.contextInput(), request.level, request.budget)
        val actual = scope.contextResult(request)
        assertPackageBytes(baseline, actual.context)
        assertEquals(SemanticStatus.OFF, actual.semantic)
        assertTrue(fake.queriesEmbedded.isEmpty())
        for (state in listOf(SemanticState.NOT_INSTALLED, SemanticState.DISABLED)) {
            fake.problem = SemanticComponents.Opening.Unavailable(state, "Unavailable")
            assertPackageBytes(baseline, scope.context(request.copy(ml = MlMode.ON)))
        }
        fake.problem = null
        for (refusal in WorkerEmbeddingProvider.Refusal.entries) {
            fake.queryRefusal = refusal
            assertPackageBytes(baseline, scope.context(request.copy(ml = MlMode.ON)))
        }
        fake.queryRefusal = null
        scope.semantic.invalidate()
        Files.delete(f.directory.resolve("embeddings.json"))
        assertPackageBytes(baseline, scope.context(request.copy(ml = MlMode.ON)))
    }

    @Test
    fun `all four budgets retain source validity and exact package accounting`() = fixture { _, _, scope ->
        for (budget in listOf(2_000, 4_000, 8_000, 16_000)) {
            val compiled = scope.context(ContextRequest("anchor", TokenCount(budget)))
            assertEquals(1, compiled.schemaVersion)
            assertEquals(TokenCount(budget), compiled.budget)
            assertEquals(estimator.count(compiled.markdown), compiled.totalTokens)
            assertTrue(compiled.totalTokens.value <= budget)
            assertTrue(compiled.fragments.any { it.sourceBacked })
            compiled.fragments.filter { it.sourceBacked }.forEach { assertSource(scope, it) }
        }
    }

    @Test
    fun `request status belongs to that compilation rather than a completed indexing job`() =
        fixture { _, fake, scope ->
            scope.scan()
            val indexed = scope.semanticStatus()
            assertEquals(SemanticState.USED, indexed.state)
            val request = ContextRequest("anchor", TokenCount(4_000))
            val applied = scope.contextResult(request)
            assertEquals(SemanticStatus(SemanticState.USED, fake.provider.id, fake.provider.identity), applied.semantic)
            assertNotEquals(indexed, applied.semantic)
            val off = scope.contextResult(request.copy(ml = MlMode.OFF))
            assertEquals(SemanticStatus.OFF, off.semantic)
            fake.queriesEmbedded.clear()
            for (stage in RetrievalStage.entries.filter { it != RetrievalStage.FULL }) {
                val baseline = TaskRetrieval(scope.graph(), scope.searchIndex()).analyze("anchor", stage)
                val actual = scope.contextResult(request.copy(stage = stage))
                val expected = compiler(scope).compile(baseline.contextInput(), request.level, request.budget)
                assertPackageBytes(expected, actual.context)
                assertEquals(baseline.semantic, actual.semantic)
            }
            assertTrue(fake.queriesEmbedded.isEmpty())
            fake.queryRefusal = WorkerEmbeddingProvider.Refusal.DEADLINE_EXPIRED
            val refused = scope.contextResult(request)
            assertEquals(SemanticStatus.NOT_INSTALLED, refused.semantic)
            assertEquals(indexed, scope.semanticStatus())
        }

    @Test
    fun `the five argument context function returns the same package as a default request`() = fixture { _, _, scope ->
        assertPackageBytes(scope.context("anchor"), scope.context(ContextRequest("anchor")))
        val custom = TokenEstimators.create(TokenizerId("chars_4"))
        val expected = scope.context("Lib", TokenCount(2_000), ContextLevel.DEEP, custom, RetrievalStage.LEXICAL)
        val request = ContextRequest("Lib", TokenCount(2_000), ContextLevel.DEEP, custom, RetrievalStage.LEXICAL)
        assertPackageBytes(expected, scope.context(request))
    }

    private fun compiler(scope: KrosScope): ContextCompiler =
        ContextCompiler(scope.graph(), scope.readWorkspaceFiles(), estimator)

    private fun assertPackageBytes(expected: ContextPackage, actual: ContextPackage) {
        assertEquals(ServiceJson.encode(expected), ServiceJson.encode(actual))
        assertEquals(expected.markdown, actual.markdown)
    }

    private fun assertSource(scope: KrosScope, fragment: ContextFragment) {
        val bytes = scope.readWorkspaceFiles().read(fragment.artifact)
        val digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
        val artifact = scope.graph().artifacts().single { it.ref == fragment.artifact }
        assertEquals(artifact.contentHash.value, digest)
        val source = bytes.toString(Charsets.UTF_8)
        val range = requireNotNull(fragment.range)
        val lines = source.split('\n')
        val first = lines.take(range.startLine - 1).sumOf { it.length + 1 } + range.startColumn - 1
        val last = lines.take(range.endLine - 1).sumOf { it.length + 1 } + range.endColumn - 1
        assertEquals(source.substring(first, last), fragment.text)
    }

    private fun fixture(test: (SemanticQueryFixture, FakeSemanticComponents, KrosScope) -> Unit) {
        SemanticQueryFixture(root) { directory ->
            SemanticTestWorkspace.create(directory)
            repeat(36) {
                val text = "# Anchor $it\n" + "Original anchor example with Unicode λ 😀\n".repeat(120)
                SemanticTestWorkspace.write(directory, "lib/docs/anchor-$it.md", text)
            }
        }.use { f ->
            val fake = FakeSemanticComponents(root.resolve("scratch"))
            val options = OpenOptions(
                semanticIndexing = SemanticIndexingMode.FOREGROUND,
                progress = ProgressListener { if (it is ProgressEvent.Started) started += it.stage.id },
            )
            KrosScope.open(root, options, { null }, semanticComponents = fake).use { scope ->
                scope.memory.load()
                test(f, fake, scope)
            }
        }
    }
}
