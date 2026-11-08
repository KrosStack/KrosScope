package io.github.krosstack.kscope.benchmark.realrepos.corpus

import io.github.krosstack.kscope.benchmark.realrepos.measurement.CorpusRepository
import java.io.IOException
import java.nio.file.Files
import java.util.HexFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CorpusDecodingTest {
    @Test
    fun `a non UTF8 commit message is excluded and counted without losing valid tasks`() {
        CorpusRepository("sample").use { repo ->
            repo.commit("Initial", mapOf("Source.kt" to "val value = 0\n"))
            val good = repo.commit("Correct calculation", mapOf("Source.kt" to "val value = 1\n"))
            val head = repo.commit("Malformed", mapOf("Source.kt" to "val value = 2\n"))
            val raw = repo.git(listOf("cat-file", "commit", head)).replace("Malformed", "\u00b0")
            val malformed = writeObject(repo, "commit", raw.toByteArray(Charsets.ISO_8859_1))

            val result = RealRepositoryCorpus.prepare(listOf(repo.spec().copy(head = malformed)))

            assertEquals(listOf(good), result.tasks.map { it.commit })
            assertEquals(1, result.exclusions.getValue("sample")["undecodable-commit"])
        }
    }

    @Test
    fun `a non UTF8 changed path excludes only its commit`() {
        CorpusRepository("sample").use { repo ->
            repo.commit("Initial", mapOf("Source.kt" to "val value = 0\n"))
            val good = repo.commit("Correct calculation", mapOf("Source.kt" to "val value = 1\n"))
            val head = repo.commit("Change path", mapOf("Source.kt" to "val value = 2\n"))
            val blob = repo.git(listOf("rev-parse", "$head:Source.kt")).trim()
            val entry = "100644 ".toByteArray() + byteArrayOf(0xb0.toByte()) + ".kt\u0000".toByteArray() +
                HexFormat.of().parseHex(blob)
            val tree = writeObject(repo, "tree", entry)
            val raw = repo.git(listOf("cat-file", "commit", head)).replaceFirst(Regex("tree [a-f0-9]+"), "tree $tree")
            val malformed = writeObject(repo, "commit", raw.toByteArray(Charsets.UTF_8))

            val result = RealRepositoryCorpus.prepare(listOf(repo.spec().copy(head = malformed)))

            assertEquals(listOf(good), result.tasks.map { it.commit })
            assertEquals(1, result.exclusions.getValue("sample")["undecodable-commit"])
        }
    }

    @Test
    fun `an aborting Git read names the repository and pinned commit`() {
        CorpusRepository("broken").use { repo ->
            repo.commit("Initial", mapOf("Source.kt" to "val value = 0\n"))
            val missing = "f".repeat(40)

            val failure = assertFailsWith<IOException> {
                RealRepositoryCorpus.prepare(listOf(repo.spec().copy(head = missing)))
            }

            assertTrue(failure.message.orEmpty().contains("broken"), failure.message)
            assertTrue(failure.message.orEmpty().contains(missing), failure.message)
        }
    }

    @Test
    fun `undecodable changed source bytes retain their parent gold`() {
        CorpusRepository("sample").use { repo ->
            repo.commit("Initial", mapOf("Source.java" to "class Source {}\n"))
            repo.commit("Preserve original text", mapOf("Source.java" to "class Original {}\n"))
            Files.write(repo.root.resolve("Source.java"), byteArrayOf(0xb0.toByte(), 0xb2.toByte()))
            repo.git(listOf("add", "Source.java"))
            repo.git(
                listOf("commit", "-q", "--amend", "--no-edit"),
                mapOf("GIT_COMMITTER_DATE" to "2024-01-01T00:02:00Z"),
            )
            val change = repo.commit("Correct encoding", mapOf("Source.java" to "class Source {}\n"))

            val result = RealRepositoryCorpus.prepare(listOf(repo.spec()))

            assertEquals(2, result.tasks.size)
            assertEquals(listOf("Source.java"), result.tasks.single { it.commit == change }.goldFiles)
        }
    }

    @Test
    fun `an aborting source read names the repository revision and path`() {
        CorpusRepository("broken").use { repo ->
            val parent = repo.commit("Initial", mapOf("Source.kt" to "val value = 0\n"))
            repo.commit("Correct calculation", mapOf("Source.kt" to "val value = 1\n"))
            val blob = repo.git(listOf("rev-parse", "$parent:Source.kt")).trim()
            val objectFile = repo.root.resolve(".git/objects/${blob.take(2)}/${blob.drop(2)}").toFile()
            assertTrue(objectFile.setWritable(true))
            Files.delete(objectFile.toPath())

            val failure = assertFailsWith<IOException> { RealRepositoryCorpus.prepare(listOf(repo.spec())) }

            listOf("broken", parent, "Source.kt").forEach { context ->
                assertTrue(failure.message.orEmpty().contains(context), failure.message)
            }
        }
    }

    private fun writeObject(repo: CorpusRepository, type: String, bytes: ByteArray): String {
        val input = repo.root.resolve(".git/$type-input")
        Files.write(input, bytes)
        return repo.git(listOf("hash-object", "-t", type, "-w", input.toString())).trim()
    }
}
