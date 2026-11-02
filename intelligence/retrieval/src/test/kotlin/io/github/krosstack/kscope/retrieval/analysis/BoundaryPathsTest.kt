package io.github.krosstack.kscope.retrieval.analysis

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.retrieval.RetrievalFixture
import io.github.krosstack.kscope.retrieval.RetrievalGraph
import io.github.krosstack.kscope.retrieval.artifact
import io.github.krosstack.kscope.retrieval.node
import io.github.krosstack.kscope.retrieval.relationship
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class BoundaryPathsTest {
    @Test
    fun `two hop generated boundary chains preserve evidence for each hop`() {
        val source = artifact("A.schema")
        val consumer = artifact("C.kt")
        val generates = relationship(node("A.schema"), node("B.generated"), RelationKind.GENERATES).copy(
            evidenceClass = EvidenceClass.CONVENTION,
        )
        val binds = relationship(node("B.generated"), node("C.kt"), RelationKind.BINDS, Certainty.PROBABLE)
        val graph = RetrievalGraph(RetrievalFixture(listOf(source, consumer), listOf(generates, binds)))

        assertEquals(
            listOf(listOf(generates, binds)),
            BoundaryPaths(graph).between(setOf(source.ref, consumer.ref)).paths,
        )
    }

    @Test
    fun `reverse traversal retains the original relationship orientation`() {
        val first = artifact("A.kt")
        val second = artifact("B.kt")
        val edge = relationship(node("B.kt"), node("A.kt"), RelationKind.ACTUALIZES)
        val graph = RetrievalGraph(RetrievalFixture(listOf(first, second), listOf(edge)))

        assertEquals(listOf(listOf(edge)), BoundaryPaths(graph).between(setOf(first.ref, second.ref)).paths)
    }

    @Test
    fun `large boundary neighborhoods report truncated paths`() {
        val root = artifact("A.kt")
        val files = (1..120).map { artifact("B$it.kt") }
        val edges = files.map { relationship(node("A.kt"), node(it.ref.path.value), RelationKind.BINDS) }
        val graph = RetrievalGraph(RetrievalFixture(listOf(root) + files, edges))
        val result = BoundaryPaths(graph).between((listOf(root) + files).map { it.ref }.toSet())

        assertTrue(result.paths.isNotEmpty())
        assertTrue(result.paths.size <= 100)
        assertTrue(result.truncated)
    }

    @Test
    fun `unresolved and not analysed boundaries cannot form chains`() {
        val source = artifact("A.schema")
        val consumer = artifact("C.kt")
        val resolved = relationship(node("A.schema"), node("C.kt"), RelationKind.BINDS)
        val unresolved = resolved.copy(resolution = Resolution.Unresolved("C.kt", ReasonCode("test.missing")))
        val unknown = resolved.copy(resolution = Resolution.NotAnalysed("C.kt", ReasonCode("test.unknown")))
        val ordinary = resolved.copy(kind = RelationKind.CALLS)
        val graph = RetrievalGraph(RetrievalFixture(listOf(source, consumer), listOf(unresolved, unknown, ordinary)))

        assertTrue(BoundaryPaths(graph).between(setOf(source.ref, consumer.ref)).paths.isEmpty())
    }

    @Test
    fun `paths are stable cycle safe and limited to three hops`() {
        val files = ('A'..'E').map { artifact("$it.kt") }
        val edges = files.zipWithNext().map { (from, to) ->
            relationship(node(from.ref.path.value), node(to.ref.path.value), RelationKind.ACTUALIZES)
        }
        val cycle = relationship(node("C.kt"), node("B.kt"), RelationKind.BINDS)
        val graph = RetrievalGraph(RetrievalFixture(files, edges + cycle))
        val reverseGraph = RetrievalGraph(RetrievalFixture(files.reversed(), (edges + cycle).reversed()))
        val selected = setOf(files.first().ref, files[3].ref, files.last().ref)
        val paths = BoundaryPaths(graph).between(selected).paths

        assertTrue(paths.any { it == edges.take(3) })
        assertTrue(paths.none { it.size > 3 })
        assertTrue(paths.none { path -> path.contains(edges.first()) && path.contains(edges.last()) })
        assertEquals(paths, BoundaryPaths(reverseGraph).between(selected.reversed().toSet()).paths)
        assertTrue(BoundaryPaths(graph).between(setOf(files.first().ref)).paths.isEmpty())
    }
}
