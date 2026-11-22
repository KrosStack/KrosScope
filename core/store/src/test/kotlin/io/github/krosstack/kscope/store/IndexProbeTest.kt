package io.github.krosstack.kscope.store

import io.github.krosstack.kscope.model.Samples
import io.github.krosstack.kscope.store.sqlite.Schema
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IndexProbeTest {
    @Test
    fun `a file that is not there is not an index and is not created`() = TempDirectory().use { directory ->
        val file = directory.path.resolve("kscope").resolve("index.db")

        assertFalse(IndexProbe.hasArtifacts(file))
        assertTrue(Files.notExists(file.parent))
    }

    @Test
    fun `an index with files is found while it is open and after it is closed`() = TempDirectory().use { directory ->
        val file = directory.path.resolve("index.db")
        openedStore(file).use { store ->
            assertFalse(IndexProbe.hasArtifacts(file), "an index without files")
            store.replace(Samples.batch)
            assertTrue(IndexProbe.hasArtifacts(file), "an open index")
        }

        assertTrue(IndexProbe.hasArtifacts(file), "a closed index")
    }

    @Test
    fun `a database of another schema version is neither an index nor touched`() = TempDirectory().use { directory ->
        val file = directory.path.resolve("index.db")
        openedStore(file).use { it.replace(Samples.batch) }
        java.sql.DriverManager.getConnection("jdbc:sqlite:$file").use { connection ->
            connection.createStatement().use { it.execute("PRAGMA user_version = ${Schema.VERSION + 1}") }
        }
        val before = Files.readAllBytes(file).contentHashCode()

        assertFalse(IndexProbe.hasArtifacts(file))
        assertEquals(before, Files.readAllBytes(file).contentHashCode())
    }

    @Test
    fun `a file that is not a database is not an index`() = TempDirectory().use { directory ->
        val file = directory.path.resolve("index.db")
        Files.writeString(file, "not a database")

        assertFalse(IndexProbe.hasArtifacts(file))
    }

    @Test
    fun `a missing file, an empty file and an index without files are no index`() = TempDirectory().use { directory ->
        val missing = directory.path.resolve("missing.db")
        val empty = directory.path.resolve("empty.db").also { Files.write(it, ByteArray(0)) }
        val withoutFiles = directory.path.resolve("index.db")
        openedStore(withoutFiles).close()

        assertEquals(IndexState.Absent, IndexProbe.inspect(missing))
        assertEquals(IndexState.Absent, IndexProbe.inspect(empty))
        assertEquals(IndexState.Absent, IndexProbe.inspect(withoutFiles))
    }

    @Test
    fun `an index of another schema version says which version it has and is left as it is`() =
        TempDirectory().use { directory ->
            val file = directory.path.resolve("index.db")
            openedStore(file).use { it.replace(Samples.batch) }
            java.sql.DriverManager.getConnection("jdbc:sqlite:$file").use { connection ->
                connection.createStatement().use { it.execute("PRAGMA user_version = ${Schema.VERSION - 1}") }
            }
            val before = Files.readAllBytes(file).contentHashCode()

            assertEquals(IndexState.OtherFormat(Schema.VERSION - 1, Schema.VERSION), IndexProbe.inspect(file))
            assertEquals(before, Files.readAllBytes(file).contentHashCode())
        }

    @Test
    fun `a file that is not a database is damaged, and an index with files is readable`() =
        TempDirectory().use { directory ->
            val broken = directory.path.resolve("broken.db").also { Files.writeString(it, "not a database") }
            val file = directory.path.resolve("index.db")
            openedStore(file).use { it.replace(Samples.batch) }

            assertIs<IndexState.Damaged>(IndexProbe.inspect(broken))
            assertEquals(IndexState.Readable, IndexProbe.inspect(file))
        }
}
