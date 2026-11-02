package io.github.krosstack.kscope.benchmark.agent.launch

import io.github.krosstack.kscope.benchmark.agent.workspace.GitCli
import io.github.krosstack.kscope.benchmark.agent.workspace.Workspace
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentAdaptersTest {
    private val workspace = Workspace(Path.of("workspace-root"), GitCli())
    private val promptFile = Path.of("logs", "prompt.txt")
    private val request = AgentRequest(workspace, "THE PROMPT", promptFile, Path.of("logs"), Path.of("reference.patch"))

    @Test
    fun `claude reads the prompt from standard input with the fixed isolated options`() {
        val invocation = ClaudeAdapter().invocation(Path.of("claude"), request)

        assertEquals(
            listOf(
                "claude", "-p", "--model", "sonnet", "--output-format", "stream-json", "--verbose",
                "--setting-sources", "", "--strict-mcp-config", "--disable-slash-commands",
                "--tools", "Read,Edit,Write,Glob,Grep", "--permission-mode", "acceptEdits",
                "--no-session-persistence", "--no-chrome",
            ),
            invocation.command,
        )
        assertEquals(promptFile, invocation.stdin)
    }

    @Test
    fun `claude can pass the prompt as an argument right after the print flag`() {
        val invocation = ClaudeAdapter("opus", PromptTransport.ARGUMENT).invocation(Path.of("claude"), request)

        assertEquals(listOf("claude", "-p", "THE PROMPT", "--model", "opus"), invocation.command.take(5))
        assertNull(invocation.stdin)
    }

    @Test
    fun `claude children inherit no variable that starts with CLAUDE or ANTHROPIC`() {
        val base = mapOf(
            "PATH" to "p",
            "CLAUDECODE" to "1",
            "claude_code_entrypoint" to "cli",
            "ANTHROPIC_API_KEY" to "k",
            "Anthropic_Base_Url" to "u",
            "CLAUDE_CONFIG_DIR" to "d",
            "HOME" to "h",
            "MYCLAUDE" to "x",
        )

        val environment = ClaudeAdapter().environment(base)

        assertEquals(mapOf("PATH" to "p", "HOME" to "h", "MYCLAUDE" to "x"), environment)
    }

    @Test
    fun `claude exposes no shell tool`() {
        assertFalse(ClaudeAdapter.TOOLS.split(',').any { it.contains("Bash") })
    }

    @Test
    fun `codex runs exec in the workspace with a shell sandbox and reads the prompt from standard input`() {
        val invocation = CodexAdapter("gpt-x").invocation(Path.of("codex"), request)

        assertEquals(
            listOf(
                "codex", "exec", "--json", "--ephemeral", "--skip-git-repo-check", "-s", "workspace-write",
                "-C", workspace.root.toString(), "-m", "gpt-x", "-",
            ),
            invocation.command,
        )
        assertEquals(promptFile, invocation.stdin)
    }

    @Test
    fun `codex omits the model option when none is chosen and can take the prompt as an argument`() {
        val invocation = CodexAdapter(null, PromptTransport.ARGUMENT).invocation(Path.of("codex"), request)

        assertFalse("-m" in invocation.command)
        assertEquals("THE PROMPT", invocation.command.last())
        assertTrue(invocation.stdin == null)
    }
}
