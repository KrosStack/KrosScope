package io.github.krosstack.kscope.cli.mcp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

internal object McpTools {
    private const val MIN_BUDGET = 500
    private const val MAX_BUDGET = 200_000
    private val tools = listOf(
        McpTool(
            "context",
            "Task context",
            "Call this tool before reading files for a task to get the source worth reading within a token budget.",
            listOf(
                McpArgument("task", "The task to implement or investigate, in your own words."),
                McpArgument("budget", "Maximum tokens in the context.", false, integerRange = MIN_BUDGET..MAX_BUDGET),
            ),
        ),
        McpTool(
            "analyze",
            "Analyze a task or name",
            "Find the files, modules and tests a task touches, or inspect a declaration by name or file.",
            listOf(
                McpArgument("query", "A task description, symbol name or workspace file."),
                McpArgument("detail", "How much detail to return.", false, listOf("brief", "standard", "full")),
            ),
        ),
        McpTool(
            "symbol",
            "Find a symbol",
            "Find declarations matching a symbol name.",
            listOf(McpArgument("name", "The symbol name to find.")),
        ),
        McpTool(
            "impact",
            "Change impact",
            "Find code and tests affected by changing a symbol or file.",
            listOf(McpArgument("node", "The symbol name, node identifier or workspace file to change.")),
        ),
        McpTool(
            "related",
            "Related code",
            "Find code related to a symbol or file.",
            listOf(McpArgument("node", "The symbol name, node identifier or workspace file to explore.")),
        ),
        McpTool("info", "Workspace information", "Describe the indexed workspace and its index."),
        McpTool(
            "scan",
            "Update the index",
            "Bring the index up to date at level resolved, without ever running the build.",
        ),
    )
    val catalogue = JsonArray(tools.map { it.schema() })

    fun contains(name: String): Boolean = tools.any { it.name == name }

    fun call(name: String, arguments: JsonElement?, execute: (List<String>) -> McpToolResult): McpToolResult =
        tools.first { it.name == name }.call(arguments, execute)
}
