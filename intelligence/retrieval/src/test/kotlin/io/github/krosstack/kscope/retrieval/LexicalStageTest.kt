package io.github.krosstack.kscope.retrieval

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.spi.search.SearchHit
import kotlin.test.Test
import kotlin.test.assertEquals

class LexicalStageTest {
    @Test
    fun `lexical ranking excludes files reached only through structural expansion`() {
        val files = listOf(artifact("Root.kt"), artifact("Other.kt"))
        val graph = RetrievalFixture(files, listOf(relationship(node("Root.kt"), node("Other.kt"))))
        val index = RetrievalIndex(setOf("root"), listOf(SearchHit(node("Root.kt"), 1.0, listOf("root"), emptyMap())))
        val retrieval = TaskRetrieval(graph, index)
        val rankings = listOf(RetrievalStage.LEXICAL, RetrievalStage.FULL).map { stage ->
            retrieval.analyze("root", stage).files.map { it.artifact.path.value }
        }

        assertEquals(listOf(listOf("Root.kt"), listOf("Root.kt", "Other.kt")), rankings)
    }

    @Test
    fun `lexical analysis reports semantic off even when ml is on`() {
        val result = TaskRetrieval(RetrievalFixture(), RetrievalIndex()).analyze("", RetrievalStage.LEXICAL, MlMode.ON)

        assertEquals(SemanticStatus.OFF, result.semantic)
    }

    @Test
    fun `lexical analysis preserves lexical order when structural fusion prefers another candidate`() {
        val paths = listOf("First.kt", "Second.kt", "Third.kt")
        val edges = listOf(
            relationship(node("First.kt"), node("Second.kt"), certainty = Certainty.HEURISTIC),
            relationship(node("Third.kt"), node("Second.kt"), RelationKind.IMPLEMENTS),
        )
        val hits = paths.mapIndexed { index, path ->
            SearchHit(node(path), 3.0 - index, listOf("candidate"), emptyMap())
        }
        val retrieval = TaskRetrieval(RetrievalFixture(paths.map(::artifact), edges), RetrievalIndex(hits = hits))
        val rankings = listOf(RetrievalStage.LEXICAL, RetrievalStage.FULL).map { stage ->
            retrieval.analyze("candidate", stage).files.map { it.artifact.path.value }
        }

        assertEquals(listOf(paths, listOf("Second.kt", "First.kt", "Third.kt")), rankings)
    }
}
