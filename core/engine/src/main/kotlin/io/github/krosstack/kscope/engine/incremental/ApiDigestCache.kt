package io.github.krosstack.kscope.engine.incremental

import io.github.krosstack.kscope.engine.hashing.digestOf
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.KscopeJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.serializer
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@Serializable
internal data class CachedApiDigest(val digest: String?)

/** Derived API digests, including absent answers, keyed by producer, digest version and artifact content. */
internal class ApiDigestCache(private val file: Path?) {
    private val entries = load().toMutableMap()
    private val used = mutableSetOf<String>()
    private var changed = false

    fun lookup(producer: String, version: String, artifact: Artifact): CachedApiDigest? {
        val key = keyOf(producer, version, artifact)
        used += key
        return entries[key]
    }

    fun remember(producer: String, version: String, artifact: Artifact, digest: String?) {
        val key = keyOf(producer, version, artifact)
        val entry = CachedApiDigest(digest)
        used += key
        if (entries.put(key, entry) != entry) changed = true
    }

    fun save() {
        val target = file ?: return
        changed = entries.keys.retainAll(used) || changed
        if (!changed) return
        val text = KscopeJson.encodeToString(serializer, entries.toSortedMap())
        Files.createDirectories(target.parent)
        val temporary = target.resolveSibling(target.fileName.toString() + ".tmp")
        Files.writeString(temporary, text)
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
        changed = false
    }

    private fun load(): Map<String, CachedApiDigest> {
        val source = file?.takeIf { Files.isRegularFile(it) } ?: return emptyMap()
        return try {
            KscopeJson.decodeFromString(serializer, Files.readString(source))
        } catch (_: IOException) {
            emptyMap()
        } catch (_: SerializationException) {
            emptyMap()
        }
    }

    private fun keyOf(producer: String, version: String, artifact: Artifact): String =
        digestOf(listOf(producer, version, artifact.contentHash.value))

    private companion object {
        val serializer = serializer<Map<String, CachedApiDigest>>()
    }
}
