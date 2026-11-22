package io.github.krosstack.kscope.service.lookup

import io.github.krosstack.kscope.service.TemporaryTree
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IndexLookupTest {
    private val base: Path = Files.createTempDirectory("kscope-lookup").toRealPath()
    private val lookup = IndexLookup(base)

    @AfterTest
    fun cleanUp() {
        TemporaryTree.delete(base)
    }

    @Test
    fun `a folder with an index is its own nearest index`() {
        val workspace = IndexedFolders.scanned(base.resolve("workspace"))

        assertEquals(workspace, lookup.nearest(workspace))
    }

    @Test
    fun `the nearest of two parents with an index is found`() {
        val outer = IndexedFolders.scanned(base.resolve("outer"))
        val inner = IndexedFolders.scanned(outer.resolve("inner"))
        val deep = Files.createDirectories(inner.resolve("a/b"))
        val beside = Files.createDirectories(outer.resolve("beside/a"))

        assertEquals(inner, lookup.nearest(deep))
        assertEquals(outer, lookup.nearest(beside))
    }

    @Test
    fun `an index without files or of another version is passed over`() {
        val outer = IndexedFolders.scanned(base.resolve("outer"))
        val empty = IndexedFolders.withoutFiles(outer.resolve("empty"))
        val old = IndexedFolders.ofAnotherVersion(outer.resolve("old"))

        assertEquals(outer, lookup.nearest(empty))
        assertEquals(outer, lookup.nearest(Files.createDirectories(old.resolve("src"))))
    }

    @Test
    fun `no folder gives no index and nothing above the stop folder is looked at`() {
        val outer = IndexedFolders.scanned(base.resolve("outer"))
        val stop = Files.createDirectories(outer.resolve("stop"))
        val folder = Files.createDirectories(stop.resolve("a/b"))

        assertNull(IndexLookup(stop).nearest(folder))
        assertNull(lookup.nearest(Files.createDirectories(base.resolve("plain/a"))))
        assertEquals(outer, lookup.nearest(folder))
    }

    @Test
    fun `a folder that the caller knows to be ready is taken before its index is looked at`() {
        val outer = IndexedFolders.scanned(base.resolve("outer"))
        val served = Files.createDirectories(outer.resolve("served"))
        val folder = Files.createDirectories(served.resolve("a"))
        val knowing = IndexLookup(base) { it == served }

        assertEquals(served, knowing.nearest(folder))
        assertEquals(outer, knowing.nearest(outer.resolve("beside")))
        assertEquals(emptyList(), knowing.below(outer))
        val near = IndexedFolders.scanned(served.resolve("near"))
        assertEquals(near, knowing.nearest(near.resolve("a")))
        assertEquals(folder, IndexLookup(base) { it == served || it == folder }.nearest(folder.resolve("b")))
    }

    @Test
    fun `indexes one and two levels below are listed in path order`() {
        val folder = Files.createDirectories(base.resolve("folder"))
        val second = IndexedFolders.scanned(folder.resolve("b/second"))
        val first = IndexedFolders.scanned(folder.resolve("a"))
        IndexedFolders.scanned(folder.resolve("c/d/third"))

        assertEquals(listOf(first, second), lookup.below(folder))
    }

    @Test
    fun `hidden folders build output and unusable indexes are not listed below`() {
        val folder = Files.createDirectories(base.resolve("folder"))
        listOf(".hidden", "build", "out", "node_modules").forEach { IndexedFolders.scanned(folder.resolve(it)) }
        IndexedFolders.scanned(folder.resolve("app/build"))
        IndexedFolders.withoutFiles(folder.resolve("empty"))

        assertEquals(emptyList(), lookup.below(folder))
        assertEquals(emptyList(), lookup.below(base.resolve("missing")))
    }
}
