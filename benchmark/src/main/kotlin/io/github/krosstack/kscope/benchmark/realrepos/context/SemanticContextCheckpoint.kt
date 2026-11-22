package io.github.krosstack.kscope.benchmark.realrepos.context

import io.github.krosstack.kscope.benchmark.context.identity.ContextBenchmarkIdentity
import io.github.krosstack.kscope.benchmark.realrepos.RealCommitTask
import io.github.krosstack.kscope.benchmark.realrepos.measurement.canonicalFuturePath
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.KscopeJson
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE

internal class SemanticContextCheckpoint(output: Path) {
    private val root = canonicalFuturePath(output)

    fun <T> locked(scratch: Path, operation: () -> T): T = lock(root) { lock(canonicalFuturePath(scratch), operation) }

    fun bind(inputs: JsonElement): ContentHash {
        Files.createDirectories(root)
        val identity = ContextBenchmarkIdentity.fingerprint(inputs)
        val expected = "semantic-context-v1:${identity.value}"
        val marker = inside(".identity")
        if (Files.exists(marker)) {
            require(
                Files.readString(marker) == expected,
            ) { "Semantic context run identity changed or output is foreign." }
        } else {
            require(Files.list(root).use { files -> files.allMatch { it.fileName.toString() == ".run.lock" } }) {
                "Semantic context requires a new output directory; existing records are not compatible."
            }
            publish(marker, expected)
        }
        return identity
    }

    fun load(task: RealCommitTask, identity: ContentHash): SemanticContextRecord? {
        val path = inside(
            "tasks/${ContextBenchmarkIdentity.fingerprint(KscopeJson.encodeToJsonElement(task.id)).value}.json",
        )
        if (!Files.exists(path)) return null
        val envelope = KscopeJson.parseToJsonElement(Files.readString(path)).jsonObject
        val payload = envelope.getValue("record")
        require(
            envelope.getValue("digest").jsonPrimitive.content == ContextBenchmarkIdentity.fingerprint(payload).value,
        ) {
            "Semantic context record provenance or contents changed."
        }
        val record = KscopeJson.decodeFromString<SemanticContextRecord>(payload.toString())
        require(record.schemaVersion == 1 && record.task == task && record.identity == identity) {
            "Semantic context record differs from the bound task."
        }
        return record.takeIf { it.settled }
    }

    fun save(record: SemanticContextRecord) {
        val payload = KscopeJson.encodeToJsonElement(record)
        val envelope = buildJsonObject {
            put("record", payload)
            put("digest", ContextBenchmarkIdentity.fingerprint(payload).value)
        }
        val key = ContextBenchmarkIdentity.fingerprint(KscopeJson.encodeToJsonElement(record.task.id))
        publish(inside("tasks/${key.value}.json"), envelope.toString())
    }

    private fun <T> lock(directory: Path, operation: () -> T): T {
        Files.createDirectories(directory)
        return FileChannel.open(directory.resolve(".run.lock"), CREATE, WRITE).use { channel ->
            channel.tryLock().use { lock ->
                checkNotNull(lock) { "Semantic context run is already active." }
                operation()
            }
        }
    }

    private fun inside(relative: String): Path = canonicalFuturePath(root.resolve(relative)).also {
        require(it.startsWith(root)) { "Semantic context path escapes output." }
    }

    private fun publish(target: Path, contents: String) {
        Files.createDirectories(target.parent)
        val temporary = Files.createTempFile(target.parent, ".context-", ".pending")
        try {
            Files.writeString(temporary, contents)
            Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
