package io.github.krosstack.kscope.cli.mcp

import io.github.krosstack.kscope.cli.command.ScanRunner
import io.github.krosstack.kscope.cli.serve.ServeSettings

internal data class McpSettings(
    val noServer: Boolean = false,
    val serve: ServeSettings = ServeSettings(),
    val scanRunner: ScanRunner = ScanRunner.DEFAULT,
    val executeTool: ((List<String>) -> McpToolResult)? = null,
)
