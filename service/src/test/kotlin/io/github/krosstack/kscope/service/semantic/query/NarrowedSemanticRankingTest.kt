package io.github.krosstack.kscope.service.semantic.query

import io.github.krosstack.kscope.retrieval.MlMode
import io.github.krosstack.kscope.retrieval.RetrievalStage
import io.github.krosstack.kscope.retrieval.SemanticState
import io.github.krosstack.kscope.retrieval.TaskRetrieval
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.TestGit
import io.github.krosstack.kscope.service.lookup.narrowingFor
import io.github.krosstack.kscope.service.semantic.FakeSemanticComponents
import io.github.krosstack.kscope.service.semantic.SemanticIndexingMode
import io.github.krosstack.kscope.service.semantic.SemanticTestWorkspace
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NarrowedSemanticRankingTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `semantic matches of other repositories are left out of a narrowed answer`() {
        fixture { scope ->
            val first = assertNotNull(scope.narrowingFor("first")).repository
            val everywhere = scope.analyze("anchor", includeContextEstimates = false)
            val inside = scope.analyze("anchor", includeContextEstimates = false, within = first)

            assertEquals(SemanticState.USED, everywhere.semantic.state)
            assertEquals(SemanticState.USED, inside.semantic.state)
            assertTrue(everywhere.files.any { it.artifact.repository != first })
            assertTrue(inside.files.isNotEmpty())
            assertTrue(inside.files.all { it.artifact.repository == first })
        }
    }

    @Test
    fun `the filter is what removes the semantic matches of other repositories`() {
        fixture { scope ->
            val first = assertNotNull(scope.narrowingFor("first")).repository
            val structural = TaskRetrieval(scope.graph(), scope.searchIndex()).analyze("Other", within = first)
            val unfiltered = scope.withSemanticRanking(structural, RetrievalStage.FULL, MlMode.ON)
            val filtered = scope.withSemanticRanking(structural, RetrievalStage.FULL, MlMode.ON, within = first)

            assertEquals(SemanticState.USED, filtered.semantic.state)
            assertTrue(unfiltered.files.any { it.artifact.repository != first })
            assertTrue(filtered.files.all { it.artifact.repository == first })
        }
    }

    private fun fixture(test: (KrosScope) -> Unit) {
        assumeTrue(TestGit.available(), "git is not available")
        SemanticQueryFixture(root) { directory ->
            listOf("first", "second").forEach { name ->
                SemanticTestWorkspace.create(directory.resolve(name))
                repeat(DOCUMENTS) {
                    SemanticTestWorkspace.write(
                        directory,
                        "$name/lib/docs/anchor-$it.md",
                        "# Anchor $it\nExample of $name $it\n",
                    )
                }
                TestGit.run(directory.resolve(name), listOf("init", "-q"))
                TestGit.run(directory.resolve(name), listOf("add", "-A"))
                TestGit.run(directory.resolve(name), listOf("commit", "-q", "-m", "initial"))
            }
        }.use {
            val options = OpenOptions(semanticIndexing = SemanticIndexingMode.OFF)
            val fake = FakeSemanticComponents(root.resolve("scratch"))
            KrosScope.open(root, options, { null }, semanticComponents = fake).use { scope ->
                scope.memory.load()
                test(scope)
            }
        }
    }

    private companion object {
        const val DOCUMENTS = 12
    }
}
