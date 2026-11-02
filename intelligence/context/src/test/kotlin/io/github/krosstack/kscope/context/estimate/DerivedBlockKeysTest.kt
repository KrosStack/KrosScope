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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal class DerivedBlockKeysTest {
    private val estimator = TokenizerId("test_additive")
    private val files = listOf(PATH, "source/Line\nBreak", "source/Broken\uD800Name").map { sourceArtifact(it, "x\n") }
    private val fixture = ContextFixture(files, emptyList(), files.associate { it.ref to "x\n" })
    private val derived = DerivedBlockKeys(ContextGraph(fixture), estimator)
    private val sliced = BlockMeasureKeys(ContextGraph(fixture), estimator)

    @Test
    fun `a signature an outline and a generated summary have a key made of what the block is made of`() {
        val note = "heuristic compiler evidence"
        for (rendering in listOf(ContextRendering.SIGNATURE, ContextRendering.OUTLINE, ContextRendering.GENERATED)) {
            val expected = BlockMeasureKey(
                files.first().contentHash,
                null,
                rendering,
                estimator,
                BlockLayout.digest(PATH, note),
                BlockLayout.textDigest(TEXT),
            )

            assertEquals(expected, derived.of(fragment(rendering = rendering, note = note)), rendering.name)
        }
    }

    @Test
    fun `the key holds the range of the fragment when it has one`() {
        val range = TextRange(3, 1, 5, 2)

        assertEquals(range, derived.of(fragment(range = range))?.range)
        assertNull(derived.of(fragment(range = null))?.range)
    }

    @Test
    fun `the key holds the path as it is printed`() {
        val key = derived.of(fragment(path = "source/Line\nBreak"))

        assertEquals(BlockLayout.digest("source/Line Break", null), key?.layoutDigest)
    }

    @Test
    fun `fragments that differ in one part do not share a key`() {
        val key = derived.of(fragment())

        assertNotEquals(key, derived.of(fragment(text = "fun greet(): String")))
        assertNotEquals(key, derived.of(fragment(note = "heuristic compiler evidence")))
        assertNotEquals(key, derived.of(fragment(rendering = ContextRendering.OUTLINE)))
        assertNotEquals(key, derived.of(fragment(range = TextRange(1, 1, 1, 5))))
        assertNotEquals(key, derived.of(fragment(path = "source/Line\nBreak")))
    }

    @Test
    fun `a key made of a text digest never equals the key of a block that is a slice of a file`() {
        val range = TextRange(1, 1, 1, 2)
        val fromGraph = derived.of(fragment(rendering = ContextRendering.FILE, range = range))
        val fromFile = sliced.of(fragment(rendering = ContextRendering.FILE, range = range))

        assertNotNull(fromGraph)
        assertNotNull(fromFile)
        assertNotEquals(fromFile, fromGraph)
    }

    @Test
    fun `a path a note or a text that is not valid Unicode has no key`() {
        assertNull(derived.of(fragment(path = "source/Broken\uD800Name")))
        assertNull(derived.of(fragment(note = "broken \uDC00 note")))
        assertNull(derived.of(fragment(text = "broken \uD800 text")))
        assertNull(derived.of(fragment(text = "ends with half a pair \uD83D")))
    }

    @Test
    fun `a file the graph does not know has no key`() {
        assertNull(derived.of(fragment(path = "source/Unknown")))
    }

    private fun fragment(
        rendering: ContextRendering = ContextRendering.SIGNATURE,
        path: String = PATH,
        range: TextRange? = null,
        note: String? = null,
        text: String = TEXT,
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
        const val PATH = "source/Greeter"
        const val TEXT = "fun greet()"
    }
}
