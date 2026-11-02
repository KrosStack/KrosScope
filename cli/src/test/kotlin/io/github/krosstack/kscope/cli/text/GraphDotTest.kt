package io.github.krosstack.kscope.cli.text

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.retrieval.GraphNode
import io.github.krosstack.kscope.retrieval.GraphResult
import io.github.krosstack.kscope.retrieval.GraphScope
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GraphDotTest {
    @Test
    fun `DOT quotes labels and unresolved targets while preserving evidence and truncation`() {
        val node = NodeRef.OfArtifact(ArtifactRef(RepositoryId("sample"), RepoPath("source")))
        val graphNode = GraphNode(node, "a\"b\\c\nd\u0000")
        val edge = Relationship(
            node,
            RelationKind.REFERENCES,
            null,
            Resolution.Unresolved("Missing\"Class", ReasonCode("test.missing")),
            EvidenceClass.TEXTUAL,
            null,
            Scope(),
        )
        val result = GraphResult(GraphScope.NEIGHBORHOOD, listOf(graphNode), listOf(edge), true)
        val rendered = GraphDot.render(result)

        assertTrue(rendered.startsWith("digraph kscope {"))
        assertTrue(rendered.contains("a\\\"b\\\\c\\nd"), rendered)
        assertTrue(rendered.contains("Missing\\\"Class"), rendered)
        assertTrue(rendered.contains("textual"), rendered)
        assertTrue(rendered.contains("unresolved:test.missing"), rendered)
        assertTrue(rendered.contains("truncated=true"), rendered)
        assertFalse('\u0000' in rendered)
    }
}
