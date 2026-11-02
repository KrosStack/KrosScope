package io.github.krosstack.kscope.service

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WorkspaceContentTest {
    @Test
    fun `content reads require a known artifact in the matching repository`() {
        val root = Files.createTempDirectory("kscope-content")
        try {
            val ref = ArtifactRef(RepositoryId("repo"), RepoPath("source"))
            Files.writeString(root.resolve("source"), "declaration")
            val content = WorkspaceContent(root, setOf(ref))

            assertEquals("declaration", content.read(ref).decodeToString())
            assertFailsWith<IOException> { content.read(ref.copy(repository = RepositoryId("other"))) }
            assertFailsWith<IOException> { content.read(ref.copy(path = RepoPath("unknown"))) }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `a file is read below the directory of its repository`() {
        val root = Files.createTempDirectory("kscope-content")
        try {
            val ref = ArtifactRef(RepositoryId("repo"), RepoPath("source"))
            Files.createDirectories(root.resolve("checkout"))
            Files.writeString(root.resolve("checkout/source"), "declaration")
            val content = WorkspaceContent(root, setOf(ref), mapOf(ref.repository to root.resolve("checkout")))

            assertEquals("declaration", content.read(ref).decodeToString())
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `a local repository directory that links outside the workspace is not read`() {
        val base = Files.createTempDirectory("kscope-content")
        try {
            val ref = ArtifactRef(RepositoryId("repo"), RepoPath("source"))
            val root = Files.createDirectories(base.resolve("workspace"))
            val outside = Files.createDirectories(base.resolve("outside"))
            Files.writeString(outside.resolve("source"), "secret")
            linkOrSkip(root.resolve("secrets"), outside)
            val content = WorkspaceContent(root, setOf(ref), mapOf(ref.repository to root.resolve("secrets")))

            assertFailsWith<IOException> { content.read(ref) }
        } finally {
            base.toFile().deleteRecursively()
        }
    }

    @Test
    fun `a local repository directory that links to another directory of the workspace is read`() {
        val base = Files.createTempDirectory("kscope-content")
        try {
            val ref = ArtifactRef(RepositoryId("repo"), RepoPath("source"))
            val root = Files.createDirectories(base.resolve("workspace"))
            Files.createDirectories(root.resolve("real"))
            Files.writeString(root.resolve("real/source"), "declaration")
            linkOrSkip(root.resolve("alias"), root.resolve("real"))
            val content = WorkspaceContent(root, setOf(ref), mapOf(ref.repository to root.resolve("alias")))

            assertEquals("declaration", content.read(ref).decodeToString())
        } finally {
            base.toFile().deleteRecursively()
        }
    }

    @Test
    fun `an authorized external checkout is read from its own directory`() {
        val base = Files.createTempDirectory("kscope-content")
        try {
            val ref = ArtifactRef(RepositoryId("repo"), RepoPath("source"))
            val root = Files.createDirectories(base.resolve("workspace"))
            val outside = Files.createDirectories(base.resolve("outside"))
            Files.writeString(outside.resolve("source"), "declaration")
            val content = WorkspaceContent(root, setOf(ref), external = mapOf(ref.repository to outside))

            assertEquals("declaration", content.read(ref).decodeToString())
        } finally {
            base.toFile().deleteRecursively()
        }
    }

    @Test
    fun `an external checkout does not open files that link beyond its own directory`() {
        val base = Files.createTempDirectory("kscope-content")
        try {
            val ref = ArtifactRef(RepositoryId("repo"), RepoPath("source"))
            val root = Files.createDirectories(base.resolve("workspace"))
            val outside = Files.createDirectories(base.resolve("outside"))
            Files.writeString(base.resolve("secret"), "secret")
            linkOrSkip(outside.resolve("source"), base.resolve("secret"))
            val content = WorkspaceContent(root, setOf(ref), external = mapOf(ref.repository to outside))

            assertFailsWith<IOException> { content.read(ref) }
        } finally {
            base.toFile().deleteRecursively()
        }
    }

    private fun linkOrSkip(link: Path, target: Path) {
        val created = try {
            Files.createSymbolicLink(link, target)
            true
        } catch (_: UnsupportedOperationException) {
            false
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
        assumeTrue(created, "this system does not let the test create symbolic links")
    }
}
