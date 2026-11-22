package io.github.krosstack.kscope.cli.status

import io.github.krosstack.kscope.cli.status.StatusLineFixtures.freshness
import io.github.krosstack.kscope.cli.status.StatusLineFixtures.line
import io.github.krosstack.kscope.service.freshness.FreshnessCounts
import io.github.krosstack.kscope.service.freshness.FreshnessState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatusLineTest {
    private val index = StatusLineFixtures.INDEX

    @Test
    fun `behind says which repositories are at another commit and which working trees changed`() {
        val behind = freshness(FreshnessState.BEHIND, FreshnessCounts(2, 6, 0), FreshnessCounts(1, 7, 0), files = 3)

        assertEquals(
            "kscope: index $index; level resolved; scanned 2d ago; behind: 2 of 8 repositories at another commit, " +
                "1 of 8 working trees changed (3 files); server not running",
            line(behind, 8),
        )
    }

    @Test
    fun `an index of one repository says another commit checked out, and a working tree that changed`() {
        val behind = freshness(FreshnessState.BEHIND, FreshnessCounts(1, 0, 0), FreshnessCounts(1, 0, 0), files = 1)

        assertEquals(
            "kscope: index $index; level resolved; scanned 2d ago; behind: another commit checked out, " +
                "working tree changed (1 file); server not running",
            line(behind, 1),
        )
    }

    @Test
    fun `an estimate by file times says so`() {
        val behind = freshness(
            FreshnessState.BEHIND,
            FreshnessCounts(0, 0, 0),
            FreshnessCounts(1, 0, 0),
            files = 2,
            estimate = true,
        )

        val expected = "behind: working tree changed (2 files, estimated from file times); "
        assertTrue(line(behind, 1).contains(expected), line(behind, 1))
    }

    @Test
    fun `not checked shows only when it is above zero`() {
        val behind = freshness(FreshnessState.BEHIND, FreshnessCounts(1, 7, 0), FreshnessCounts(0, 5, 3))
        val clean = freshness(FreshnessState.BEHIND, FreshnessCounts(1, 7, 0), FreshnessCounts(0, 8, 0))

        val expected = "behind: 1 of 8 repositories at another commit, 3 of 8 working trees not checked; "
        assertTrue(line(behind, 8).contains(expected), line(behind, 8))
        assertFalse(line(clean, 8).contains("not checked"))
    }

    @Test
    fun `nothing behind is up to date, with the number of repositories`() {
        val same = freshness(FreshnessState.NOT_BEHIND, FreshnessCounts(0, 8, 0), FreshnessCounts(0, 8, 0))
        val single = freshness(FreshnessState.NOT_BEHIND, FreshnessCounts(0, 1, 0), FreshnessCounts(0, 1, 0))

        assertEquals(
            "kscope: index $index; level resolved; scanned 2d ago; up to date (8 repositories); server not running",
            line(same, 8),
        )
        assertTrue(line(single, 1).contains("; up to date (1 repository); "))
    }

    @Test
    fun `nothing behind but something not checked says as far as checked`() {
        val partly = freshness(FreshnessState.NOT_BEHIND, FreshnessCounts(0, 6, 2), FreshnessCounts(0, 8, 0))

        val expected = "up to date as far as checked (8 repositories; 2 of 8 commits not checked)"
        assertTrue(line(partly, 8).contains(expected), line(partly, 8))
    }

    @Test
    fun `not checked gives its reason`() {
        val unchecked = freshness(FreshnessState.NOT_CHECKED, FreshnessCounts(0, 1, 0), FreshnessCounts(0, 0, 1))
            .copy(reason = "this repository could not be checked in 10000 ms")

        val expected = "; not checked: working tree (this repository could not be checked in 10000 ms); "
        assertTrue(line(unchecked, 1).contains(expected), line(unchecked, 1))
    }

    @Test
    fun `one working tree of many that was not checked is counted, with the reason of the first`() {
        val unchecked = freshness(FreshnessState.NOT_CHECKED, FreshnessCounts(0, 31, 0), FreshnessCounts(0, 30, 1))
            .copy(reason = "this repository could not be checked in 10000 ms")

        val expected = "; not checked: 1 of 31 working trees (this repository could not be checked in 10000 ms); "
        assertTrue(line(unchecked, 31).contains(expected), line(unchecked, 31))
    }

    @Test
    fun `the words are lower case, and an unknown scan time is said`() {
        val behind = freshness(FreshnessState.BEHIND, FreshnessCounts(1, 0, 0), FreshnessCounts(0, 1, 0))

        val text = line(behind, 1, age = null)

        assertFalse(text.contains("BEHIND") || text.contains("Index is"), text)
        assertTrue(text.contains("; scan time not known; "), text)
    }
}
