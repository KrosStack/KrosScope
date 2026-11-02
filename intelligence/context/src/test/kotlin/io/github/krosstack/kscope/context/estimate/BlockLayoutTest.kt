package io.github.krosstack.kscope.context.estimate

import io.github.krosstack.kscope.context.ContextMarkdown
import io.github.krosstack.kscope.context.fragment.SourceDocument
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
import java.security.MessageDigest
import java.util.HexFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

internal class BlockLayoutTest {
    @Test
    fun `the markdown of a fragment block is the one the layout version names`() {
        val ticks = "`".repeat(4)
        val whole = fragment("src/A.kt", TextRange(1, 1, 3, 1), ContextRendering.FILE, "package a\n\nclass A\n")
        val declaration = fragment("src/B.kt", TextRange(7, 1, 7, 9), ContextRendering.DECLARATION, "b = \"$ticks\"")
        val signature = fragment("src/C\nD\r.kt", null, ContextRendering.SIGNATURE, "fun c(): String", "a note")
        val entry = fragment("app.cfg", TextRange(2, 1, 3, 1), ContextRendering.ENTRY, "k=1\r\nk=2\r\n")

        assertBlock("``` src/A.kt:1-3 [file]\npackage a\n\nclass A\n```\n\n", whole)
        assertBlock("````` src/B.kt:7-7 [declaration]\nb = \"$ticks\"\n`````\n\n", declaration)
        assertBlock("``` src/C D .kt [signature]\nfun c(): String\n```\na note\n\n", signature)
        assertBlock("``` app.cfg:2-3 [entry]\nk=1\r\nk=2\r\n```\n\n", entry)
        assertEquals(PINNED_VERSION, BlockLayout.VERSION, RAISE_THE_VERSION)
    }

    @Test
    fun `the slices and the full range of a rendering are the ones the layout version names`() {
        val document = SourceDocument("alpha\r\nβ🙂γ\n\nlast")

        assertEquals(TextRange(1, 1, 4, 5), document.fullRange, RAISE_THE_VERSION)
        assertEquals("alpha\r", document.slice(TextRange(1, 1, 1, 7)), RAISE_THE_VERSION)
        assertEquals("β🙂γ", document.slice(TextRange(2, 1, 2, 5)), RAISE_THE_VERSION)
        assertNull(document.slice(TextRange(2, 3, 2, 5)), RAISE_THE_VERSION)
        assertEquals(document.text, document.slice(document.fullRange), RAISE_THE_VERSION)
        assertEquals(PINNED_VERSION, BlockLayout.VERSION, RAISE_THE_VERSION)
    }

    @Test
    fun `the digest tells apart paths notes and unicode that cannot be encoded`() {
        val digest = BlockLayout.digest("source/A", null)

        assertEquals(DIGEST_LENGTH, digest.length)
        assertEquals(digest, BlockLayout.digest("source/A", null))
        assertNotEquals(digest, BlockLayout.digest("source/B", null))
        assertNotEquals(digest, BlockLayout.digest("source/A", ""))
        assertNotEquals(BlockLayout.digest("source/A", "note"), BlockLayout.digest("source/A", "other"))
        assertNotEquals(BlockLayout.digest("a/b", "c"), BlockLayout.digest("a/bc", ""))
        assertNotEquals(BlockLayout.digest("a\uD800b", null), BlockLayout.digest("a?b", null))
    }

    @Test
    fun `the text digest is the SHA-256 of the UTF-16 code units whatever the length of the text`() {
        for (length in listOf(0, 1, CHUNK - 1, CHUNK, CHUNK + 1, 2 * CHUNK, 2 * CHUNK + 1, 3 * CHUNK + 7)) {
            val text = "aé漢z".repeat(length / 4 + 1).take(length)
            val expected = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_16BE)),
            )

            assertEquals(expected, BlockLayout.textDigest(text), "length=$length")
        }
    }

    @Test
    fun `the text digest tells apart texts and unicode that cannot be encoded`() {
        val digest = BlockLayout.textDigest("fun a()")

        assertEquals(DIGEST_LENGTH, digest.length)
        assertEquals(digest, BlockLayout.textDigest("fun a()"))
        assertNotEquals(digest, BlockLayout.textDigest("fun b()"))
        assertNotEquals(BlockLayout.textDigest(""), BlockLayout.textDigest(Char(0).toString()))
        assertNotEquals(BlockLayout.textDigest("a\uD800b"), BlockLayout.textDigest("a?b"))
        assertNotEquals(BlockLayout.textDigest("a\uD800b"), BlockLayout.textDigest("a\uDC00b"))
        assertNotEquals(BlockLayout.textDigest("x".repeat(CHUNK + 1)), BlockLayout.textDigest("x".repeat(CHUNK)))
    }

    private fun assertBlock(expected: String, fragment: ContextFragment) =
        assertEquals(expected, ContextMarkdown.fragment(fragment), RAISE_THE_VERSION)

    private fun fragment(
        path: String,
        range: TextRange?,
        rendering: ContextRendering,
        text: String,
        note: String? = null,
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
        const val PINNED_VERSION = 1
        const val CHUNK = 4096
        const val DIGEST_LENGTH = 64
        const val RAISE_THE_VERSION =
            "The markdown of a fragment block or the slicing of a rendering changed. Raise BlockLayout.VERSION " +
                "together with this expectation, so that no block measure kept under the old layout is recalled."
    }
}
