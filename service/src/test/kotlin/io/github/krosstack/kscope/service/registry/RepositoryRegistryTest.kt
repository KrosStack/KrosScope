package io.github.krosstack.kscope.service.registry

import io.github.krosstack.kscope.engine.RegisteredRepository
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepositoryRegistryTest {
    private fun <T> withRegistry(block: (RepositoryRegistry, Path) -> T): T {
        val home = Files.createTempDirectory("kscope-registry")
        try {
            return block(RepositoryRegistry(home.resolve(RepositoryRegistry.FILE_NAME)), home)
        } finally {
            home.toFile().deleteRecursively()
        }
    }

    @Test
    fun `repositories are kept per workspace and read back in name order`() = withRegistry { registry, home ->
        val app = Files.createDirectories(home.resolve("app"))
        val other = Files.createDirectories(home.resolve("other"))
        val kit = RegisteredRepository("kit", home.resolve("kit"))
        val web = RegisteredRepository("web", home.resolve("web"))
        registry.change(app) { (it + web + kit) to Unit }

        assertEquals(listOf(kit, web), registry.registered(app))
        assertEquals(emptyList(), registry.registered(other))
    }

    @Test
    fun `the file names the workspace and each repository and parses back unchanged`() =
        withRegistry { registry, home ->
            val app = Files.createDirectories(home.resolve("app"))
            val quoted = RegisteredRepository("odd", home.resolve("with space"))
            registry.change(app) { listOf(quoted) to Unit }

            val text = Files.readString(home.resolve(RepositoryRegistry.FILE_NAME))
            assertTrue(text.contains("[[workspace]]") && text.contains("name = \"odd\""), text)
            assertEquals(listOf(quoted), RepositoryRegistry(home.resolve(RepositoryRegistry.FILE_NAME)).registered(app))
        }

    @Test
    fun `a file that cannot be read registers nothing and refuses a change`() = withRegistry { registry, home ->
        val app = Files.createDirectories(home.resolve("app"))
        Files.writeString(home.resolve(RepositoryRegistry.FILE_NAME), "workspace = 3\n")

        assertEquals(emptyList(), registry.registered(app))
        assertNull(registry.change(app) { it to "changed" })
        assertEquals("workspace = 3\n", Files.readString(home.resolve(RepositoryRegistry.FILE_NAME)))
    }

    @Test
    fun `a workspace whose last repository goes is left out of the file`() = withRegistry { registry, home ->
        val app = Files.createDirectories(home.resolve("app"))
        registry.change(app) { listOf(RegisteredRepository("kit", home.resolve("kit"))) to Unit }
        registry.change(app) { emptyList<RegisteredRepository>() to Unit }

        assertTrue("[[workspace]]" !in Files.readString(home.resolve(RepositoryRegistry.FILE_NAME)))
    }
}
