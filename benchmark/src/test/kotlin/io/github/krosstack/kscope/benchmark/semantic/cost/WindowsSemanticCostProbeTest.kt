package io.github.krosstack.kscope.benchmark.semantic.cost

import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WindowsSemanticCostProbeTest {
    @Test
    fun `windows rounds parse bounded CSV and release every owned resource`() {
        val csv = "\"app,\"\"quoted\"\".exe\",\"11\",\"Console\",\"1\",\"1,234 K\"\n" +
            "\"app.exe\",\"22\",\"Console\",\"1\",\"5\u00a0678 K\"\n"
        assertRead(csv, mapOf(11L to 1_263_616L, 22L to 5_814_272L, 33L to null))
        assertRead("\"app.exe\",\"11\",\"Console\",\"1\",\"9.876 K\"", mapOf(11L to 10_113_024L))
        for (value in listOf("N/A", "0 K", "9223372036854775808 K", "9007199254740992 K")) {
            assertRead("\"app.exe\",\"11\",\"Console\",\"1\",\"$value\"", mapOf(11L to null))
        }
        assertRead("\"app.exe\",11,\"Console\",\"1\",\"123 K\"", mapOf(11L to null))
        assertRead(csv, mapOf(11L to null), exit = 1)
        assertRead(csv, mapOf(11L to null), finished = false)
        assertRead(csv + "x".repeat(1_048_576), mapOf(11L to null))
        assertRead(csv, mapOf(11L to null), failStart = true)
    }

    private fun assertRead(
        output: String,
        expected: Map<Long, Long?>,
        exit: Int = 0,
        finished: Boolean = true,
        failStart: Boolean = false,
    ) {
        val process = RecordedProcess(exit, finished)
        var launches = 0
        var outputFile: Path? = null
        val reader = WindowsResidentMemory { builder ->
            launches++
            assertEquals(listOf("tasklist.exe", "/FO", "CSV", "/NH"), builder.command())
            assertEquals(ProcessBuilder.Redirect.DISCARD, builder.redirectError())
            val file = assertNotNull(builder.redirectOutput().file()).toPath()
            outputFile = file
            assertTrue(Files.exists(file))
            if (failStart) throw IOException("start failed")
            Files.writeString(file, output)
            process
        }
        assertEquals(expected, reader.read(expected.keys.toList()))
        assertEquals(1, launches)
        assertFalse(Files.exists(assertNotNull(outputFile)))
        if (!failStart) {
            assertEquals(listOf(2L to TimeUnit.SECONDS), process.waits)
            assertEquals(!finished, process.destroyed)
            assertEquals(!finished, process.joined)
        }
    }

    private class RecordedProcess(private val exit: Int, private val finished: Boolean) : Process() {
        val waits = mutableListOf<Pair<Long, TimeUnit>>()
        var destroyed = false
        var joined = false
        private val stdout = ByteArrayInputStream(byteArrayOf())
        private val stderr = ByteArrayInputStream(byteArrayOf())
        private val stdin = ByteArrayOutputStream()

        override fun getOutputStream() = stdin

        override fun getInputStream() = stdout

        override fun getErrorStream() = stderr

        override fun waitFor(): Int {
            joined = true
            return exit
        }

        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean {
            waits += timeout to unit
            return finished
        }

        override fun exitValue() = exit

        override fun isAlive() = !finished && !destroyed

        override fun destroy() {
            destroyed = true
        }

        override fun destroyForcibly(): Process {
            destroyed = true
            return this
        }
    }
}
