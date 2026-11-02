package io.github.krosstack.kscope.cli.serve

import java.io.BufferedWriter

internal class ServeOutput(private val writer: BufferedWriter) {
    @Synchronized
    fun send(response: ServeResponse) {
        writer.write(ServeWire.encode(response))
        writer.newLine()
        writer.flush()
    }

    fun write(text: String, stderr: Boolean) = send(
        ServeResponse(kind = "output", stream = if (stderr) "stderr" else "stdout", text = text),
    )
}
