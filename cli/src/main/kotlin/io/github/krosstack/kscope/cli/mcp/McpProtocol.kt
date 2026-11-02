package io.github.krosstack.kscope.cli.mcp

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal class McpProtocol(private val streams: McpStreams, private val call: (String, JsonElement?) -> McpToolResult) {
    private var legacy = false
    private val worker = McpWorker(streams, ::respond)

    fun run() {
        worker.use {
            it.start()
            val lines = McpLines(streams.input)
            while (true) {
                val line = lines.next() ?: break
                line.fold(
                    onSuccess = ::readLine,
                    onFailure = { queueError(McpResults.error(null, PARSE_ERROR, "Parse error")) },
                )
            }
        }
    }

    private fun readLine(line: Pair<String, Boolean>): Unit = if (line.second) {
        queueError(McpResults.error(null, INVALID_REQUEST, "Request line exceeds 1 MiB"))
    } else {
        read(line.first)
    }

    private fun read(line: String): Unit = runCatching { McpJson.parse(line) }.fold(
        onSuccess = ::accept,
        onFailure = { queueError(McpResults.error(null, PARSE_ERROR, "Parse error")) },
    )

    private fun accept(element: JsonElement) {
        if (McpRequest.isNotification(element)) {
            val notification = McpRequest.from(element)
            if (notification?.method == "notifications/cancelled") worker.cancel(notification.params["requestId"])
            return
        }
        val request = McpRequest.from(element)
        if (request == null) {
            queueError(McpResults.error(McpRequest.id(element), INVALID_REQUEST, "Invalid request"))
        } else {
            worker.offer(McpPendingRequest(request, null))
        }
    }

    private fun queueError(error: JsonObject) = worker.offer(McpPendingRequest(null, error))

    private fun respond(request: McpRequest): JsonObject? {
        if (request.id == null) return null
        if (request.method == "initialize") legacy = true
        val error = if (!legacy && request.method != "server/discover") McpMetadata.error(request) else null
        return error ?: dispatch(request)
    }

    private fun dispatch(request: McpRequest): JsonObject {
        val result = when (request.method) {
            "initialize", "server/discover" -> opening(request)
            "ping" -> McpResults.complete(!legacy)
            "tools/list" -> McpResults.tools(!legacy)
            "tools/call" -> return tool(request)
            else -> null
        }
        return result?.let { McpResults.result(checkNotNull(request.id), it) }
            ?: McpResults.error(request.id, METHOD_NOT_FOUND, "Unknown method: ${request.method}")
    }

    private fun opening(request: McpRequest): JsonObject =
        if (request.method == "initialize") McpResults.initialize(request.params) else McpResults.discover()

    private fun tool(request: McpRequest): JsonObject {
        val name = request.params["name"] as? JsonPrimitive
        val valid = name?.isString == true && McpTools.contains(name.content)
        if (!valid) {
            val label = name?.content ?: "null"
            return McpResults.error(request.id, INVALID_PARAMS, "Unknown tool: $label")
        }
        val result = runCatching {
            call(checkNotNull(name).content, request.params["arguments"])
        }.getOrElse { failure ->
            val message = "error: ${failure.message.orEmpty()}"
            streams.error.write(message + "\n")
            streams.error.flush()
            McpToolResult(message, true)
        }
        return McpResults.result(checkNotNull(request.id), McpResults.tool(result, !legacy))
    }

    companion object {
        private const val PARSE_ERROR = -32700
        private const val INVALID_REQUEST = -32600
        private const val METHOD_NOT_FOUND = -32601
        private const val INVALID_PARAMS = -32602
        const val INSTRUCTIONS = "KrosScope is local code intelligence for Kotlin, Android and Kotlin Multiplatform. " +
            "Call context with the task before reading files. Analyze finds the files and tests a task touches; " +
            "symbol finds declarations, impact finds affected code, related finds nearby code, and info describes " +
            "the workspace. Results come from an index; scan brings it up to date and never runs the build."
    }
}
