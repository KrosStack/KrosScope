package io.github.krosstack.kscope.benchmark.agent.launch

import io.github.krosstack.kscope.benchmark.agent.telemetry.CodexEventParser
import io.github.krosstack.kscope.benchmark.agent.telemetry.EventParser
import java.nio.file.Path

internal class CodexAdapter(
    override val model: String? = null,
    private val transport: PromptTransport = PromptTransport.STDIN,
) : AgentAdapter {
    override val name = "codex"
    override val executableName = "codex"
    override val parser: EventParser = CodexEventParser()

    override fun invocation(executable: Path, request: AgentRequest): AgentInvocation {
        val options = listOf(
            executable.toString(), "exec", "--json", "--ephemeral", "--skip-git-repo-check",
            "-s", "workspace-write", "-C", request.workspace.root.toString(),
        ) + model?.let { listOf("-m", it) }.orEmpty()
        return when (transport) {
            PromptTransport.STDIN -> AgentInvocation(options + STDIN_MARKER, request.promptFile)
            PromptTransport.ARGUMENT -> AgentInvocation(options + request.prompt, null)
        }
    }

    override fun environment(base: Map<String, String>): Map<String, String> = base

    private companion object {
        const val STDIN_MARKER = "-"
    }
}
