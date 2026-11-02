package io.github.krosstack.kscope.service.registry

import io.github.krosstack.kscope.model.RepositoryId
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScannedCheckoutsTest {
    private fun <T> withRecord(block: (ScannedCheckouts, Path) -> T): T {
        val home = Files.createTempDirectory("kscope-scanned")
        try {
            return block(ScannedCheckouts(home.resolve(ScannedCheckouts.FILE_NAME)), home)
        } finally {
            home.toFile().deleteRecursively()
        }
    }

    @Test
    fun `each workspace keeps the checkouts of its own last scan`() = withRecord { record, home ->
        val app = Files.createDirectories(home.resolve("app"))
        val other = Files.createDirectories(home.resolve("other"))
        val kit = mapOf(RepositoryId("kit") to home.resolve("kit"))
        val web = mapOf(RepositoryId("web") to home.resolve("web"))
        assertTrue(record.save(app, kit))
        assertTrue(record.save(other, web))
        assertTrue(record.save(app, web))

        assertEquals(web, record.read(app))
        assertEquals(web, record.read(other))
    }

    @Test
    fun `a scan with no registered repositories empties the record of its workspace`() = withRecord { record, home ->
        val app = Files.createDirectories(home.resolve("app"))
        record.save(app, mapOf(RepositoryId("kit") to home.resolve("kit")))
        record.save(app, emptyMap())

        assertEquals(emptyMap(), record.read(app))
    }

    @Test
    fun `nothing is created for a workspace that has no registered repositories`() = withRecord { record, home ->
        assertTrue(record.save(Files.createDirectories(home.resolve("app")), emptyMap()))

        assertFalse(Files.exists(home.resolve(ScannedCheckouts.FILE_NAME)))
    }

    @Test
    fun `a file that cannot be read says nothing and is replaced by the next scan`() = withRecord { record, home ->
        val app = Files.createDirectories(home.resolve("app"))
        Files.writeString(home.resolve(ScannedCheckouts.FILE_NAME), "not json")
        assertEquals(emptyMap(), record.read(app))

        val kit = mapOf(RepositoryId("kit") to home.resolve("kit"))
        assertTrue(record.save(app, kit))
        assertEquals(kit, record.read(app))
    }
}
