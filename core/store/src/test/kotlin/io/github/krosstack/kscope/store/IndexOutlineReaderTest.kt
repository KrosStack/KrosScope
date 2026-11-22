package io.github.krosstack.kscope.store

import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.Samples
import io.github.krosstack.kscope.store.sqlite.Schema
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IndexOutlineReaderTest {
    private val levelBatch = FactBatch(
        ProducerRef("kscope.level", "1"),
        OwnershipUnit(OwnershipKind.WORKSPACE, "workspace"),
        Samples.fingerprint,
        emptyList(),
    )

    private fun found(file: Path): IndexOutline = assertIs<IndexRead.Found>(IndexOutlineReader.read(file)).outline

    @Test
    fun `it reads the repositories once each and the kinds of units that producers own`() =
        TempDirectory().use { directory ->
            val file = directory.path.resolve("index.db")
            openedStore(file).use {
                it.replace(Samples.batch)
                it.replace(levelBatch)
            }

            val outline = found(file)

            assertEquals(listOf(Samples.repository), outline.repositories)
            assertEquals(
                setOf(
                    UnitKind(Samples.producer, OwnershipKind.ARTIFACT),
                    UnitKind(levelBatch.producer, OwnershipKind.WORKSPACE),
                ),
                outline.units,
            )
        }

    @Test
    fun `an index that a store has open for writing is read`() = TempDirectory().use { directory ->
        val file = directory.path.resolve("index.db")
        openedStore(file).use { store ->
            store.replace(Samples.batch)

            assertEquals(listOf(Samples.repository), found(file).repositories)
        }
    }

    @Test
    fun `no file is no index and nothing is created`() = TempDirectory().use { directory ->
        val file = directory.path.resolve("kscope").resolve("index.db")

        assertEquals(IndexRead.Unusable(IndexState.Absent), IndexOutlineReader.read(file))
        assertTrue(Files.notExists(file.parent))
    }

    @Test
    fun `an index of another version is neither read nor changed, and neither is a current one`() =
        TempDirectory().use { directory ->
            val older = directory.path.resolve("older.db")
            val current = directory.path.resolve("current.db")
            listOf(older, current).forEach { file -> openedStore(file).use { it.replace(Samples.batch) } }
            DriverManager.getConnection("jdbc:sqlite:$older").use { connection ->
                connection.createStatement().use { it.execute("PRAGMA user_version = ${Schema.VERSION - 1}") }
            }
            val bytesBefore = listOf(older, current).map { Files.readAllBytes(it).toList() }

            val result = IndexOutlineReader.read(older)
            found(current)

            assertEquals(IndexRead.Unusable(IndexState.OtherFormat(Schema.VERSION - 1, Schema.VERSION)), result)
            assertEquals(bytesBefore, listOf(older, current).map { Files.readAllBytes(it).toList() })
        }

    @Test
    fun `a file that is not a database is damaged`() = TempDirectory().use { directory ->
        val file = directory.path.resolve("index.db").also { Files.writeString(it, "not a database") }

        assertIs<IndexState.Damaged>(assertIs<IndexRead.Unusable>(IndexOutlineReader.read(file)).state)
    }
}
