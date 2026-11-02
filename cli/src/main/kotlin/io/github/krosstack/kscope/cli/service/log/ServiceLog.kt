package io.github.krosstack.kscope.cli.service.log

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Clock
import java.time.temporal.ChronoUnit

internal class ServiceLog(
    private val file: Path,
    private val clock: Clock = Clock.systemUTC(),
    private val limitBytes: Long = LIMIT_BYTES,
) : ServiceOutput {
    private val rotated: Path = file.resolveSibling(file.fileName.toString() + ".1")

    @Synchronized
    override fun begin() = quietly { Files.write(file, ByteArray(0)) }

    @Synchronized
    override fun line(text: String) = quietly {
        rotateWhenFull()
        Files.writeString(file, stamped(text), Charsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }

    private fun rotateWhenFull() {
        if (Files.exists(file) && Files.size(file) > limitBytes) {
            Files.move(file, rotated, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun stamped(text: String): String {
        val time = clock.instant().truncatedTo(ChronoUnit.SECONDS)
        return text.lines().joinToString(separator = "") { "$time $it\n" }
    }

    // A log that cannot be written must not stop the service it describes.
    private fun quietly(write: () -> Unit) {
        try {
            write()
        } catch (_: IOException) {
            return
        }
    }

    companion object {
        const val LIMIT_BYTES = 1_048_576L
    }
}
