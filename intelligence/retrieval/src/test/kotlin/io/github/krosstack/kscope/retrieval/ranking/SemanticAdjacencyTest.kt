package io.github.krosstack.kscope.retrieval.ranking

import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.retrieval.RetrievalGraph
import io.github.krosstack.kscope.retrieval.artifact
import io.github.krosstack.kscope.retrieval.relationship
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class SemanticAdjacencyTest {
    @Test
    fun `file seeds reach their declarations without stored containment edges`() {
        val file = artifact("source/Receiver.kt")
        val receiver = retrievalSymbol("Receiver", file)
        val method = retrievalSymbol("Receiver.receive", file, container = receiver.id)
        val graph = RetrievalGraph(SemanticFixture(listOf(file), listOf(receiver, method)))

        val scores = GraphRanking(graph).rank(mapOf(NodeRef.OfArtifact(file.ref) to 1.0))

        assertTrue((scores[NodeRef.OfSymbol(receiver.id)] ?: 0.0) > 0.0)
        assertTrue((scores[NodeRef.OfSymbol(method.id)] ?: 0.0) > 0.0)
        assertTrue(graph.edges.isEmpty())
    }

    @Test
    fun `member seeds reach the contract bound to their containing declaration`() {
        val source = artifact("source/Receiver.kt")
        val manifest = artifact("configuration/Components.xml")
        val receiver = retrievalSymbol("Receiver", source)
        val method = retrievalSymbol("Receiver.receive", source, container = receiver.id)
        val component = retrievalSymbol("DeclaredReceiver", manifest)
        val binding = relationship(NodeRef.OfSymbol(component.id), NodeRef.OfSymbol(receiver.id), RelationKind.BINDS)
        val graph = RetrievalGraph(
            SemanticFixture(listOf(source, manifest), listOf(receiver, method, component), listOf(binding)),
        )

        val scores = GraphRanking(graph).rank(mapOf(NodeRef.OfSymbol(method.id) to 1.0))

        assertTrue((scores[NodeRef.OfSymbol(component.id)] ?: 0.0) > 0.0)
    }

    @Test
    fun `module containment does not spread relevance to unrelated source files`() {
        val first = artifact("source/First.kt")
        val second = artifact("source/Second.kt")
        val module = NodeRef.OfModule(ModuleId("repo//:library"))
        val edges = listOf(
            relationship(module, NodeRef.OfArtifact(first.ref), RelationKind.CONTAINS),
            relationship(module, NodeRef.OfArtifact(second.ref), RelationKind.CONTAINS),
        )
        val graph = RetrievalGraph(SemanticFixture(listOf(first, second), emptyList(), edges))

        val scores = GraphRanking(graph).rank(mapOf(NodeRef.OfArtifact(first.ref) to 1.0))

        assertFalse(module in scores)
        assertFalse(NodeRef.OfArtifact(second.ref) in scores)
    }
}
