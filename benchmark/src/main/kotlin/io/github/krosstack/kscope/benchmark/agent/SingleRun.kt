package io.github.krosstack.kscope.benchmark.agent

import io.github.krosstack.kscope.benchmark.agent.launch.AgentExecutor
import io.github.krosstack.kscope.benchmark.agent.launch.AgentRequest
import io.github.krosstack.kscope.benchmark.agent.telemetry.ParseContext
import io.github.krosstack.kscope.benchmark.agent.verification.Verifier
import io.github.krosstack.kscope.benchmark.agent.workspace.ContextPreparer
import io.github.krosstack.kscope.benchmark.agent.workspace.FileTrees
import io.github.krosstack.kscope.benchmark.agent.workspace.PreparedContext
import io.github.krosstack.kscope.benchmark.agent.workspace.PromptTemplate
import io.github.krosstack.kscope.benchmark.agent.workspace.Workspace
import io.github.krosstack.kscope.benchmark.agent.workspace.WorkspaceFactory
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.Path
import java.sql.SQLException
import java.time.Duration
import java.time.Instant

internal class RunEnvironment(
    val source: Path,
    val tempRoot: Path,
    val logs: (String) -> Path,
    val keepWorkspaces: Boolean,
)

private data class RunSession(
    val task: AgentTask,
    val arm: Arm,
    val repeat: Int,
    val workspace: Workspace,
    val logs: Path,
    val context: PreparedContext,
    val request: AgentRequest,
    val start: RunStart = RunStart(),
)

private class RunStart(val instant: Instant = Instant.now(), val clock: Long = System.nanoTime())

internal class SingleRun(
    private val environment: RunEnvironment,
    private val executor: AgentExecutor,
    private val verifier: Verifier,
    private val contexts: ContextPreparer,
    private val factory: WorkspaceFactory = WorkspaceFactory(),
) {
    private val baseFingerprint: String by lazy { factory.baseFingerprint(environment.source) }

    fun inputsOf(task: AgentTask): String =
        Fingerprint.of(sequenceOf(baseFingerprint, task.contentDigest()).map { it.toByteArray(Charsets.UTF_8) })

    fun execute(
        task: AgentTask,
        arm: Arm,
        repeat: Int,
        timeout: Duration,
        deadline: RunDeadline = RunDeadline.NONE,
    ): RunRecord {
        val start = RunStart()
        val runRoot = environment.tempRoot.resolve(RunRecord.runKey(task.id, arm, repeat))
        fun failure(error: Exception) = failedRecord(task, arm, repeat, error, start)
        return try {
            val session = prepare(task, arm, repeat, runRoot)
            finish(session, runAgent(session, deadline.limit(timeout)), deadline)
        } catch (error: IllegalStateException) {
            failure(error)
        } catch (error: IllegalArgumentException) {
            failure(error)
        } catch (error: IOException) {
            failure(error)
        } catch (error: UncheckedIOException) {
            failure(error)
        } catch (error: SQLException) {
            failure(error)
        } finally {
            if (!environment.keepWorkspaces) FileTrees.deleteQuietly(runRoot)
        }
    }

    private fun prepare(task: AgentTask, arm: Arm, repeat: Int, runRoot: Path): RunSession {
        val logs = environment.logs(RunRecord.runKey(task.id, arm, repeat)).also { Files.createDirectories(it) }
        val workspace = factory.create(environment.source, runRoot.resolve(WORKSPACE))
        val leaks = workspace.forbiddenEntries(FORBIDDEN_NAMES)
        check(leaks.isEmpty()) { "The workspace copy holds forbidden entries: ${leaks.joinToString()}" }
        val statement = task.spec.statement
        val index = runRoot.resolve(INDEX)
        val context = contexts.prepare(arm, statement, workspace.root, index, task.spec.goldFiles.toSet())
        val prompt = PromptTemplate.render(statement, context.markdown)
        val promptFile = logs.resolve("prompt.txt").also { Files.writeString(it, prompt) }
        val request = AgentRequest(workspace, prompt, promptFile, logs, task.referencePatch)
        return RunSession(task, arm, repeat, workspace, logs, context, request)
    }

    private fun runAgent(session: RunSession, timeout: Duration): RunRecord {
        val spec = session.task.spec
        val root = session.workspace.root.toString()
        val parseContext = ParseContext(spec.goldFiles.toSet(), session.workspace.baseFiles(), root)
        val agentStart = System.nanoTime()
        val run = executor.execute(session.request, parseContext, timeout)
        val agentMs = elapsedMs(agentStart)
        return RunRecord(
            taskId = session.task.id,
            arm = session.arm,
            repeat = session.repeat,
            agent = executor.name,
            model = executor.model,
            dryRun = executor.dryRun,
            status = if (run.failure == null) RunStatus.COMPLETED else RunStatus.FAILED,
            failure = run.failure,
            context = session.context.record,
            telemetry = run.telemetry,
            gold = goldRecord(spec, session.workspace.changedFiles()),
            timing = RunTiming(session.start.instant.toString(), agentMs),
            inputs = inputsOf(session.task),
        )
    }

    private fun finish(session: RunSession, skeleton: RunRecord, deadline: RunDeadline): RunRecord {
        if (skeleton.status == RunStatus.FAILED) return skeleton.withTotal(elapsedMs(session.start.clock))
        val verifyStart = System.nanoTime()
        session.workspace.copyIn(session.task.hiddenTests)
        val requested = Duration.ofMinutes(session.task.spec.verify.timeoutMinutes.toLong())
        val allowed = deadline.limit(requested)
        val verification = verifier.verify(session.task, session.workspace, session.logs, allowed)
        val timing = skeleton.timing.copy(verifyMs = elapsedMs(verifyStart), totalMs = elapsedMs(session.start.clock))
        val record = skeleton.copy(verification = verification, timing = timing)
        return if (verification.timedOut && allowed < requested) record.endedByGlobalLimit() else record
    }

    private fun RunRecord.endedByGlobalLimit() = copy(
        status = RunStatus.FAILED,
        failure = RunFailure(FailureCode.TIMEOUT, "the global time limit ended the verification"),
    )

    private fun RunRecord.withTotal(totalMs: Long) = copy(timing = timing.copy(totalMs = totalMs))

    private fun goldRecord(spec: AgentTaskSpec, changed: List<String>): GoldRecord {
        val expected = spec.goldFiles.toSet()
        val allowed = expected + spec.mayEditFiles
        return GoldRecord(
            modifiedFiles = changed,
            goldFilesHit = changed.filter { it in expected },
            goldFilesMissed = spec.goldFiles.filter { it !in changed }.sorted(),
            unnecessaryFiles = changed.filter { it !in allowed },
        )
    }

    private fun failedRecord(task: AgentTask, arm: Arm, repeat: Int, error: Exception, start: RunStart) = RunRecord(
        taskId = task.id,
        arm = arm,
        repeat = repeat,
        agent = executor.name,
        model = executor.model,
        dryRun = executor.dryRun,
        status = RunStatus.FAILED,
        failure = RunFailure(FailureCode.WORKSPACE_ERROR, error.message?.take(DETAIL_LIMIT)),
        timing = RunTiming(start.instant.toString(), totalMs = elapsedMs(start.clock)),
        inputs = inputsOf(task),
    )

    private fun elapsedMs(start: Long): Long = (System.nanoTime() - start) / NANOS_PER_MILLI

    companion object {
        const val WORKSPACE = "workspace"
        const val INDEX = "index"
        private const val DETAIL_LIMIT = 300
        private const val NANOS_PER_MILLI = 1_000_000L
        val FORBIDDEN_NAMES = setOf(
            "CLAUDE.md", "AGENTS.md", "GEMINI.md", ".claude", ".codex", ".cursor", ".kscope", "planning", "reports",
            AgentTask.PATCH,
        )
    }
}
