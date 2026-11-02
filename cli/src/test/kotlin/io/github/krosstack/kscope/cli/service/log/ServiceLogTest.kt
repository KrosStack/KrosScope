package io.github.krosstack.kscope.cli.service.log

import io.github.krosstack.kscope.cli.service.entry.inTemporaryDirectory
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServiceLogTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-12T08:12:40.987Z"), ZoneOffset.UTC)
    private val stamp = "2026-10-12T08:12:40Z"

    private fun log(home: Path, limitBytes: Long = ServiceLog.LIMIT_BYTES) =
        ServiceLog(home.resolve("service.log"), clock, limitBytes)

    @Test
    fun `each line is prefixed with the time in UTC to the second and written as UTF-8`() =
        inTemporaryDirectory { home ->
            val log = log(home)

            log.begin()
            log.line("/work/caf\u00e9: served.")
            log.line("one\ntwo")

            val bytes = Files.readAllBytes(home.resolve("service.log"))
            assertEquals(
                "$stamp /work/caf\u00e9: served.\n$stamp one\n$stamp two\n",
                bytes.toString(Charsets.UTF_8),
            )
        }

    @Test
    fun `begin starts the file anew and leaves the rotated one`() = inTemporaryDirectory { home ->
        Files.writeString(home.resolve("service.log"), "from an earlier run\n")
        Files.writeString(home.resolve("service.log.1"), "older still\n")

        log(home).begin()

        assertEquals("", Files.readString(home.resolve("service.log")))
        assertEquals("older still\n", Files.readString(home.resolve("service.log.1")))
    }

    @Test
    fun `a file that passes the limit is moved to the rotated name, replacing the older one, and started anew`() =
        inTemporaryDirectory { home ->
            val log = log(home, limitBytes = LIMIT_BYTES)
            log.begin()

            for (number in 1..5) log.line("line-$number")

            assertEquals(stamped(1..4), Files.readString(home.resolve("service.log.1")))
            assertEquals(stamped(5..5), Files.readString(home.resolve("service.log")))
            for (number in 6..9) log.line("line-$number")
            assertEquals(stamped(5..8), Files.readString(home.resolve("service.log.1")))
            assertEquals(stamped(9..9), Files.readString(home.resolve("service.log")))
        }

    @Test
    fun `a log that cannot be written never throws`() = inTemporaryDirectory { home ->
        val blocked = Files.createDirectory(home.resolve("service.log"))
        val missing = ServiceLog(home.resolve("no").resolve("such").resolve("service.log"), clock)

        log(home).begin()
        log(home).line("a directory is in the way")
        missing.begin()
        missing.line("the directory does not exist")

        assertTrue(Files.isDirectory(blocked))
    }

    private fun stamped(lines: IntRange): String = lines.joinToString(separator = "") { "$stamp line-$it\n" }

    private companion object {
        const val LIMIT_BYTES = 100L
    }
}
