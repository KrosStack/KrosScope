package io.github.krosstack.kscope.context.fragment

import io.github.krosstack.kscope.context.CharacterEstimator
import io.github.krosstack.kscope.context.ContextFixture
import io.github.krosstack.kscope.context.sourceArtifact
import io.github.krosstack.kscope.context.token.FragmentCosts
import io.github.krosstack.kscope.context.token.TokenCosts
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.context.ContextFragment
import io.github.krosstack.kscope.model.context.ContextRendering
import io.github.krosstack.kscope.model.context.ContextRole
import io.github.krosstack.kscope.model.context.TokenCount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

internal class FragmentMergeMemoTest {
    private val text = "abcdefghijklmnopqrstuvwxyz"
    private val file = sourceArtifact("source/Alphabet", text)
    private val fixture = ContextFixture(listOf(file), emptyList(), mapOf(file.ref to text))
    private val costs = RecordingFragmentCosts()
    private val merge = FragmentMerge(ContextGraph(fixture), SourceDocuments(fixture), costs)
    private val fragments = listOf(fragment(file, text, 2, 12), fragment(file, text, 8, 20))

    @Test
    fun `repeated overlapping merges skip counting`() {
        merge.normalize(fragments)
        merge.normalize(fragments.map { it.copy(text = String(it.text.toCharArray())) })

        assertEquals(1, costs.counted.size)
    }

    @Test
    fun `repeated overlapping merges reuse the source string`() {
        val first = merge.normalize(fragments).single()
        val repeated = merge.normalize(fragments.map { it.copy(text = String(it.text.toCharArray())) }).single()

        assertSame(first.text, repeated.text)
    }

    @Test
    fun `equal content in different documents preserves uncached text in distinct memo entries`() {
        val other = sourceArtifact("source/OtherAlphabet", text)
        val workspace = ContextFixture(listOf(file, other), emptyList(), mapOf(file.ref to text, other.ref to text))
        val graph = ContextGraph(workspace)
        val documents = SourceDocuments(workspace)
        val shared = FragmentMerge(graph, documents, costs)
        val inputs = listOf(
            fragments,
            fragments.map { it.copy(artifact = other.ref, node = NodeRef.OfArtifact(other.ref)) },
        )
        val uncached = inputs.map {
            FragmentMerge(graph, documents, TokenCosts(CharacterEstimator())).normalize(it).single().text
        }

        val merged = inputs.map { shared.normalize(it).single().text }
        inputs.forEach { shared.normalize(it) }

        assertEquals(uncached to 2, merged to costs.counted.size)
    }

    @Test
    fun `changed merged range bounds count distinct source spans once`() {
        val shifted = listOf(fragment(file, text, 4, 12), fragment(file, text, 8, 20))
        val widened = fragments + fragment(file, text, 18, 24)

        listOf(fragments, shifted, widened).forEach {
            merge.normalize(it)
            merge.normalize(it)
        }

        assertEquals(
            listOf("bcdefghijklmnopqrst", "defghijklmnopqrst", "bcdefghijklmnopqrstuvwx"),
            costs.counted.map { it.second },
        )
    }

    @Test
    fun `changed rendering counts a distinct source span once`() {
        listOf(ContextRendering.DECLARATION, ContextRendering.ENTRY).forEach { rendering ->
            val rendered = fragments.map { it.copy(rendering = rendering) }
            merge.normalize(rendered)
            merge.normalize(rendered)
        }

        assertEquals(listOf(ContextRendering.DECLARATION, ContextRendering.ENTRY), costs.counted.map { it.first })
    }

    private fun fragment(file: Artifact, text: String, start: Int, end: Int): ContextFragment = ContextFragment(
        file.ref,
        TextRange(1, start, 1, end + 1),
        NodeRef.OfArtifact(file.ref),
        ContextRendering.DECLARATION,
        ContextRole.READ,
        1.0,
        TokenCount(end - start + 1),
        text.substring(start - 1, end),
        true,
    )

    private class RecordingFragmentCosts : FragmentCosts {
        val counted = mutableListOf<Pair<ContextRendering, String>>()

        override fun count(
            hash: ContentHash,
            range: TextRange?,
            rendering: ContextRendering,
            text: String,
        ): TokenCount {
            counted.add(rendering to text)
            return TokenCount(text.length)
        }
    }
}
