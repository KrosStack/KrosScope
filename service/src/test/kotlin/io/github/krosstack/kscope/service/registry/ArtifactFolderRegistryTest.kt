package io.github.krosstack.kscope.service.registry

import io.github.krosstack.kscope.engine.RegisteredRepository
import io.github.krosstack.kscope.service.trust.WorkspaceKey
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArtifactFolderRegistryTest {
    private fun <T> withRegistry(block: (RepositoryRegistry, Path) -> T): T {
        val home = Files.createTempDirectory("kscope-registry")
        try {
            return block(RepositoryRegistry(home.resolve(RepositoryRegistry.FILE_NAME)), home)
        } finally {
            home.toFile().deleteRecursively()
        }
    }

    @Test
    fun `artifact folders are kept per workspace in the order they were registered`() = withRegistry { registry, home ->
        val app = Files.createDirectories(home.resolve("app"))
        val other = Files.createDirectories(home.resolve("other"))
        val maven = home.resolve("m2")
        val cache = home.resolve("gradle")
        registry.changeArtifactFolders(app) { (it + listOf(maven, cache)) to Unit }

        assertEquals(listOf(maven, cache), registry.artifactFolders(app))
        assertEquals(emptyList(), registry.artifactFolders(other))
        val text = Files.readString(home.resolve(RepositoryRegistry.FILE_NAME))
        assertTrue("artifact-folders = [" in text, text)
    }

    @Test
    fun `a file written before artifact folders existed reads as before`() = withRegistry { registry, home ->
        val app = Files.createDirectories(home.resolve("app"))
        val key = WorkspaceKey.of(app)
        Files.writeString(
            home.resolve(RepositoryRegistry.FILE_NAME),
            """
            [[workspace]]
            path = "$key"
            repositories = [
              { name = "kit", path = "/src/kit" },
            ]
            """.trimIndent() + "\n",
        )

        assertEquals(listOf(RegisteredRepository("kit", Path.of("/src/kit"))), registry.registered(app))
        assertEquals(emptyList(), registry.artifactFolders(app))
    }

    @Test
    fun `a workspace with folders and no repository is kept and a file without folders has no such key`() =
        withRegistry { registry, home ->
            val app = Files.createDirectories(home.resolve("app"))
            registry.change(app) { listOf(RegisteredRepository("kit", home.resolve("kit"))) to Unit }
            assertTrue("artifact-folders" !in Files.readString(home.resolve(RepositoryRegistry.FILE_NAME)))

            registry.changeArtifactFolders(app) { listOf(home.resolve("m2")) to Unit }
            registry.change(app) { emptyList<RegisteredRepository>() to Unit }

            assertEquals(emptyList(), registry.registered(app))
            assertEquals(listOf(home.resolve("m2")), registry.artifactFolders(app))
            registry.changeArtifactFolders(app) { emptyList<Path>() to Unit }
            assertTrue("[[workspace]]" !in Files.readString(home.resolve(RepositoryRegistry.FILE_NAME)))
        }

    @Test
    fun `changing the folders keeps the repositories and the other way round`() = withRegistry { registry, home ->
        val app = Files.createDirectories(home.resolve("app"))
        val kit = RegisteredRepository("kit", home.resolve("kit"))
        registry.change(app) { listOf(kit) to Unit }
        registry.changeArtifactFolders(app) { listOf(home.resolve("m2")) to Unit }
        registry.change(app) { (it + RegisteredRepository("web", home.resolve("web"))) to Unit }

        assertEquals(listOf("kit", "web"), registry.registered(app).map { it.name })
        assertEquals(listOf(home.resolve("m2")), registry.artifactFolders(app))
    }

    @Test
    fun `a path with backslashes, as Windows writes it, is read from the file and written back unchanged`() =
        withRegistry { registry, home ->
            val app = Files.createDirectories(home.resolve("app"))
            val file = home.resolve(RepositoryRegistry.FILE_NAME)
            val key = WorkspaceKey.of(app)
            Files.writeString(
                file,
                """
                [[workspace]]
                path = "$key"
                repositories = []
                artifact-folders = ["D:\\Users\\me\\.m2\\repository", "E:/caches"]
                """.trimIndent() + "\n",
            )

            assertEquals(
                listOf("D:/Users/me/.m2/repository", "E:/caches"),
                registry.artifactFolders(app).map { it.toString().replace('\\', '/') },
            )

            val windows = Path.of("D:\\Users\\me\\.m2\\repository")
            registry.changeArtifactFolders(app) { listOf(windows) to Unit }
            assertEquals(listOf(windows), RepositoryRegistry(file).artifactFolders(app))
        }

    @Test
    fun `a folder entry that is not a string makes the file unreadable`() = withRegistry { registry, home ->
        val app = Files.createDirectories(home.resolve("app"))
        val key = WorkspaceKey.of(app)
        val text = "[[workspace]]\npath = \"$key\"\nrepositories = []\nartifact-folders = [3]\n"
        Files.writeString(home.resolve(RepositoryRegistry.FILE_NAME), text)

        assertEquals(emptyList(), registry.artifactFolders(app))
        assertNull(registry.changeArtifactFolders(app) { it to "changed" })
        assertEquals(text, Files.readString(home.resolve(RepositoryRegistry.FILE_NAME)))
    }
}
