package io.github.krosstack.kscope.service.lookup

import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.TemporaryTree
import io.github.krosstack.kscope.service.TestGit
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class IndexCoverTest {
    private val base: Path = Files.createTempDirectory("kscope-cover").toRealPath()
    private val lookup = IndexLookup(base)

    @AfterTest
    fun cleanUp() {
        TemporaryTree.delete(base)
    }

    private fun twoRepositories(): Path {
        assumeTrue(TestGit.available())
        val root = Files.createDirectories(base.resolve("root"))
        IndexedFolders.repository(root, "first")
        IndexedFolders.repository(root, "second")
        Files.createDirectories(root.resolve("notes"))
        return IndexedFolders.scanned(root)
    }

    @Test
    fun `an index of one repository covers every folder below it`() {
        val root = IndexedFolders.scanned(base.resolve("root"))
        val folder = Files.createDirectories(root.resolve("a/b"))

        assertEquals(root, lookup.covering(folder))
    }

    @Test
    fun `a folder with its own index or without a parent index is not covered`() {
        val root = IndexedFolders.scanned(base.resolve("root"))
        val inner = IndexedFolders.scanned(root.resolve("inner"))

        assertNull(lookup.covering(inner))
        assertNull(lookup.covering(root))
        assertNull(lookup.covering(Files.createDirectories(base.resolve("plain/a"))))
    }

    @Test
    fun `an index of several repositories covers the folders of its repositories only`() {
        val root = twoRepositories()

        assertEquals(root, lookup.covering(root.resolve("first/docs")))
        assertEquals(root, lookup.covering(root.resolve("second")))
        assertNull(lookup.covering(root.resolve("notes")))
    }

    @Test
    fun `the repository that holds a folder is found by whole path segments`() {
        val root = twoRepositories()

        KrosScope.open(root).use { scope ->
            val first = assertNotNull(scope.repositoryHolding("first"))
            assertEquals(first, scope.repositoryHolding("first/docs"))
            assertNotNull(scope.repositoryHolding("second/docs"))
            assertNull(scope.repositoryHolding("firstly"))
            assertNull(scope.repositoryHolding("notes"))
        }
    }

    @Test
    fun `the one repository of an index holds every folder`() {
        val root = IndexedFolders.scanned(base.resolve("root"))

        KrosScope.open(root).use { scope ->
            assertEquals(scope.repositoryHolding("a"), scope.repositoryHolding("b/c"))
            assertNotNull(scope.repositoryHolding("a"))
        }
    }
}
