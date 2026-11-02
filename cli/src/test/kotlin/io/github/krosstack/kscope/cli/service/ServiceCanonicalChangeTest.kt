package io.github.krosstack.kscope.cli.service

import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.service.home.ServiceFile
import io.github.krosstack.kscope.cli.service.home.ServiceFileReading
import io.github.krosstack.kscope.cli.service.home.WorkspacePaths
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class ServiceCanonicalChangeTest {
    private fun file(home: Path) = ServiceFile(home.resolve("service.toml"))

    private fun kept(home: Path): List<String> = (file(home).read() as ServiceFileReading.Read).workspaces

    private fun withDots(root: Path): String {
        val real = WorkspacePaths.real(root)
        return WorkspacePaths.key(Files.createDirectories(real.resolve("sub")).resolve(".."))
    }

    private fun withLink(parent: Path, target: Path): String? =
        createDirectoryLink(parent.resolve("link"), WorkspacePaths.real(target))?.let(WorkspacePaths::key)

    @Test
    fun `add does not write a second entry for a directory that the file spells with dots`() = inWorkspace { root ->
        inServiceHome { home ->
            val spelled = withDots(root)
            file(home).write(listOf(spelled))

            val result = runService(home, "add", "--workspace", root.toString())

            assertEquals(0, result.statusCode, result.output)
            assertEquals("${WorkspacePaths.real(root)} is kept already.", result.stdoutLines().first())
            assertEquals(listOf(spelled), kept(home))
        }
    }

    @Test
    fun `remove takes out an entry that spells the directory with dots as it is written`() = inWorkspace { root ->
        inServiceHome { home ->
            val spelled = withDots(root)
            val other = WorkspacePaths.key(home.resolve("other").toAbsolutePath())
            file(home).write(listOf(spelled, other))

            val result = runService(home, "remove", "--workspace", root.toString())

            assertEquals(0, result.statusCode, result.output)
            assertEquals(listOf("Removed ${WorkspacePaths.real(root)}."), result.stdoutLines())
            assertEquals(listOf(other), kept(home))
        }
    }

    @Test
    fun `remove takes out every spelling of the directory`() = inWorkspace { root ->
        inServiceHome { home ->
            val real = WorkspacePaths.key(WorkspacePaths.real(root))
            file(home).write(listOf(real, withDots(root)))

            runService(home, "remove", "--workspace", root.toString())

            assertEquals(emptyList(), kept(home))
        }
    }

    @Test
    fun `add and remove find a directory through a symbolic link`() = inWorkspace { parent ->
        inWorkspace { root ->
            inServiceHome { home ->
                val link = withLink(parent, root)
                assumeTrue(link != null, "symbolic links cannot be created here")
                file(home).write(listOf(checkNotNull(link)))

                val added = runService(home, "add", "--workspace", root.toString())
                val removed = runService(home, "remove", "--workspace", root.toString())

                assertEquals("${WorkspacePaths.real(root)} is kept already.", added.stdoutLines().first())
                assertEquals(listOf("Removed ${WorkspacePaths.real(root)}."), removed.stdoutLines())
                assertEquals(emptyList(), kept(home))
            }
        }
    }

    @Test
    fun `a directory given through a symbolic link is the kept directory`() = inWorkspace { parent ->
        inWorkspace { root ->
            inServiceHome { home ->
                val link = withLink(parent, root)
                assumeTrue(link != null, "symbolic links cannot be created here")
                runService(home, "add", "--workspace", root.toString())

                val again = runService(home, "add", "--workspace", parent.resolve("link").toString())

                assertEquals("${WorkspacePaths.real(root)} is kept already.", again.stdoutLines().first())
                assertEquals(listOf(WorkspacePaths.key(WorkspacePaths.real(root))), kept(home))
            }
        }
    }
}
