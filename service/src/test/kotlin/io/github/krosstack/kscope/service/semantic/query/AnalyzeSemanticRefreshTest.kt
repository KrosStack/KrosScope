package io.github.krosstack.kscope.service.semantic.query

import io.github.krosstack.kscope.retrieval.AnalysisResult
import io.github.krosstack.kscope.retrieval.TaskRetrieval
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.ServiceJson
import io.github.krosstack.kscope.service.semantic.FakeSemanticComponents
import io.github.krosstack.kscope.service.semantic.SemanticIndexingMode
import io.github.krosstack.kscope.service.semantic.SemanticTestWorkspace
import io.github.krosstack.kscope.service.wiring.TokenCountFile
import io.github.krosstack.kscope.service.wiring.TokenCountFileBytes
import io.github.krosstack.kscope.service.withContextEstimates
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AnalyzeSemanticRefreshTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `a semantic query refresh reloads token counts before fallback estimates`() = fixture { f, fake, scope ->
        var expected = scope.withContextEstimates(structural(scope))
        val file = f.directory.resolve(TokenCountFile.FILE_NAME)
        val counts = TokenCountFileBytes.read(file)
        TokenCountFileBytes.write(file, counts.mapValues { (_, count) -> count + 100 })
        requireNotNull(scope.tokenCounts).reload()
        val stale = scope.withContextEstimates(structural(scope))
        assertNotEquals(expected.contextEstimates, stale.contextEstimates)
        TokenCountFileBytes.write(file, counts)
        fake.onQuery = {
            SemanticTestWorkspace.write(root, "lib/docs/unrelated.md", "# Unrelated\nAdded documentation\n")
            KrosScope.open(root, OpenOptions(tokenCache = false)).use { updated ->
                updated.scan()
                expected = updated.withContextEstimates(structural(updated))
            }
        }
        val revision = scope.memory.revision
        val actual = scope.analyze("anchor")
        assertTrue(scope.memory.revision > revision)
        assertEquals(ServiceJson.encode(expected), ServiceJson.encode(actual))
        assertNotEquals(stale.contextEstimates, actual.contextEstimates)
        assertEquals(listOf("anchor"), fake.queriesEmbedded)
        assertEquals(1, fake.closed)
        f.assertNoLeases()
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
            val options = OpenOptions(semanticIndexing = SemanticIndexingMode.OFF)
            KrosScope.open(root, options, { null }, semanticComponents = fake).use { scope ->
                scope.memory.load()
                test(f, fake, scope)
            }
        }
    }
}
