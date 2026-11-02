package io.github.krosstack.kscope.benchmark.agent.verification

import io.github.krosstack.kscope.benchmark.agent.AgentTaskSpec
import io.github.krosstack.kscope.benchmark.agent.CompileStatus
import io.github.krosstack.kscope.benchmark.agent.VerifySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerificationTest {
    private val spec = AgentTaskSpec(
        id = "T99",
        title = "Example",
        category = "example",
        statement = "Do it.",
        goldFiles = listOf("a.kt"),
        verify = VerifySpec(listOf(":m:test")),
        failToPass = listOf("p.NewTest#works"),
        passToPass = listOf("p.OldTest#stays"),
    )

    private fun log(text: String = "", exit: Int? = 0, timedOut: Boolean = false) = GradleLog(text, exit, timedOut, 12)

    private fun passing() = mapOf("p.NewTest#works" to TestOutcome.PASSED, "p.OldTest#stays" to TestOutcome.PASSED)

    @Test
    fun `all listed tests passing is a pass with a compiled build`() {
        val record = VerificationAssessment.assess(spec, passing(), log())

        assertTrue(record.passed)
        assertEquals(CompileStatus.PASSED, record.compile)
        assertEquals(1, record.failToPassPassed)
        assertTrue(record.regressions.isEmpty())
        assertEquals(12, record.seconds)
    }

    @Test
    fun `a failing fail-to-pass test is a failure without a compile problem`() {
        val results = passing() + ("p.NewTest#works" to TestOutcome.FAILED)
        val text = "> Task :m:test FAILED\nExecution failed for task ':m:test'.\n"

        val record = VerificationAssessment.assess(spec, results, log(text, exit = 1))

        assertFalse(record.passed)
        assertEquals(CompileStatus.PASSED, record.compile)
        assertEquals(0, record.failToPassPassed)
    }

    @Test
    fun `a failing pass-to-pass test is a regression`() {
        val results = passing() + ("p.OldTest#stays" to TestOutcome.FAILED)

        val record = VerificationAssessment.assess(spec, results, log("Execution failed for task ':m:test'.", exit = 1))

        assertFalse(record.passed)
        assertEquals(listOf("p.OldTest#stays"), record.regressions)
    }

    @Test
    fun `a failed compile task is a compile failure and the listed tests are missing`() {
        val text = "Execution failed for task ':m:compileTestKotlinDesktop'.\n"

        val record = VerificationAssessment.assess(spec, emptyMap(), log(text, exit = 1))

        assertFalse(record.passed)
        assertEquals(CompileStatus.FAILED, record.compile)
        assertEquals(listOf("p.NewTest#works", "p.OldTest#stays"), record.missing)
    }

    @Test
    fun `a failing code generation or resource task is not a test failure`() {
        val text = "Execution failed for task ':m:generateCommonMainDatabaseInterface'.\n"

        assertEquals(CompileStatus.FAILED, VerificationAssessment.assess(spec, emptyMap(), log(text, exit = 1)).compile)
    }

    @Test
    fun `a build that fails without any failed task or test is treated as a build failure`() {
        val record = VerificationAssessment.assess(spec, passing(), log("Could not configure", exit = 1))

        assertEquals(CompileStatus.FAILED, record.compile)
        assertFalse(record.passed)
    }

    @Test
    fun `a timeout never passes`() {
        val record = VerificationAssessment.assess(spec, passing(), log(exit = null, timedOut = true))

        assertFalse(record.passed)
        assertTrue(record.timedOut)
    }

    @Test
    fun `failures of tests that are not listed are counted apart`() {
        val results = passing() + ("p.Extra#own" to TestOutcome.FAILED)

        val record = VerificationAssessment.assess(spec, results, log("Execution failed for task ':m:test'.", exit = 1))

        assertTrue(record.passed)
        assertEquals(1, record.otherFailedTests)
    }
}
