package io.github.krosstack.kscope.analyzer.schema.generator.specs

import io.github.krosstack.kscope.analyzer.schema.ModuleSpec
import io.github.krosstack.kscope.analyzer.schema.generator.GeneratorWorkspace
import io.github.krosstack.kscope.analyzer.schema.generator.RepositorySpec
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.spi.WorkspaceFiles
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val files = mapOf("build.gradle.kts" to "", "deep/er/x/y.txt" to "")

private fun workspace() = GeneratorWorkspace(
    listOf(
        RepositorySpec("one", files, listOf(ModuleSpec(":"))),
        RepositorySpec("two", mapOf("a.txt" to ""), listOf(ModuleSpec(":")), location = "far/away/two"),
    ),
)

class CheckoutRootsTest {
    @Test
    fun `the directory of a repository is the location of one of its files without the path of the file`() =
        workspace().use {
            val roots = CheckoutRoots(it.files, it.artifacts)

            assertEquals(it.roots.getValue("one"), roots.of(RepositoryId("one")))
            assertEquals(it.roots.getValue("two"), roots.of(RepositoryId("two")))
        }

    @Test
    fun `a repository without artifacts has no directory`() = workspace().use {
        val roots = CheckoutRoots(it.files, it.artifacts.filter { artifact -> artifact.ref.repository.value == "one" })

        assertNull(roots.of(RepositoryId("two")))
    }

    @Test
    fun `a location that is relative to the working directory gives the empty path as the root`() {
        val relative = object : WorkspaceFiles {
            override fun read(ref: ArtifactRef): ByteArray = ByteArray(0)

            override fun absolutePath(ref: ArtifactRef): Path = Path.of(ref.path.value)
        }
        val artifacts = workspace().use { it.artifacts.filter { artifact -> artifact.ref.repository.value == "one" } }

        assertEquals(Path.of(""), CheckoutRoots(relative, artifacts).of(RepositoryId("one")))
    }

    @Test
    fun `a location is found in the checkout that holds it, with its path below the checkout`() = workspace().use {
        val roots = CheckoutRoots(it.files, it.artifacts)

        assertEquals(
            CheckoutPath(RepositoryId("one"), "deep/er/x/y.txt"),
            roots.locate(it.roots.getValue("one").resolve("deep/er/x/y.txt")),
        )
        assertEquals(
            CheckoutPath(RepositoryId("two"), "a.txt"),
            roots.locate(it.roots.getValue("two").resolve("a.txt")),
        )
    }

    @Test
    fun `the directory of a checkout is in it with the empty path`() = workspace().use {
        val roots = CheckoutRoots(it.files, it.artifacts)

        assertEquals(CheckoutPath(RepositoryId("two"), ""), roots.locate(it.roots.getValue("two")))
    }

    @Test
    fun `a location beside, above or outside the checkouts is in none`() = workspace().use {
        val roots = CheckoutRoots(it.files, it.artifacts)
        val one = it.roots.getValue("one")

        assertNull(roots.locate(one.resolveSibling("one-more").resolve("a.txt")))
        assertNull(roots.locate(one.parent))
        assertNull(roots.locate(one.resolve("..").resolve("..").normalize()))
    }

    @Test
    fun `the innermost checkout holds a location when checkouts nest`() = GeneratorWorkspace(
        listOf(
            RepositorySpec("outer", mapOf("a.txt" to "")),
            RepositorySpec("inner", mapOf("x/b.txt" to ""), location = "outer/inner"),
        ),
    ).use {
        val roots = CheckoutRoots(it.files, it.artifacts)

        assertEquals(
            CheckoutPath(RepositoryId("inner"), "x/b.txt"),
            roots.locate(it.roots.getValue("outer").resolve("inner/x/b.txt")),
        )
        assertEquals(
            CheckoutPath(RepositoryId("outer"), "other/c.txt"),
            roots.locate(it.roots.getValue("outer").resolve("other/c.txt")),
        )
    }
}
