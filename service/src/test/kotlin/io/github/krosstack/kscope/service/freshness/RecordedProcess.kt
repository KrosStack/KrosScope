package io.github.krosstack.kscope.service.freshness

import java.io.InputStream
import java.io.OutputStream

internal class RecordedProcess : Process() {
    var ended = false
        private set

    override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()

    override fun getInputStream(): InputStream = InputStream.nullInputStream()

    override fun getErrorStream(): InputStream = InputStream.nullInputStream()

    override fun waitFor(): Int = 0

    override fun exitValue(): Int = 0

    override fun destroy() {
        ended = true
    }
}
