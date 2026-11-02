package io.github.krosstack.kscope.benchmark.agent.launch

import io.github.krosstack.kscope.benchmark.agent.telemetry.EventParser
import io.github.krosstack.kscope.benchmark.agent.workspace.Workspace
import java.nio.file.Path

internal enum class PromptTransport { STDIN, ARGUMENT }

internal class AgentRequest(
    val workspace: Workspace,
    val prompt: String,
    val promptFile: Path,
    val logDirectory: Path,
    val referencePatch: Path,
)

internal class AgentInvocation(val command: List<String>, val stdin: Path?)

internal interface AgentAdapter {
    val name: String
    val model: String?
    val executableName: String
    val parser: EventParser

    fun invocation(executable: Path, request: AgentRequest): AgentInvocation

    fun environment(base: Map<String, String>): Map<String, String>
}
