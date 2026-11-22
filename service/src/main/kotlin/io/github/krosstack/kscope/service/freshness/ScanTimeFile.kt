package io.github.krosstack.kscope.service.freshness

import io.github.krosstack.kscope.model.KscopeJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.format.DateTimeParseException

@Serializable
internal data class DirtyPaths(val exceeded: Boolean = false, val paths: List<String> = emptyList())

@Serializable
internal data class ScanTimes(
    val format: Int,
    val startedAt: String,
    val finishedAt: String,
    val dirtyPaths: Map<String, DirtyPaths>? = null,
)

internal class ScanTimeFile(private val indexDirectory: Path) {
    private val file = indexDirectory.resolve(FILE_NAME)

    fun read(): ScanTimes? = try {
        KscopeJson.decodeFromString(ScanTimes.serializer(), Files.readString(file, Charsets.UTF_8))
            .takeIf { it.format == FORMAT && Instant.parse(it.startedAt) <= Instant.parse(it.finishedAt) }
    } catch (_: IOException) {
        null
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: DateTimeParseException) {
        null
    }

    fun write(started: Instant, finished: Instant, dirtyPaths: Map<String, DirtyPaths>? = null) {
        val times = ScanTimes(FORMAT, started.toString(), finished.toString(), dirtyPaths)
        try {
            replace(KscopeJson.encodeToString(ScanTimes.serializer(), times) + "\n")
        } catch (_: IOException) {
            Files.deleteIfExists(file)
        }
    }

    private fun replace(text: String) {
        val temporary = Files.createTempFile(indexDirectory, "scan-", ".tmp")
        try {
            Files.writeString(temporary, text, Charsets.UTF_8)
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    companion object {
        const val FILE_NAME = "scan.json"
        private const val FORMAT = 1
    }
}
