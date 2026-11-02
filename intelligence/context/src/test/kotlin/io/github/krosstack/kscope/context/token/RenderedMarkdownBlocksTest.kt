package io.github.krosstack.kscope.context.token

import io.github.krosstack.kscope.context.ContextMarkdown
import io.github.krosstack.kscope.context.sourceArtifact
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.context.ContextFragment
import io.github.krosstack.kscope.model.context.ContextRendering
import io.github.krosstack.kscope.model.context.ContextRole
import io.github.krosstack.kscope.model.context.TokenCount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

internal class RenderedMarkdownBlocksTest {
    private val file = sourceArtifact("source/Example", "val message = \"α🙂漢字 ```\"\n")
    private val fragment = ContextFragment(
        file.ref, TextRange(1, 1, 2, 1), NodeRef.OfArtifact(file.ref), ContextRendering.FILE,
        ContextRole.READ, 1.0, TokenCount(10), "val message = \"α🙂漢字 ```\"\n", true,
    )

    @Test
    fun `score only changes reuse the identical immutable rendered block`() {
        val memo = RenderedMarkdownBlocks()
        val first = memo.fragment(fragment)
        val changed = fragment.copy(
            relevance = 0.1,
            role = ContextRole.EDIT,
            node = NodeRef.OfSymbol(SymbolId("different")),
            tokens = TokenCount(0),
            sourceBacked = false,
        )

        assertSame(first, memo.fragment(changed))
        assertEquals(ContextMarkdown.blocks("", listOf(changed)).single(), first)
    }

    @Test
    fun `every rendered field invalidates stale Markdown while preserving canonical bytes`() {
        val changed = listOf(
            fragment.copy(artifact = file.ref.copy(path = RepoPath("source/Other"))),
            fragment.copy(range = TextRange(3, 1, 4, 1)),
            fragment.copy(rendering = ContextRendering.DECLARATION),
            fragment.copy(text = "a `````` fence\n\n"),
            fragment.copy(evidenceNote = "heuristic structural evidence"),
        )
        val memo = RenderedMarkdownBlocks()
        memo.fragment(fragment)
        for (value in changed) {
            assertEquals(ContextMarkdown.blocks("", listOf(value)).single(), memo.fragment(value))
        }
    }
}
