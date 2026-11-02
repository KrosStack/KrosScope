package io.github.krosstack.kscope.context.fragment

import io.github.krosstack.kscope.context.CharacterEstimator
import io.github.krosstack.kscope.context.ContextFixture
import io.github.krosstack.kscope.context.declaration
import io.github.krosstack.kscope.context.selection.ContextSelector
import io.github.krosstack.kscope.context.sourceArtifact
import io.github.krosstack.kscope.context.token.TokenCosts
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.context.ContextFragment
import io.github.krosstack.kscope.model.context.ContextInput
import io.github.krosstack.kscope.model.context.ContextRendering
import io.github.krosstack.kscope.model.context.ContextRole
import io.github.krosstack.kscope.model.context.TokenCount
import kotlin.test.Test
import kotlin.test.assertEquals

internal class FragmentMergeTest {
    @Test
    fun `raw declarations subsume their covered normalized signatures`() {
        val text = "fun first() = 7\n"
        val file = sourceArtifact("source/Sample", text)
        val symbol = declaration("first", file, TextRange(1, 1, 1, 16), "fun first()")
        val fixture = ContextFixture(listOf(file), listOf(symbol), mapOf(file.ref to text))
        val node = NodeRef.OfSymbol(symbol.id)
        val body = fragment(file, node, "fun first() = 7", symbol.declaration?.range)
        val signature = fragment(file, node, "fun first()", null, ContextRendering.SIGNATURE)
        val merge = FragmentMerge(ContextGraph(fixture), SourceDocuments(fixture), TokenCosts(CharacterEstimator()))

        val normalized = merge.normalize(listOf(signature, body))

        assertEquals(listOf(body), normalized)
    }

    @Test
    fun `fitting full file upgrades retain coverage of contained declarations`() {
        val text = "class Box {\n    fun first() = 7\n}\n"
        val file = sourceArtifact("source/Sample", text)
        val parent = declaration("Box", file, TextRange(1, 1, 4, 1), "class Box")
        val child = declaration("first", file, TextRange(2, 5, 2, 20), "fun first()")
        val fixture = ContextFixture(listOf(file), listOf(parent, child), mapOf(file.ref to text))
        val parentBody = fragment(file, NodeRef.OfSymbol(parent.id), text, parent.declaration?.range)
        val childBody = fragment(file, NodeRef.OfSymbol(child.id), "fun first() = 7", child.declaration?.range)
        val outline = fragment(
            file,
            NodeRef.OfArtifact(file.ref),
            "class Box\nfun first()",
            null,
            ContextRendering.OUTLINE,
        )
        val whole = fragment(file, NodeRef.OfArtifact(file.ref), text, TextRange(1, 1, 4, 1), ContextRendering.FILE)
        val graph = ContextGraph(fixture)
        val merge = FragmentMerge(graph, SourceDocuments(fixture), TokenCosts(CharacterEstimator()))
        val offers = listOf(
            ContextOffer(listOf(parentBody)),
            ContextOffer(listOf(childBody)),
            ContextOffer(listOf(outline, whole)),
        )

        val selected = ContextSelector(merge, CharacterEstimator(), graph)
            .select(ContextInput("fix", emptyList()), offers, TokenCount(1000))

        assertEquals(1, selected.fragments.size)
        assertEquals(ContextRendering.FILE, selected.fragments.single().rendering)
        assertEquals(text, selected.fragments.single().text)
    }

    private fun fragment(
        file: Artifact,
        node: NodeRef,
        text: String,
        range: TextRange?,
        rendering: ContextRendering = ContextRendering.DECLARATION,
    ) = ContextFragment(
        file.ref, range, node, rendering, ContextRole.READ, 1.0, TokenCount(text.length), text, range != null,
    )
}
