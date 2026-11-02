package io.github.krosstack.kscope.analyzer.kotlin.process

import io.github.krosstack.kscope.spi.worker.WorkerProtocol
import io.github.krosstack.kscope.spi.worker.WorkerResponse
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

class WorkerChannelLimitTest {
    private class StreamsProcess(private val output: String) : Process() {
        override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()

        override fun getInputStream(): InputStream = ByteArrayInputStream(output.toByteArray())

        override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

        override fun waitFor(): Int = 0

        override fun exitValue(): Int = 0

        override fun destroy() = Unit
    }

    @Test
    fun `an output line over the limit is a message that cannot be read and the next message still arrives`() {
        val completed = WorkerProtocol.encode(WorkerResponse.Completed("a-1"))
        val runaway = "{\"type\":\"batch\",\"pad\":\"" + "x".repeat(5_000) + "\"}"
        val channel = WorkerChannel(StreamsProcess("$runaway\n$completed\n"), {}, maxLine = 1_000)
        val first = assertIs<Answer.Unreadable>(channel.next(10.seconds))
        assertEquals(runaway.take(BoundedLines.PREVIEW), first.line)
        assertEquals(WorkerResponse.Completed("a-1"), assertIs<Answer.Message>(channel.next(10.seconds)).response)
    }
}
