package io.github.krosstack.kscope.context.estimate

import io.github.krosstack.kscope.context.BlockMeasureKey
import io.github.krosstack.kscope.context.ContextFixture
import io.github.krosstack.kscope.context.fragment.ContextGraph
import io.github.krosstack.kscope.context.sourceArtifact
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
import io.github.krosstack.kscope.model.context.TokenizerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

internal class BlockMeasureKeysTest {
    private val text = "package sample\n\nclass Greeter {\n    fun greet() = \"hello\"\n}\n"
    private val estimator = TokenizerId("test_additive")
    private val range = TextRange(3, 1, 5, 2)
    private val files = listOf(PATH, "source/Line\nBreak", "source/Broken\uD800Name", "source/Other").map {
        sourceArtifact(it, text)
    }
    private val fixture = ContextFixture(files, emptyList(), files.associate { it.ref to text })
    private val keys = BlockMeasureKeys(ContextGraph(fixture), estimator)

    @Test
    fun `a whole file a declaration and an entry have a key made of what the block is made of`() {
        for (rendering in listOf(ContextRendering.FILE, ContextRendering.DECLARATION, ContextRendering.ENTRY)) {
            val expected = BlockMeasureKey(files.first().contentHash, range, rendering, estimator, digest(PATH, null))

            assertEquals(expected, keys.of(fragment(rendering = rendering)), rendering.name)
        }
    }

    @Test
    fun `the key holds the path as it is printed and the evidence note`() {
        val note = "heuristic compiler evidence"

        assertEquals(digest(PATH, note), keys.of(fragment(note = note))?.layoutDigest)
        assertEquals(digest("source/Line Break", null), keys.of(fragment(path = "source/Line\nBreak"))?.layoutDigest)
        assertNotEquals(keys.of(fragment()), keys.of(fragment(note = note)))
    }

    @Test
    fun `a signature an outline and a generated summary have no key`() {
        for (rendering in listOf(ContextRendering.SIGNATURE, ContextRendering.OUTLINE, ContextRendering.GENERATED)) {
            assertNull(keys.of(fragment(rendering = rendering)), rendering.name)
        }
    }

    @Test
    fun `a fragment without a range or without source behind it has no key`() {
        assertNull(keys.of(fragment(range = null)))
        assertNull(keys.of(fragment(sourceBacked = false)))
    }

    @Test
    fun `a path or a note that is not valid Unicode has no key`() {
        assertNull(keys.of(fragment(path = "source/Broken\uD800Name")))
        assertNull(keys.of(fragment(note = "broken \uDC00 note")))
    }

    @Test
    fun `a file the graph does not know has no key`() {
        assertNull(keys.of(fragment(path = "source/Unknown")))
    }

    private fun digest(path: String, note: String?): String = BlockLayout.digest(path, note)

    private fun fragment(
        rendering: ContextRendering = ContextRendering.FILE,
        path: String = PATH,
        range: TextRange? = this.range,
        note: String? = null,
        sourceBacked: Boolean = range != null,
    ): ContextFragment = ContextFragment(
        ArtifactRef(RepositoryId("sample"), RepoPath(path)),
        range,
        NodeRef.OfSymbol(SymbolId("sample")),
        rendering,
        ContextRole.READ,
        1.0,
        TokenCount(0),
        text,
        sourceBacked,
        note,
    )

    private companion object {
        const val PATH = "source/Greeter"
    }
}
