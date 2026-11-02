package io.github.krosstack.kscope.retrieval

import io.github.krosstack.kscope.model.BuildRootId
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.GeneratedInfo
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.retrieval.ranking.SemanticFixture
import io.github.krosstack.kscope.retrieval.ranking.retrievalSymbol
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.WorkspaceStructure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GraphQueriesTest {
    @Test
    fun `related uses exact roots and projects generated hits to their source of truth`() {
        val schema = artifact("schema")
        val consumer = artifact("consumer")
        val disconnected = artifact("disconnected")
        val source = retrievalSymbol("Schema", schema)
        val sourceNode = NodeRef.OfSymbol(source.id)
        val generatedFile = artifact("generated").copy(generated = GeneratedInfo("generator", sourceNode))
        val generated = retrievalSymbol(
            "Generated",
            generatedFile,
            origin = SymbolOrigin.Generated("generator", sourceNode),
        )
        val consumerSymbol = retrievalSymbol("Consumer", consumer)
        val generatedNode = NodeRef.OfSymbol(generated.id)
        val generation = relationship(sourceNode, generatedNode, RelationKind.GENERATES)
        val consumption = relationship(NodeRef.OfSymbol(consumerSymbol.id), generatedNode, RelationKind.REFERENCES)
        val graph = SemanticFixture(
            listOf(schema, consumer, generatedFile, disconnected),
            listOf(source, generated, consumerSymbol),
            listOf(generation, consumption),
        )
        val result = GraphQueries(graph).related("Generated")

        assertEquals(listOf(generatedNode), result.roots)
        assertTrue(result.files.any { it.artifact == schema.ref && it.score > 0.0 })
        assertTrue(result.files.any { it.artifact == consumer.ref && it.score > 0.0 })
        assertFalse(result.files.any { it.artifact == generatedFile.ref || it.artifact == disconnected.ref })
        assertTrue(result.files.all { file -> file.reasons.single().signal == SignalKind.GRAPH })
        assertTrue(result.symbols.any { it.symbol.id == consumerSymbol.id })
        assertEquals(setOf(generation, consumption), result.relationships.toSet())
        assertFalse(result.truncated)
        assertEquals(emptyList(), GraphQueries(graph).related("Generate").roots)
    }

    @Test
    fun `neighborhood depth walks persisted edges and retains unresolved edges from visited sources`() {
        val files = listOf("a", "b", "c", "d").map(::artifact)
        val chain = listOf(relationship(node("a"), node("b")), relationship(node("b"), node("c")))
        val unresolved = relationship(node("b"), node("d")).copy(
            resolution = Resolution.Unresolved("Missing", ReasonCode("test.missing")),
        )
        val queries = GraphQueries(RetrievalFixture(files, chain + unresolved))
        val result = queries.graph(GraphScope.NEIGHBORHOOD, "a", depth = 1)

        assertEquals(listOf(node("a"), node("b")), result.nodes.map { it.ref })
        assertEquals(setOf(chain[0], unresolved), result.relationships.toSet())
        assertFalse(result.truncated)
        assertEquals(listOf(node("a")), queries.graph(GraphScope.NEIGHBORHOOD, "a", depth = 0).nodes.map { it.ref })
        assertEquals(emptyList(), queries.graph(GraphScope.NEIGHBORHOOD, "absent").nodes)
    }

    @Test
    fun `workspace graph exposes only persisted relationships without synthesizing membership edges`() {
        val file = artifact("source")
        val symbol = retrievalSymbol("Declared", file)
        val graph = SemanticFixture(listOf(file), listOf(symbol))
        val workspace = GraphQueries(graph).graph(GraphScope.WORKSPACE)
        val neighborhood = GraphQueries(graph).graph(GraphScope.NEIGHBORHOOD, "source")

        assertEquals(
            setOf(NodeRef.OfArtifact(file.ref), NodeRef.OfSymbol(symbol.id)),
            workspace.nodes.map { it.ref }.toSet(),
        )
        assertTrue(workspace.relationships.isEmpty())
        assertEquals(listOf(NodeRef.OfArtifact(file.ref)), neighborhood.nodes.map { it.ref })
        assertTrue(neighborhood.relationships.isEmpty())
    }

    @Test
    fun `module graph preserves recorded dependency evidence and excludes file relationships`() {
        val first = module("first")
        val second = module("second")
        val edge = relationship(NodeRef.OfModule(first.id), NodeRef.OfModule(second.id), RelationKind.DEPENDS_ON)
            .copy(evidenceClass = EvidenceClass.BUILD_MODEL)
        val fileEdge = relationship(node("a"), node("b"))
        val structure = WorkspaceStructure(
            emptyList(),
            emptyList(),
            listOf(second, first),
            emptyList(),
            emptyList(),
            emptyList(),
        )
        val fixture = RetrievalFixture(listOf(artifact("a"), artifact("b")), listOf(fileEdge, edge))
        val reader = object : GraphReader by fixture {
            override fun structure() = structure
        }
        val result = GraphQueries(reader).graph()

        assertEquals(listOf(NodeRef.OfModule(first.id), NodeRef.OfModule(second.id)), result.nodes.map { it.ref })
        assertEquals(listOf(edge), result.relationships)
        assertFalse(result.truncated)
    }

    @Test
    fun `graph node and relationship limits report deterministic truncation independently`() {
        val files = listOf("a", "b", "c").map(::artifact)
        val edges = listOf(relationship(node("a"), node("b")), relationship(node("b"), node("c")))
        val forward = GraphQueries(RetrievalFixture(files, edges)).graph(GraphScope.WORKSPACE, limit = 1)
        val reversed = GraphQueries(RetrievalFixture(files.reversed(), edges.reversed()))
            .graph(GraphScope.WORKSPACE, limit = 1)
        val multiple = edges.mapIndexed { index, edge ->
            edge.copy(from = node("a"), resolution = Resolution.Unresolved("Missing$index", ReasonCode("test.missing")))
        }
        val unresolvedGraph = RetrievalFixture(listOf(files[0]), multiple)
        val edgeLimited = GraphQueries(unresolvedGraph).graph(GraphScope.WORKSPACE, limit = 1)

        assertEquals(forward, reversed)
        assertEquals(listOf(node("a")), forward.nodes.map { it.ref })
        assertTrue(forward.relationships.isEmpty())
        assertTrue(forward.truncated)
        assertEquals(1, edgeLimited.relationships.size)
        assertTrue(edgeLimited.truncated)
    }

    private fun module(name: String) = Module(
        ModuleId("test//:$name"),
        BuildRootId("test//"),
        ":$name",
        RepoPath(name),
        emptyList(),
        emptySet(),
        null,
        EvidenceClass.BUILD_MODEL,
        Certainty.EXACT,
    )
}
