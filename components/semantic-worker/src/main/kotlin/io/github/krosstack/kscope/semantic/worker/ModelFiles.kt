package io.github.krosstack.kscope.semantic.worker

import io.github.krosstack.kscope.model.KscopeJson
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.inputStream
import kotlin.io.path.readText

internal class ModelFiles(directory: Path) {
    val descriptor: ModelDescriptor = KscopeJson.decodeFromString(directory.resolve("model.json").readText())
    val model: Path = directory.resolve("model.onnx")
    val tokenizer: Path = directory.resolve("tokenizer.json")

    init {
        require(descriptor.pooling == "cls") { "Unsupported pooling" }
        require(descriptor.files.keys.containsAll(listOf("model.onnx", "tokenizer.json", "LICENSE")))
        descriptor.files.toSortedMap().forEach { (name, expected) ->
            require(Path.of(name).fileName.toString() == name) { "Invalid model file name" }
            val digest = MessageDigest.getInstance("SHA-256")
            directory.resolve(name).inputStream().use { stream ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var count = stream.read(buffer)
                while (count >= 0) {
                    digest.update(buffer, 0, count)
                    count = stream.read(buffer)
                }
            }
            check(digest.digest().toHexString() == expected) { "Model checksum mismatch: $name" }
        }
    }
}
