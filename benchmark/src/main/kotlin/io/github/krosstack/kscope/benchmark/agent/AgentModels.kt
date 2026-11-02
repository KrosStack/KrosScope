package io.github.krosstack.kscope.benchmark.agent

import io.github.krosstack.kscope.benchmark.agent.telemetry.AgentTelemetry
import kotlinx.serialization.Serializable

@Serializable
internal enum class Arm(val letter: String, val description: String) {
    A("A", "statement only"),
    B("B", "statement and BM25 whole-file context"),
    C("C", "statement and KrosScope context"),
    ;

    val usesContext: Boolean get() = this != A

    companion object {
        fun parse(letter: String): Arm? = entries.firstOrNull { it.letter.equals(letter, ignoreCase = true) }
    }
}

@Serializable
internal data class VerifySpec(val gradleTasks: List<String>, val timeoutMinutes: Int = DEFAULT_VERIFY_MINUTES) {
    private companion object {
        const val DEFAULT_VERIFY_MINUTES = 15
    }
}

@Serializable
internal data class AgentTaskSpec(
    val id: String,
    val title: String,
    val category: String,
    val statement: String,
    val goldFiles: List<String>,
    val mayEditFiles: List<String> = emptyList(),
    val verify: VerifySpec,
    val failToPass: List<String>,
    val passToPass: List<String>,
)

@Serializable
internal enum class RunStatus { COMPLETED, FAILED }

@Serializable
internal enum class FailureCode(val label: String) {
    EXECUTABLE_NOT_FOUND("agent executable not found"),
    AUTHENTICATION("authentication error"),
    TIMEOUT("run timeout"),
    NONZERO_EXIT("non-zero exit"),
    AGENT_ERROR("agent reported an error"),
    NO_RESULT("no result event"),
    WORKSPACE_ERROR("workspace error"),
}

@Serializable
internal data class RunFailure(val code: FailureCode, val detail: String? = null)

@Serializable
internal data class ContextRecord(
    val tokens: Int = 0,
    val files: List<String> = emptyList(),
    val goldFilesInContext: Int = 0,
    val indexMs: Long = 0,
    val contextMs: Long = 0,
    val budget: Int = 0,
    val estimator: String = "",
)

@Serializable
internal enum class CompileStatus { PASSED, FAILED }

@Serializable
internal data class VerificationRecord(
    val passed: Boolean,
    val compile: CompileStatus,
    val failToPassPassed: Int,
    val failToPassTotal: Int,
    val regressions: List<String> = emptyList(),
    val missing: List<String> = emptyList(),
    val otherFailedTests: Int = 0,
    val gradleExitCode: Int? = null,
    val timedOut: Boolean = false,
    val seconds: Long = 0,
)

@Serializable
internal data class GoldRecord(
    val modifiedFiles: List<String> = emptyList(),
    val goldFilesHit: List<String> = emptyList(),
    val goldFilesMissed: List<String> = emptyList(),
    val unnecessaryFiles: List<String> = emptyList(),
)

@Serializable
internal data class RunTiming(
    val startedAt: String,
    val agentMs: Long = 0,
    val verifyMs: Long = 0,
    val totalMs: Long = 0,
)

@Serializable
internal data class RunRecord(
    val schemaVersion: Int = RECORD_SCHEMA_VERSION,
    val taskId: String,
    val arm: Arm,
    val repeat: Int,
    val agent: String,
    val model: String?,
    val dryRun: Boolean,
    val status: RunStatus,
    val failure: RunFailure? = null,
    val context: ContextRecord = ContextRecord(),
    val telemetry: AgentTelemetry = AgentTelemetry(),
    val verification: VerificationRecord? = null,
    val gold: GoldRecord? = null,
    val timing: RunTiming,
    val inputs: String = "",
) {
    val key: String get() = runKey(taskId, arm, repeat)

    companion object {
        const val RECORD_SCHEMA_VERSION = 1

        fun runKey(taskId: String, arm: Arm, repeat: Int): String = "$taskId-${arm.letter}-r$repeat"
    }
}

@Serializable
internal data class RunBinding(
    val schemaVersion: Int = 1,
    val agent: String,
    val model: String?,
    val budget: Int,
    val estimator: String,
    val promptVersion: String,
    val dryRun: Boolean,
)
