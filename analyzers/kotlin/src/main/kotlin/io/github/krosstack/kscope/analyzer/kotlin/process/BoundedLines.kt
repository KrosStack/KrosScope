package io.github.krosstack.kscope.analyzer.kotlin.process

import java.io.Reader

/**
 * Splits text into lines like [java.io.BufferedReader.readLine] does, except that a line longer than [limit]
 * characters is not kept: it is read to its end without being stored and reported as overlong with its first
 * [PREVIEW] characters, so that a runaway line costs time and not memory.
 */
internal class BoundedLines(private val reader: Reader, private val limit: Int) {
    private val line = StringBuilder()
    private var preview: String? = null

    fun forEachLine(onLine: (String) -> Unit, onOverlong: (String) -> Unit) {
        val buffer = CharArray(BUFFER_SIZE)
        while (true) {
            val count = reader.read(buffer)
            if (count < 0) break
            var start = 0
            for (index in 0 until count) {
                if (buffer[index] == '\n') {
                    append(buffer, start, index - start)
                    finish(onLine, onOverlong)
                    start = index + 1
                }
            }
            append(buffer, start, count - start)
        }
        if (line.isNotEmpty() || preview != null) finish(onLine, onOverlong)
    }

    private fun append(buffer: CharArray, from: Int, length: Int) {
        if (length <= 0 || preview != null) return
        if (line.length + length > limit) {
            line.append(buffer, from, minOf(length, PREVIEW))
            preview = line.substring(0, minOf(PREVIEW, line.length))
            line.setLength(0)
            line.trimToSize()
        } else {
            line.append(buffer, from, length)
        }
    }

    private fun finish(onLine: (String) -> Unit, onOverlong: (String) -> Unit) {
        val overlong = preview
        if (overlong != null) onOverlong(overlong) else onLine(line.toString().removeSuffix("\r"))
        line.setLength(0)
        preview = null
    }

    companion object {
        const val PREVIEW = 200
        private const val BUFFER_SIZE = 8192
    }
}
