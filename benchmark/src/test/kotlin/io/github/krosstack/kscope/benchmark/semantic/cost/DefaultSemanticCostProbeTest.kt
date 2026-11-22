package io.github.krosstack.kscope.benchmark.semantic.cost

import io.github.krosstack.kscope.benchmark.context.ContextAnalysisIdentityTest.Fixture
import io.github.krosstack.kscope.retrieval.SemanticState
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.semantic.SemanticIndexingMode
import io.github.krosstack.kscope.service.semantic.semanticNotice
import io.github.krosstack.kscope.service.semantic.semanticStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultSemanticCostProbeTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `default advance recognizes publishing with no notice as active work`() = Fixture(root).use { f ->
        f.fake.open(f.root, OpenOptions(semanticIndexing = SemanticIndexingMode.DEFERRED)).use { scope ->
            scope.scan()
            val advance = SemanticCostProbe.Dependencies().advance
            var active: Boolean
            do {
                active = advance(scope)
            } while (scope.semanticNotice() != null)
            assertEquals(SemanticState.USED, scope.semanticStatus().state)
            assertEquals(0, scope.semanticStatus().behindChunks)
            assertTrue(active)
            assertFalse(advance(scope))
        }
    }
}
