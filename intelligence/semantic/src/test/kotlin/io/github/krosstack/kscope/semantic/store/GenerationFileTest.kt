package io.github.krosstack.kscope.semantic.store

import io.github.krosstack.kscope.semantic.SemanticFixture
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GenerationFileTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `round trip retains metadata vectors and identical bytes`() {
        val expected = SemanticFixture().apply { file() }.generation()
        val file = directory.resolve("generation.bin")
        expected.write(file)
        val actual = assertNotNull(GenerationFile.read(file))
        assertEquals(expected.header, actual.header)
        assertEquals(expected.chunks.map { it.chunk.nodes }, actual.chunks.map { it.chunk.nodes })
        assertContentEquals(expected.chunks.single().vector.bytes, actual.chunks.single().vector.bytes)
        assertContentEquals(expected.bytes(), actual.bytes())
    }

    @Test
    fun `truncated corrupt and foreign files count as absent`() {
        val generation = SemanticFixture().apply { file() }.generation()
        val file = directory.resolve("generation.bin")
        val bytes = generation.bytes()
        Files.write(file, bytes.copyOf(bytes.size - 1))
        assertNull(GenerationFile.read(file))
        Files.write(file, bytes.clone().also { it[10] = (it[10].toInt() xor 1).toByte() })
        assertNull(GenerationFile.read(file))
        val foreign = bytes.copyOf(bytes.size - 32).also { it[3] = 2 }
        Files.write(file, foreign + java.security.MessageDigest.getInstance("SHA-256").digest(foreign))
        assertNull(GenerationFile.read(file))
    }

    @Test
    fun `valid digest with wrong header JSON shape counts as absent`() {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use {
            it.writeInt(1)
            it.writeUTF("[]")
        }
        val payload = buffer.toByteArray()
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(payload)
        val file = Files.write(directory.resolve("generation.bin"), payload + digest)
        assertNull(GenerationFile.read(file))
    }
}
