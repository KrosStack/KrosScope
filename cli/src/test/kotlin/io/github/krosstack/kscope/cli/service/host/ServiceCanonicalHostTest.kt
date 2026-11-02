package io.github.krosstack.kscope.cli.service.host

import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.serve.ServeMetadata
import io.github.krosstack.kscope.cli.service.createDirectoryLink
import io.github.krosstack.kscope.cli.service.home.ServiceFile
import io.github.krosstack.kscope.cli.service.home.WorkspacePaths
import io.github.krosstack.kscope.cli.service.inServiceHome
import io.github.krosstack.kscope.cli.service.runService
import io.github.krosstack.kscope.cli.service.workspaceLines
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ServiceCanonicalHostTest {
    private fun withDots(root: Path): Path =
        Files.createDirectories(WorkspacePaths.real(root).resolve("sub")).resolve("..")

    private fun linkTo(parent: Path, target: Path): Path? =
        createDirectoryLink(parent.resolve("link"), WorkspacePaths.real(target))

    private fun workspaceLines(home: Path): List<String> = runService(home, "status").workspaceLines()

    @Test
    fun `two spellings of one directory in the file are served once`() = inWorkspace { root ->
        inServiceHome { home ->
            withTestService(home, listOf(root, withDots(root))) { service ->
                service.awaitServed(root)

                val lines = workspaceLines(home)

                assertEquals(1, lines.size, lines.toString())
                assertTrue(lines.single().startsWith("${WorkspacePaths.real(root)}: served. "), lines.single())
            }
        }
    }

    @Test
    fun `a reload that adds a second spelling keeps the server it has`() = inWorkspace { root ->
        inServiceHome { home ->
            withTestService(home, listOf(root)) { service ->
                val server = service.awaitServed(root)
                val before = assertNotNull(ServeMetadata.read(root))
                val key = WorkspacePaths.key(root)
                ServiceFile(home.resolve("service.toml")).write(listOf(key, WorkspacePaths.key(withDots(root))))

                val answer = service.control("reload")

                assertEquals(0, answer.exitCode, answer.stderr)
                assertEquals(1, workspaceLines(home).size)
                assertEquals(before, ServeMetadata.read(root))
                assertEquals(server.metadata, before)
            }
        }
    }

    @Test
    fun `a directory and a symbolic link to it in the file are served once`() = inWorkspace { parent ->
        inWorkspace { root ->
            inServiceHome { home ->
                val link = linkTo(parent, root)
                assumeTrue(link != null, "symbolic links cannot be created here")
                withTestService(home, listOf(root, checkNotNull(link))) { service ->
                    service.awaitServed(root)

                    val lines = workspaceLines(home)

                    assertEquals(1, lines.size, lines.toString())
                    assertTrue(lines.single().startsWith("${WorkspacePaths.real(root)}: served. "), lines.single())
                }
            }
        }
    }
}
