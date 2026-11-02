package io.github.krosstack.kscope.benchmark.agent.verification

import io.github.krosstack.kscope.benchmark.agent.AgentTask
import io.github.krosstack.kscope.benchmark.agent.VerificationRecord
import io.github.krosstack.kscope.benchmark.agent.launch.ChildEnvironment
import io.github.krosstack.kscope.benchmark.agent.launch.ProcessRunner
import io.github.krosstack.kscope.benchmark.agent.launch.ProcessSpec
import io.github.krosstack.kscope.benchmark.agent.launch.isWindows
import io.github.krosstack.kscope.benchmark.agent.workspace.Workspace
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

internal class GradleVerifier(
    private val runner: ProcessRunner = ProcessRunner(),
    private val environment: Map<String, String> = System.getenv(),
    private val androidSdk: String? = null,
) : Verifier {
    override fun verify(
        task: AgentTask,
        workspace: Workspace,
        logDirectory: Path,
        limit: Duration?,
    ): VerificationRecord {
        val log = logDirectory.resolve("gradle.log")
        val stderr = logDirectory.resolve("gradle.err")
        val spec = ProcessSpec(command(task, workspace), workspace.root, gradleEnvironment(), null, stderr)
        val outcome = runner.run(spec, log, limit ?: Duration.ofMinutes(task.spec.verify.timeoutMinutes.toLong()))
        val text = try {
            Files.readString(log)
        } catch (_: IOException) {
            ""
        }
        return VerificationAssessment.assess(
            task.spec,
            TestResults.read(workspace.root),
            GradleLog(text, outcome.exitCode, outcome.timedOut, outcome.wallMs / MILLIS_PER_SECOND),
        )
    }

    private fun command(task: AgentTask, workspace: Workspace): List<String> {
        val wrapper = workspace.root.resolve(if (isWindows()) "gradlew.bat" else "gradlew").toString()
        return listOf(wrapper) + task.spec.verify.gradleTasks + GRADLE_OPTIONS
    }

    private fun gradleEnvironment(): Map<String, String> {
        val additions = buildMap {
            if (environment.keys.none { it.equals("GRADLE_OPTS", ignoreCase = true) }) put("GRADLE_OPTS", CLIENT_HEAP)
            androidSdk?.let { put("ANDROID_HOME", it) }
        }
        return ChildEnvironment.with(environment, additions)
    }

    private companion object {
        const val CLIENT_HEAP = "-Xmx256m"
        const val MILLIS_PER_SECOND = 1000
        val GRADLE_OPTIONS = listOf(
            "--no-build-cache",
            "--console=plain",
            "--continue",
            "-Dorg.gradle.daemon.idletimeout=600000",
        )
    }
}
