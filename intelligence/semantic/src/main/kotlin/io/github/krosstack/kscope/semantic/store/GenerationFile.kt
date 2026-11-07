package io.github.krosstack.kscope.semantic.store

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.semantic.chunk.Chunk
import io.github.krosstack.kscope.semantic.chunk.ChunkKind
import io.github.krosstack.kscope.semantic.search.ScoredChunk
import kotlinx.serialization.SerializationException
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.HexFormat

public class GenerationFile(public val header: GenerationHeader, public val chunks: List<ScoredChunk>) {
    public fun write(file: Path) {
        Files.write(file, bytes(), StandardOpenOption.CREATE_NEW)
    }

    internal fun bytes(): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { output ->
            output.writeInt(VERSION)
            output.writeUTF(KscopeJson.encodeToString(header))
            chunks.sortedBy { it.chunk.key }.forEach { entry -> writeEntry(output, entry) }
        }
        val bytes = buffer.toByteArray()
        return bytes + MessageDigest.getInstance("SHA-256").digest(bytes)
    }

    private fun writeEntry(output: DataOutputStream, entry: ScoredChunk) {
        output.writeUTF(entry.chunk.key)
        output.writeUTF(entry.chunk.kind.name)
        output.writeUTF(KscopeJson.encodeToString(entry.chunk.artifact))
        output.writeUTF(KscopeJson.encodeToString(entry.chunk.nodes.sortedBy { KscopeJson.encodeToString(it) }))
        output.writeFloat(entry.vector.scale)
        output.write(entry.vector.bytes)
    }

    public companion object {
        private const val VERSION = 1
        private const val DIGEST_BYTES = 32
        private const val MAX_DIMENSIONS = 100_000

        public fun read(file: Path): GenerationFile? = try {
            Files.newInputStream(file).use { read(it) }
        } catch (_: IOException) {
            null
        }

        internal fun read(input: InputStream, expectedDigest: String? = null): GenerationFile? = try {
            input.use {
                val bytes = it.readBytes()
                require(
                    expectedDigest == null ||
                        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)) == expectedDigest,
                )
                decode(bytes)
            }
        } catch (_: IOException) {
            null
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

        private fun decode(bytes: ByteArray): GenerationFile {
            require(bytes.size >= DIGEST_BYTES)
            val payload = bytes.copyOf(bytes.size - DIGEST_BYTES)
            val digest = MessageDigest.getInstance("SHA-256").digest(payload)
            require(digest.contentEquals(bytes.takeLast(DIGEST_BYTES).toByteArray()))
            return DataInputStream(payload.inputStream()).use { input ->
                require(input.readInt() == VERSION)
                val header = KscopeJson.decodeFromString<GenerationHeader>(input.readUTF())
                require(valid(header, input.available()))
                val chunks = List(header.chunkCount) { readEntry(input, header.dimensions) }
                require(input.available() == 0 && chunks.map { it.chunk.key }.distinct().size == chunks.size)
                GenerationFile(header, chunks)
            }
        }

        private fun valid(header: GenerationHeader, remaining: Int): Boolean = header.dimensions in 1..MAX_DIMENSIONS &&
            header.identity.dimensions == header.dimensions && header.chunkCount >= 0 &&
            header.chunkCount <= remaining / header.dimensions

        private fun readEntry(input: DataInputStream, dimensions: Int): ScoredChunk {
            val key = input.readUTF()
            require(key.matches(Regex("[0-9a-f]{64}")))
            val kind = ChunkKind.valueOf(input.readUTF())
            val artifact = KscopeJson.decodeFromString<ArtifactRef>(input.readUTF())
            val nodes = KscopeJson.decodeFromString<List<NodeRef>>(input.readUTF())
            val scale = input.readFloat()
            val vector = Quantized(ByteArray(dimensions).also(input::readFully), scale)
            return ScoredChunk(Chunk(key, kind, "", nodes, artifact), vector)
        }
    }
}
