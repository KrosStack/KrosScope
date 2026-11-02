package io.github.krosstack.kscope.cli.mcp

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer

internal class McpLines(input: InputStream) {
    private val input = input.buffered()

    fun next(): Result<Pair<String, Boolean>>? {
        val bytes = ByteArrayOutputStream()
        var oversized = false
        var next = input.read()
        if (next < 0) return null
        while (next >= 0 && next != '\n'.code) {
            if (bytes.size() < MAX_BYTES) bytes.write(next) else oversized = true
            next = input.read()
        }
        return if (oversized) {
            Result.success("" to true)
        } else {
            runCatching {
                val text = Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes.toByteArray())).toString()
                text.removeSuffix("\r") to false
            }
        }
    }

    private companion object {
        const val MAX_BYTES = 1_048_576
    }
}
