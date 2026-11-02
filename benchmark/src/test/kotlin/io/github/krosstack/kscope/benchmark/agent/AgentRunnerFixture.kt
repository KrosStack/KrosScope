package io.github.krosstack.kscope.benchmark.agent

import io.github.krosstack.kscope.benchmark.TemporaryRepositoryTest
import io.github.krosstack.kscope.benchmark.TestGit
import io.github.krosstack.kscope.benchmark.agent.launch.AgentExecutor
import io.github.krosstack.kscope.benchmark.agent.launch.NoopExecutor
import io.github.krosstack.kscope.benchmark.agent.verification.Verifier
import io.github.krosstack.kscope.benchmark.agent.workspace.ContextPreparer
import io.github.krosstack.kscope.benchmark.agent.workspace.Workspace
import io.github.krosstack.kscope.benchmark.agent.workspace.WorkspaceFactory
import io.github.krosstack.kscope.context.TokenEstimators
import kotlinx.serialization.encodeToString
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.test.assertIs

internal open class AgentRunnerFixture : TemporaryRepositoryTest("kscope-runner-test") {
    protected val git = TestGit.cli()
    protected val source: Path = base.resolve("repo").also { Files.createDirectories(it) }
    protected val temp: Path = base.resolve("temp").also { Files.createDirectories(it) }
    protected val out: Path = base.resolve("out")

    protected val spec = AgentTaskSpec(
        id = "T01",
        title = "Fix the greeting",
        category = "bugfix",
        statement = "The greeting must say goodbye.",
        goldFiles = listOf("src/Greeting.txt"),
        mayEditFiles = listOf("src/GreetingTest.txt"),
        verify = VerifySpec(listOf(":check")),
        failToPass = listOf("p.GreetingTest#says"),
        passToPass = emptyList(),
    )

    protected fun prepare(): AgentTask {
        git.output(source, "init", "-q")
        Files.createDirectories(source.resolve("src"))
        Files.writeString(source.resolve("src/Greeting.txt"), "hello\n")
        Files.writeString(source.resolve("src/GreetingTest.txt"), "test\n")
        git.output(source, "add", "-A")
        git.output(source, "commit", "-q", "-m", "source")
        val directory = base.resolve("tasks/T01")
        Files.createDirectories(directory.resolve("hidden/src"))
        Files.writeString(directory.resolve("hidden/src/HiddenTest.txt"), "hidden\n")
        Files.writeString(directory.resolve("task.json"), agentJson.encodeToString(spec))
        val header = "diff --git a/src/Greeting.txt b/src/Greeting.txt\n"
        val patch = header + "--- a/src/Greeting.txt\n+++ b/src/Greeting.txt\n@@ -1 +1 @@\n-hello\n+goodbye\n"
        Files.writeString(directory.resolve("reference.patch"), patch)
        return assertIs<TaskLoad.Loaded>(AgentTaskStore.load(base.resolve("tasks"))).tasks.single()
    }

    protected class FakeVerifier(private val needs: Duration? = null) : Verifier {
        val limits = mutableListOf<Duration?>()

        override fun verify(
            task: AgentTask,
            workspace: Workspace,
            logDirectory: Path,
            limit: Duration?,
        ): VerificationRecord {
            limits += limit
            val fixed = Files.readString(workspace.root.resolve("src/Greeting.txt")) == "goodbye\n"
            val hidden = Files.exists(workspace.root.resolve("src/HiddenTest.txt"))
            val timedOut = needs != null && limit != null && limit < needs
            val passed = fixed && hidden && !timedOut
            val fixedTests = if (fixed) 1 else 0
            return VerificationRecord(passed, CompileStatus.PASSED, fixedTests, 1, timedOut = timedOut, seconds = 1)
        }
    }

    protected fun single(executor: AgentExecutor, store: RunStore, verifier: Verifier = FakeVerifier()) = SingleRun(
        RunEnvironment(source, temp, store::logDirectory, keepWorkspaces = false),
        executor,
        verifier,
        ContextPreparer(2000, TokenEstimators.create()),
        WorkspaceFactory(TestGit.cli()),
    )

    protected fun runnerWith(store: RunStore, verifier: Verifier, cap: Duration? = null) =
        AgentRunner(settings(cap), store, single(NoopExecutor(), store, verifier)) {}

    protected fun settings(cap: Duration? = null, keepFailed: Boolean = false) =
        RunnerSettings(listOf(Arm.A), 1, 7, Duration.ofMinutes(1), cap, keepFailed)
}
