package io.github.krosstack.kscope.service.semantic.process

import java.io.InputStream
import java.io.OutputStream

internal class StderrClosingProcess(private val delegate: Process, private val stderr: InputStream) : Process() {
    override fun getOutputStream(): OutputStream = delegate.outputStream

    override fun getInputStream(): InputStream = delegate.inputStream

    override fun getErrorStream(): InputStream = stderr

    override fun waitFor(): Int = delegate.waitFor()

    override fun exitValue(): Int = delegate.exitValue()

    override fun destroy() {
        stderr.close()
        delegate.destroy()
    }

    override fun destroyForcibly(): Process {
        stderr.close()
        delegate.destroyForcibly()
        return this
    }

    override fun isAlive(): Boolean = delegate.isAlive

    override fun toHandle(): ProcessHandle = delegate.toHandle()
}
