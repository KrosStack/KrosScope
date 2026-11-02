package io.github.krosstack.kscope.benchmark.realrepos.measurement

import io.github.krosstack.kscope.benchmark.realrepos.corpus.CorpusGit
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class RealSnapshotIsolationTest {
    @TempDir
    lateinit var output: Path

    @Test
    fun `clone and checkout leave the source repository alone when the environment selects it`() {
        CorpusRepository("sample").use { source ->
            val first = source.commit("First", mapOf("README.md" to "first\n"))
            val second = source.commit("Second", mapOf("README.md" to "second\n"))
            Files.writeString(source.root.resolve("README.md"), "uncommitted\n")
            val gitDirectory = source.root.resolve(".git")
            val index = Files.readAllBytes(gitDirectory.resolve("index"))
            val selection = mapOf(
                "GIT_DIR" to gitDirectory.toString(),
                "GIT_WORK_TREE" to source.root.toString(),
                "GIT_INDEX_FILE" to gitDirectory.resolve("index").toString(),
            )
            val environment = System.getenv() + selection
            val snapshot = RealSnapshot(source.spec(), RealRunStorage(output), environment = environment)

            snapshot.prepare()
            snapshot.checkout(first)

            assertEquals(first, CorpusGit(snapshot.workspace).read("rev-parse", "HEAD").trim())
            assertEquals(second, source.git(listOf("rev-parse", "HEAD")).trim())
            assertEquals("uncommitted\n", Files.readString(source.root.resolve("README.md")))
            assertContentEquals(index, Files.readAllBytes(gitDirectory.resolve("index")))
        }
    }
}
