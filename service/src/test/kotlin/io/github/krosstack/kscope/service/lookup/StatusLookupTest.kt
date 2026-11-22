package io.github.krosstack.kscope.service.lookup

import io.github.krosstack.kscope.service.TemporaryTree
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StatusLookupTest {
    private val base: Path = Files.createTempDirectory("kscope-status-lookup").toRealPath()
    private val lookup = IndexLookup(base).forStatus()

    @AfterTest
    fun cleanUp() {
        TemporaryTree.delete(base)
    }

    @Test
    fun `an index of another version is the nearest index instead of being passed over`() {
        val outer = IndexedFolders.scanned(base.resolve("outer"))
        val old = IndexedFolders.ofAnotherVersion(outer.resolve("old"))
        val inside = Files.createDirectories(old.resolve("src"))

        assertEquals(old, lookup.nearest(inside))
        assertEquals(outer, IndexLookup(base).nearest(inside))
    }

    @Test
    fun `a file that is not a database is the nearest index too`() {
        val outer = IndexedFolders.scanned(base.resolve("outer"))
        val broken = Files.createDirectories(outer.resolve("broken/.kscope"))
        Files.writeString(broken.resolve("index.db"), "not a database")

        assertEquals(broken.parent, lookup.nearest(broken.parent))
    }

    @Test
    fun `an index without files is still passed over`() {
        val outer = IndexedFolders.scanned(base.resolve("outer"))
        val empty = IndexedFolders.withoutFiles(outer.resolve("empty"))

        assertEquals(outer, lookup.nearest(empty))
    }

    @Test
    fun `it asks nobody whether a server keeps the folder, and keeps the stop folder`() {
        val outer = IndexedFolders.scanned(base.resolve("outer"))
        val stop = Files.createDirectories(outer.resolve("stop"))
        val folder = Files.createDirectories(stop.resolve("a"))
        val asking = IndexLookup(stop, ready = { error("the server was asked about $it") })

        assertNull(asking.forStatus().nearest(folder))
        assertEquals(outer, IndexLookup(base).forStatus().nearest(folder))
    }
}
