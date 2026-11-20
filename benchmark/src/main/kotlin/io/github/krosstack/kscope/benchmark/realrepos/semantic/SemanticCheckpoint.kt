package io.github.krosstack.kscope.benchmark.realrepos.semantic

import io.github.krosstack.kscope.model.KscopeJson
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import java.util.HexFormat

internal class SemanticCheckpoint(private val root: Path) {
    fun bind(inputs: List<String>): String {
        Files.createDirectories(root)
        val key = digest(KscopeJson.encodeToString(inputs))
        val marker = root.resolve(".identity")
        if (Files.exists(marker)) {
            require(Files.readString(marker) == key) { "Semantic checkpoint identity changed" }
        } else {
            write(marker, key)
        }
        return key
    }

    fun load(id: String): SemanticRecord? = root.resolve("tasks/${digest(id)}.json").let {
        if (Files.exists(it)) KscopeJson.decodeFromString<SemanticRecord>(Files.readString(it)) else null
    }

    fun save(record: SemanticRecord) {
        write(root.resolve("tasks/${digest(record.task.id)}.json"), KscopeJson.encodeToString(record))
    }

    companion object {
        fun digest(text: String): String = digest(text.toByteArray(Charsets.UTF_8))

        fun digest(bytes: ByteArray): String = HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(bytes),
        )

        fun write(target: Path, text: String) {
            Files.createDirectories(target.parent)
            val temporary = target.resolveSibling("${target.fileName}.pending")
            Files.writeString(temporary, text, Charsets.UTF_8)
            Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING)
        }
    }
}
