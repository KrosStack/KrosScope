package io.github.krosstack.kscope.service.semantic.process

import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

internal class UnreadStderrStream(private val delegate: InputStream, private val hiddenFrom: String) : InputStream() {
    private val closed = AtomicBoolean()
    private val revealed = AtomicBoolean()

    override fun read(): Int = delegate.read()

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = delegate.read(buffer, offset, length)

    override fun available(): Int {
        if (closed.get()) throw IOException("Stream closed")
        if (Thread.currentThread().name != hiddenFrom) revealed.set(true)
        return if (revealed.get()) delegate.available() else 0
    }

    override fun close() {
        closed.set(true)
        delegate.close()
    }
}
