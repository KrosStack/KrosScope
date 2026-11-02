package io.github.krosstack.kscope.cli.serve

import io.github.krosstack.kscope.cli.resolve
import io.github.krosstack.kscope.cli.serve.token.OwnerOnlyFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@Serializable
internal data class ServeMetadata(
    val pid: Long,
    val port: Int,
    val token: String,
    val toolVersion: String,
    val protocol: Int,
    val startTime: String,
) {
    fun write(workspace: Path) = writeTo(file(workspace))

    fun writeTo(target: Path) {
        Files.createDirectories(target.parent)
        val temporary = OwnerOnlyFile.createTemporary(target.parent, "serve-", ".tmp")
        try {
            Files.writeString(temporary, ServeWire.encode(this), Charsets.UTF_8)
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    // Another server can have replaced the file after the index directory was deleted.
    fun deleteFrom(workspace: Path) {
        if (read(workspace)?.token == token) Files.deleteIfExists(file(workspace))
    }

    companion object {
        fun file(workspace: Path): Path = workspace.resolve(".kscope/serve.json")

        private val lenient = Json(ServeWire.json) { ignoreUnknownKeys = true }

        fun read(workspace: Path): ServeMetadata? = readFrom(file(workspace))

        fun readFrom(source: Path): ServeMetadata? = try {
            lenient.decodeFromString<ServeMetadata>(Files.readString(source, Charsets.UTF_8))
        } catch (_: IOException) {
            null
        } catch (_: SerializationException) {
            null
        }
    }
}
