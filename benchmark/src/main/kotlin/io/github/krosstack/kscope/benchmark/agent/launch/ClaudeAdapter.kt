package io.github.krosstack.kscope.benchmark.agent.launch

import io.github.krosstack.kscope.benchmark.agent.telemetry.ClaudeEventParser
import io.github.krosstack.kscope.benchmark.agent.telemetry.EventParser
import java.nio.file.Path

internal class ClaudeAdapter(
    override val model: String = DEFAULT_MODEL,
    private val transport: PromptTransport = PromptTransport.STDIN,
) : AgentAdapter {
    override val name = "claude"
    override val executableName = "claude"
    override val parser: EventParser = ClaudeEventParser()

    override fun invocation(executable: Path, request: AgentRequest): AgentInvocation {
        val prompt = if (transport == PromptTransport.ARGUMENT) listOf(request.prompt) else emptyList()
        val command = listOf(executable.toString(), "-p") + prompt + listOf("--model", model) + FIXED_OPTIONS
        return AgentInvocation(command, if (transport == PromptTransport.STDIN) request.promptFile else null)
    }

    override fun environment(base: Map<String, String>): Map<String, String> =
        ChildEnvironment.without(BLOCKED_PREFIXES, base)

    companion object {
        const val DEFAULT_MODEL = "sonnet"
        val BLOCKED_PREFIXES = listOf("CLAUDE", "ANTHROPIC")
        const val TOOLS = "Read,Edit,Write,Glob,Grep"
        private val FIXED_OPTIONS = listOf(
            "--output-format", "stream-json",
            "--verbose",
            "--setting-sources", "",
            "--strict-mcp-config",
            "--disable-slash-commands",
            "--tools", TOOLS,
            "--permission-mode", "acceptEdits",
            "--no-session-persistence",
            "--no-chrome",
        )
    }
}
