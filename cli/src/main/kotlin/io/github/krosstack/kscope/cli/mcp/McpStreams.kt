package io.github.krosstack.kscope.cli.mcp

import kotlinx.serialization.json.JsonObject
import java.io.InputStream
import java.io.Writer

internal data class McpStreams(val input: InputStream, val output: Writer, val error: Writer) {
    fun respond(reply: JsonObject?) {
        if (reply == null) return
        synchronized(output) {
            output.write(reply.toString() + "\n")
            output.flush()
        }
    }
}
