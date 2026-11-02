package io.github.krosstack.kscope.benchmark.agent.verification

import io.github.krosstack.kscope.benchmark.agent.AgentTask
import io.github.krosstack.kscope.benchmark.agent.CompileStatus
import io.github.krosstack.kscope.benchmark.agent.SingleRun
import io.github.krosstack.kscope.benchmark.agent.workspace.FileTrees
import io.github.krosstack.kscope.benchmark.agent.workspace.WorkspaceFactory
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path

@Serializable
internal data class ValidationAttempt(
    val repeat: Int,
    val baseFails: Boolean,
    val referencePasses: Boolean,
    val baseSeconds: Long,
    val referenceSeconds: Long,
    val baseCompile: CompileStatus,
    val referenceMissing: List<String> = emptyList(),
    val referenceRegressions: List<String> = emptyList(),
)

@Serializable
internal data class TaskValidationResult(
    val taskId: String,
    val valid: Boolean,
    val problems: List<String>,
    val attempts: List<ValidationAttempt>,
)

internal class TaskValidator(
    private val source: Path,
    private val tempRoot: Path,
    private val verifier: Verifier,
    private val logs: (String) -> Path,
    private val factory: WorkspaceFactory = WorkspaceFactory(),
) {
    fun validate(task: AgentTask, repeats: Int, print: (String) -> Unit): TaskValidationResult {
        val problems = patchProblems(task).toMutableList()
        val attempts = (1..repeats).map { repeat ->
            print("validate ${task.id} repeat $repeat")
            attempt(task, repeat).also { print("  ${describe(it)}") }
        }
        problems += attempts.filter { !it.baseFails }.map { "repeat ${it.repeat}: the base state passes verification" }
        problems += attempts.filter { !it.referencePasses }.map { "repeat ${it.repeat}: the reference patch fails" }
        return TaskValidationResult(task.id, problems.isEmpty(), problems, attempts)
    }

    private fun attempt(task: AgentTask, repeat: Int): ValidationAttempt {
        val key = "${task.id}-validate-r$repeat"
        val baseLogs = logs(key).resolve("base").also { Files.createDirectories(it) }
        val referenceLogs = logs(key).resolve("reference").also { Files.createDirectories(it) }
        val root = tempRoot.resolve(key)
        try {
            val workspace = factory.create(source, root.resolve(SingleRun.WORKSPACE))
            workspace.copyIn(task.hiddenTests)
            val base = verifier.verify(task, workspace, baseLogs)
            workspace.restoreBase()
            check(workspace.applyPatch(task.referencePatch)) { "${task.id}: the reference patch does not apply" }
            workspace.copyIn(task.hiddenTests)
            val reference = verifier.verify(task, workspace, referenceLogs)
            return ValidationAttempt(
                repeat = repeat,
                baseFails = !base.passed,
                referencePasses = reference.passed,
                baseSeconds = base.seconds,
                referenceSeconds = reference.seconds,
                baseCompile = base.compile,
                referenceMissing = reference.missing,
                referenceRegressions = reference.regressions,
            )
        } finally {
            FileTrees.deleteQuietly(root)
        }
    }

    private fun patchProblems(task: AgentTask): List<String> {
        val allowed = task.spec.goldFiles.toSet() + task.spec.mayEditFiles
        return task.patchFiles().filter { it !in allowed }.map { "the reference patch touches $it, not a gold file" }
    }

    private fun describe(attempt: ValidationAttempt): String =
        "base ${if (attempt.baseFails) "fails" else "PASSES"} (${attempt.baseSeconds} s), " +
            "reference ${if (attempt.referencePasses) "passes" else "FAILS"} (${attempt.referenceSeconds} s)"
}

internal object ValidationReport {
    fun markdown(results: List<TaskValidationResult>): String = buildString {
        appendLine("# Agent task validation")
        appendLine()
        appendLine("| Task | Valid | Base fails | Reference passes | Verify seconds (base / reference) |")
        appendLine("|---|---|---|---|---|")
        results.forEach { result ->
            val attempts = result.attempts
            val seconds = "${attempts.joinToString(", ") { it.baseSeconds.toString() }} / " +
                attempts.joinToString(", ") { it.referenceSeconds.toString() }
            appendLine(
                "| ${result.taskId} | ${if (result.valid) "yes" else "NO"} | " +
                    "${attempts.count { it.baseFails }}/${attempts.size} | " +
                    "${attempts.count { it.referencePasses }}/${attempts.size} | $seconds |",
            )
        }
        results.filter { it.problems.isNotEmpty() }.forEach { result ->
            appendLine()
            appendLine("## ${result.taskId} problems")
            result.problems.forEach { appendLine("- $it") }
        }
    }
}
