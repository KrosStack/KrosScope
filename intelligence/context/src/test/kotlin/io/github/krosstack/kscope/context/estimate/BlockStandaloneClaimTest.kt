package io.github.krosstack.kscope.context.estimate

import io.github.krosstack.kscope.context.ContextMarkdown
import io.github.krosstack.kscope.context.token.BpeBlockBoundary
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.context.ContextFragment
import io.github.krosstack.kscope.model.context.ContextRendering
import io.github.krosstack.kscope.model.context.ContextRole
import io.github.krosstack.kscope.model.context.TokenCount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class BlockStandaloneClaimTest {
    @Test
    fun `a fragment block is standalone exactly when its printed path note and text are valid Unicode`() {
        val fragments = fragments()

        val disagreements = fragments.filter { claimed(it) != built(it) }

        assertEquals(EXPECTED_FRAGMENTS, fragments.size)
        assertEquals(emptyList(), disagreements)
    }

    @Test
    fun `the fragments checked hold blocks that are standalone and blocks that are not`() {
        val standalone = fragments().count { claimed(it) }

        assertTrue(standalone > 0)
        assertTrue(standalone < EXPECTED_FRAGMENTS)
    }

    private fun built(fragment: ContextFragment): Boolean =
        BpeBlockBoundary.standalone(ContextMarkdown.fragment(fragment))

    private fun claimed(fragment: ContextFragment): Boolean =
        BlockLayout.standalone(ContextMarkdown.printedPath(fragment.artifact), fragment.evidenceNote, fragment.text)

    private fun fragments(): List<ContextFragment> = ContextRendering.entries.flatMap { rendering ->
        RANGES.flatMap { range ->
            PATHS.flatMap { path ->
                NOTES.flatMap { note -> TEXTS.map { text -> fragment(path, range, rendering, text, note) } }
            }
        }
    }

    private fun fragment(
        path: String,
        range: TextRange?,
        rendering: ContextRendering,
        text: String,
        note: String?,
    ): ContextFragment = ContextFragment(
        ArtifactRef(RepositoryId("sample"), RepoPath(path)),
        range,
        NodeRef.OfSymbol(SymbolId("sample")),
        rendering,
        ContextRole.READ,
        1.0,
        TokenCount(0),
        text,
        range != null,
        note,
    )

    private companion object {
        val RANGES = listOf(null, TextRange(1, 1, 3, 2))

        val PATHS = listOf(
            "src/Main.kt",
            "",
            "src/Line\nBreak\r.kt",
            "src/\uD800.kt",
            "src/\uDC00.kt",
            "src/🙂.kt",
            "src/ends\uD83D",
            "\uDE42starts.kt",
            "src/é.kt",
            "a`b`c",
        )

        val NOTES = listOf(
            null,
            "",
            "heuristic compiler evidence",
            "broken \uD800 note",
            "broken \uDC00 note",
            "note ends high \uD83D",
            "\uDE42 note starts low",
            "emoji 🙂 note",
            "two\nlines",
        )

        val TEXTS = listOf(
            "",
            "x",
            "x\n",
            "x\n\n",
            "no newline at the end",
            "fun a() = 1\n\nfun b() = 2",
            "ends with ticks ```",
            "`",
            "````\ninner ``` fence\n````",
            "``` \n",
            "\n",
            "\n\n\n",
            "bad \uD800 high",
            "bad \uDC00 low",
            "ends high \uD83D",
            "\uDE42 starts low",
            "pair 🙂 inside",
            "ends with a pair 🙂",
            "high then newline \uD83D\n",
            "newline then low \n\uDE42",
            "carriage\r\nreturns\r\n",
            "αβγ 漢字",
        )

        val EXPECTED_FRAGMENTS = ContextRendering.entries.size * RANGES.size * PATHS.size * NOTES.size * TEXTS.size
    }
}
