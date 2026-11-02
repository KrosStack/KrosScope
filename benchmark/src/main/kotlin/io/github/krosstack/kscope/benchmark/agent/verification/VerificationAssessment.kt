package io.github.krosstack.kscope.benchmark.agent.verification

import io.github.krosstack.kscope.benchmark.agent.AgentTaskSpec
import io.github.krosstack.kscope.benchmark.agent.CompileStatus
import io.github.krosstack.kscope.benchmark.agent.VerificationRecord

internal class GradleLog(val text: String, val exitCode: Int?, val timedOut: Boolean, val seconds: Long)

internal object VerificationAssessment {
    private val failedTask = Regex("Execution failed for task '([^']+)'")
    private val testTask = Regex("^(test[A-Za-z]*|[a-z]+Test)$")

    fun assess(spec: AgentTaskSpec, results: Map<String, TestOutcome>, log: GradleLog): VerificationRecord {
        val failToPassPassed = spec.failToPass.count { results[it] == TestOutcome.PASSED }
        val regressions = spec.passToPass.filter { results[it] != TestOutcome.PASSED }
        val missing = (spec.failToPass + spec.passToPass).filter { it !in results }
        val listed = (spec.failToPass + spec.passToPass).toSet()
        val others = results.count { (id, outcome) -> id !in listed && outcome == TestOutcome.FAILED }
        val compile = if (compileFailed(log, results)) CompileStatus.FAILED else CompileStatus.PASSED
        val passed = !log.timedOut && compile == CompileStatus.PASSED &&
            failToPassPassed == spec.failToPass.size && regressions.isEmpty()
        return VerificationRecord(
            passed = passed,
            compile = compile,
            failToPassPassed = failToPassPassed,
            failToPassTotal = spec.failToPass.size,
            regressions = regressions,
            missing = missing,
            otherFailedTests = others,
            gradleExitCode = log.exitCode,
            timedOut = log.timedOut,
            seconds = log.seconds,
        )
    }

    private fun compileFailed(log: GradleLog, results: Map<String, TestOutcome>): Boolean {
        if (log.timedOut) return true
        val failedTasks = failedTask.findAll(log.text).map { it.groupValues[1] }.toList()
        val nonTestFailure = failedTasks.any { !testTask.matches(it.substringAfterLast(':')) }
        val unexplained = log.exitCode != 0 && failedTasks.isEmpty() && results.values.none { it == TestOutcome.FAILED }
        return nonTestFailure || unexplained
    }
}
