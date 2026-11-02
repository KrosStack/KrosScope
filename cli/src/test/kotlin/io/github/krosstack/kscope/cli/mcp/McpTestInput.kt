package io.github.krosstack.kscope.cli.mcp

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

internal class McpTestInput : InputStream() {
    private val chunks = LinkedBlockingQueue<() -> ByteArray?>()
    private var current = ByteArrayInputStream(byteArrayOf())
    private var ended = false
    private val endRead = CountDownLatch(1)

    fun send(lines: List<String>) {
        val bytes = (lines.joinToString("\n") + "\n").toByteArray(Charsets.UTF_8)
        chunks.put { bytes }
    }

    fun awaitRead() {
        val read = CountDownLatch(1)
        chunks.put {
            read.countDown()
            byteArrayOf()
        }
        assertTrue(read.await(5, TimeUnit.SECONDS), "MCP did not read the submitted requests")
    }

    fun awaitEnd() {
        assertTrue(endRead.await(5, TimeUnit.SECONDS), "MCP did not read EOF")
    }

    override fun read(): Int {
        val bytes = ByteArray(1)
        return if (read(bytes, 0, 1) < 0) -1 else bytes[0].toInt() and 255
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int = when {
        length == 0 -> 0
        ended -> -1
        else -> readChunk(bytes, offset, length)
    }

    private fun readChunk(bytes: ByteArray, offset: Int, length: Int): Int {
        while (current.available() == 0 && !ended) {
            val chunk = chunks.take().invoke()
            ended = chunk == null
            if (ended) endRead.countDown()
            current = ByteArrayInputStream(chunk ?: byteArrayOf())
        }
        return if (ended) -1 else current.read(bytes, offset, length)
    }

    override fun close() {
        chunks.offer { null }
    }
}
