package io.github.krosstack.kscope.benchmark.agent.launch

import io.github.krosstack.kscope.benchmark.agent.FailureCode
import io.github.krosstack.kscope.benchmark.agent.RunFailure
import io.github.krosstack.kscope.benchmark.agent.telemetry.AgentTelemetry
import io.github.krosstack.kscope.benchmark.agent.telemetry.ParseContext
import io.github.krosstack.kscope.benchmark.agent.telemetry.ParsedRun
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

internal class AgentRun(val failure: RunFailure?, val telemetry: AgentTelemetry)

internal interface AgentExecutor {
    val name: String
    val model: String?
    val dryRun: Boolean

    fun execute(request: AgentRequest, context: ParseContext, timeout: Duration): AgentRun
}

internal class ProcessAgentExecutor(
    private val adapter: AgentAdapter,
    private val runner: ProcessRunner = ProcessRunner(),
    private val environment: Map<String, String> = System.getenv(),
    private val executable: Path? = null,
) : AgentExecutor {
    override val name: String get() = adapter.name
    override val model: String? get() = adapter.model
    override val dryRun = false

    override fun execute(request: AgentRequest, context: ParseContext, timeout: Duration): AgentRun {
        val located = executable ?: ExecutableLocator.find(adapter.executableName, environment)
            ?: return failed(FailureCode.EXECUTABLE_NOT_FOUND, "${adapter.executableName} is not on the path")
        val invocation = adapter.invocation(located, request)
        val stderr = request.logDirectory.resolve("agent.err")
        val spec = ProcessSpec(
            invocation.command,
            request.workspace.root,
            adapter.environment(environment),
            invocation.stdin,
            stderr,
        )
        val outcome = runner.run(spec, request.logDirectory.resolve("events.jsonl"), timeout)
        val parsed = adapter.parser.parse(outcome.lines, context)
        val telemetry = parsed.telemetry.copy(wallMs = outcome.wallMs)
        return AgentRun(failureOf(outcome, parsed, stderr), telemetry)
    }

    private fun failureOf(outcome: ProcessOutcome, parsed: ParsedRun, stderr: Path): RunFailure? = when {
        outcome.startFailure != null -> notFound(outcome.startFailure)
        outcome.timedOut -> RunFailure(FailureCode.TIMEOUT, "stopped after ${outcome.wallMs / MILLIS_PER_SECOND} s")
        parsed is ParsedRun.Failed -> parsed.failure
        outcome.exitCode != 0 -> RunFailure(FailureCode.NONZERO_EXIT, "exit ${outcome.exitCode}: ${firstLine(stderr)}")
        parsed is ParsedRun.Unfinished -> RunFailure(FailureCode.NO_RESULT, "the stream ended without a result")
        else -> null
    }

    private fun notFound(message: String) = RunFailure(FailureCode.EXECUTABLE_NOT_FOUND, message.take(DETAIL_LIMIT))

    private fun firstLine(file: Path): String = try {
        Files.readAllLines(file).firstOrNull().orEmpty().take(DETAIL_LIMIT)
    } catch (_: IOException) {
        ""
    }

    private fun failed(code: FailureCode, detail: String) = AgentRun(RunFailure(code, detail), AgentTelemetry())

    private companion object {
        const val DETAIL_LIMIT = 200
        const val MILLIS_PER_SECOND = 1000
    }
}

internal class NoopExecutor : AgentExecutor {
    override val name = "noop"
    override val model: String? = null
    override val dryRun = true

    override fun execute(request: AgentRequest, context: ParseContext, timeout: Duration) =
        AgentRun(null, AgentTelemetry())
}

internal class ReferenceExecutor : AgentExecutor {
    override val name = "reference"
    override val model: String? = null
    override val dryRun = true

    override fun execute(request: AgentRequest, context: ParseContext, timeout: Duration): AgentRun {
        val applied = request.workspace.applyPatch(request.referencePatch)
        val failure = if (applied) null else RunFailure(FailureCode.WORKSPACE_ERROR, "the patch does not apply")
        return AgentRun(failure, AgentTelemetry())
    }
}
