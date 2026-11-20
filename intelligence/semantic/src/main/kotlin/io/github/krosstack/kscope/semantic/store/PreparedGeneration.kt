package io.github.krosstack.kscope.semantic.store

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.HexFormat

public class PreparedGeneration internal constructor(
    internal val temporary: Path,
    internal val provider: String,
    internal val name: String,
) : AutoCloseable {
    internal var closed: Boolean = false
        private set

    override fun close() {
        closed = true
        Files.deleteIfExists(temporary)
    }

    internal companion object {
        fun create(
            directory: Path,
            generation: GenerationFile,
            writeTemporary: (Path, ByteArray) -> Unit,
        ): PreparedGeneration {
            val provider = generation.header.provider
            require(provider.matches(Regex("[A-Za-z0-9_-]+")))
            val bytes = generation.bytes()
            val digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
            val name = "embeddings-$provider-$digest.bin"
            val temporary = Files.createTempFile(directory, "publishing-", ".tmp")
            var prepared = false
            try {
                writeTemporary(temporary, bytes)
                FileChannel.open(temporary, StandardOpenOption.WRITE).use { it.force(true) }
                return PreparedGeneration(temporary, provider, name).also { prepared = true }
            } finally {
                if (!prepared) Files.deleteIfExists(temporary)
            }
        }
    }
}
