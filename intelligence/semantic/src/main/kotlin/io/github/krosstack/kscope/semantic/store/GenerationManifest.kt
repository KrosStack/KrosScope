package io.github.krosstack.kscope.semantic.store

import io.github.krosstack.kscope.model.KscopeJson
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal class GenerationManifest(private val directory: Path) {
    fun read(): Map<String, String> = try {
        KscopeJson.decodeFromString<Map<String, String>>(Files.readString(directory.resolve(NAME)))
            .filter { (provider, name) -> valid(provider, name) }.toSortedMap()
    } catch (_: IOException) {
        emptyMap()
    } catch (_: SerializationException) {
        emptyMap()
    }

    fun write(entries: Map<String, String>) {
        val temporary = directory.resolve("$NAME.tmp")
        Files.writeString(temporary, KscopeJson.encodeToString<Map<String, String>>(entries.toSortedMap()))
        Files.move(
            temporary,
            directory.resolve(NAME),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    private fun valid(provider: String, name: String): Boolean = provider.matches(Regex("[A-Za-z0-9_-]+")) &&
        name.matches(Regex("embeddings-${Regex.escape(provider)}-[0-9a-f]{64}\\.bin"))

    companion object {
        const val NAME = "embeddings.json"
    }
}
