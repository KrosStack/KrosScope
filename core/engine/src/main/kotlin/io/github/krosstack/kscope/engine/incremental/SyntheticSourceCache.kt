package io.github.krosstack.kscope.engine.incremental

import io.github.krosstack.kscope.engine.hashing.digestOf
import io.github.krosstack.kscope.engine.pipeline.PlannedUnit
import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.spi.SyntheticSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.serializer
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@Serializable
internal class CachedSynthesis(val fingerprint: String, val sources: List<SyntheticSource>)

/**
 * Synthetic sources are returned by synthesizers and never stored in the graph, so a synthesizer whose units are all
 * current would otherwise have to run again. The cache is derived data: when it is missing or stale the synthesizer
 * simply runs.
 */
internal class SyntheticSourceCache(private val file: Path?) {
    private val entries: MutableMap<String, CachedSynthesis> = load().toMutableMap()
    private var changed = false

    fun lookup(producer: ProducerRef, units: List<PlannedUnit>): List<SyntheticSource>? =
        entries[keyOf(producer)]?.takeIf { it.fingerprint == fingerprintOf(units) }?.sources

    fun remember(producer: ProducerRef, units: List<PlannedUnit>, sources: List<SyntheticSource>) {
        entries[keyOf(producer)] = CachedSynthesis(fingerprintOf(units), sources)
        changed = true
    }

    /** Writes the cache, keeping only the entries of [active] synthesizers. */
    fun save(active: Collection<ProducerRef>) {
        val target = file ?: return
        val activeKeys = active.map(::keyOf).toSet()
        changed = entries.keys.retainAll(activeKeys) || changed
        if (!changed) return
        val text = KscopeJson.encodeToString(serializer, entries)
        Files.createDirectories(target.parent)
        val temporary = target.resolveSibling(target.fileName.toString() + ".tmp")
        Files.writeString(temporary, text)
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
        changed = false
    }

    private fun load(): Map<String, CachedSynthesis> {
        val source = file?.takeIf { Files.isRegularFile(it) } ?: return emptyMap()
        return try {
            KscopeJson.decodeFromString(serializer, Files.readString(source))
        } catch (_: IOException) {
            emptyMap()
        } catch (_: SerializationException) {
            emptyMap()
        }
    }

    private fun keyOf(producer: ProducerRef) = "${producer.id}@${producer.version}"

    private fun fingerprintOf(units: List<PlannedUnit>): String = digestOf(
        units.sortedBy { it.owner.key }.flatMap { listOf(it.owner.kind.name, it.owner.key, it.fingerprint.value) },
    )

    private companion object {
        val serializer = serializer<Map<String, CachedSynthesis>>()
    }
}
