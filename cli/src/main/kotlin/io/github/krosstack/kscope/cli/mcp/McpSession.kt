package io.github.krosstack.kscope.cli.mcp

import java.nio.file.Path

internal class McpSession(
    private val workspace: Path,
    private val streams: McpStreams,
    private val settings: McpSettings = McpSettings(),
) {
    fun run() {
        McpHost.open(workspace, settings, streams.error).use {
            val commands = McpCommands(workspace, settings, streams.error)
            val execute = settings.executeTool ?: commands::execute
            McpProtocol(streams) { name, arguments -> McpTools.call(name, arguments, execute) }.run()
        }
    }
}
