package io.github.krosstack.kscope.context.selection

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class FileRanksTest {
    private val late = file("z/First")
    private val early = file("a/Second")
    private val middle = file("m/Third")
    private val modules = mapOf(late to "a", early to "b", middle to "a")
    private val moduleThenPath = compareBy<ArtifactRef> { modules.getValue(it) }.thenBy { it.path.value }

    @Test
    fun `each file ranks at its position in the order and a repeated file ranks once`() {
        val ranks = assertNotNull(FileRanks.of(listOf(late, early, middle, late), moduleThenPath))

        assertEquals(listOf(1, 2, 0), listOf(late, early, middle).map(ranks::rankOf))
    }

    @Test
    fun `files that the order calls equal get no ranks`() {
        val moduleOnly = compareBy<ArtifactRef> { modules.getValue(it) }

        assertNull(FileRanks.of(listOf(late, early, middle), moduleOnly))
    }

    @Test
    fun `a file that was not given has no rank`() {
        val ranks = assertNotNull(FileRanks.of(listOf(late, middle), moduleThenPath))

        assertFailsWith<NoSuchElementException> { ranks.rankOf(early) }
    }

    @Test
    fun `ordering many files compares them n log n times at most and ranks them like a sort`() {
        val files = (0 until FILES).map { file("dir${(it * STEP) % FILES}/File$it") }
        val calls = intArrayOf(0)
        val counting = Comparator<ArtifactRef> { left, right ->
            calls[0]++
            left.path.value.compareTo(right.path.value)
        }

        val ranks = assertNotNull(FileRanks.of(files, counting))

        val bound = FILES * ceil(log2(FILES.toDouble())).toInt() + FILES
        assertTrue(calls[0] <= bound, "calls=${calls[0]} bound=$bound")
        assertEquals(files.sortedBy { it.path.value }, files.sortedBy(ranks::rankOf))
    }

    private fun file(path: String) = ArtifactRef(RepositoryId("sample"), RepoPath(path))

    private companion object {
        const val FILES = 200
        const val STEP = 37
    }
}
