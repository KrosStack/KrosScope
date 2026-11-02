package io.github.krosstack.kscope.benchmark.run

import io.github.krosstack.kscope.benchmark.score.Score
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GateTest {
    private val measured = ConformanceFixture.default

    @Test
    fun `a result that measured no declaration and no edge does not pass`() {
        val nothing = measured.copy(
            declarations = DeclarationMetrics(Score.combine(emptyList()), emptyList()),
            closedWorld = ClosedWorldMetrics(emptyList()),
        )

        val failures = nothing.checkGate(FileSelection.WITHOUT_ANDROID).failures

        assertTrue("no declaration was measured" in failures, "$failures")
        assertTrue("no closed-world edge was measured" in failures, "$failures")
    }

    @Test
    fun `a scan that reported errors does not pass`() {
        val failed = measured.copy(scan = measured.scan.copy(errors = 2))

        assertEquals(
            listOf("the scan reported 2 errors"),
            failed.checkGate(FileSelection.WITHOUT_ANDROID).failures,
        )
    }

    @Test
    fun `gold data with problems does not pass`() {
        val broken = measured.copy(goldProblems = listOf("a:1 names a line outside its file"))

        assertEquals(
            listOf("the gold data holds 1 problems"),
            broken.checkGate(FileSelection.WITHOUT_ANDROID).failures,
        )
    }
}
