package io.github.krosstack.kscope.service

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.RepoPath
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WorkspacePathsTest {
    private fun repository(root: Path, name: String, files: Map<String, ByteArray>) {
        val directory = root.resolve(name)
        files.forEach { (path, bytes) ->
            Files.createDirectories(directory.resolve(path).parent)
            Files.write(directory.resolve(path), bytes)
        }
        TestGit.run(directory, listOf("init", "-q"))
        TestGit.run(directory, listOf("add", "-A"))
        TestGit.run(directory, listOf("commit", "-q", "-m", "initial"))
    }

    private fun <T> inTwoRepositories(block: (KrosScope) -> T): T {
        assumeTrue(TestGit.available())
        val root = Files.createTempDirectory("kscope-paths")
        try {
            val build = "include(\":lib\")\n".toByteArray()
            repository(
                root,
                "first",
                mapOf(
                    "settings.gradle.kts" to build,
                    "lib/src/main/kotlin/First.kt" to "class First\n".toByteArray(),
                    "lib/src/main/kotlin/Shared.kt" to "class Shared\n".toByteArray(),
                    "lib/src/main/kotlin/other/Shared.kt" to "package other\n\nclass Shared\n".toByteArray(),
                ),
            )
            repository(
                root,
                "second",
                mapOf(
                    "settings.gradle.kts" to build,
                    "lib/src/main/kotlin/Second.kt" to "class Second\n".toByteArray(),
                    "lib/src/main/kotlin/Shared.kt" to "class Shared\n".toByteArray(),
                    "lib/image.png" to byteArrayOf(1, 0, 2),
                ),
            )
            return KrosScope.open(root).use { service ->
                service.scan()
                block(service)
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `info lists the repositories in directory order with their module counts`() = inTwoRepositories { service ->
        val repositories = service.info().repositories
        assertEquals(listOf("first", "second"), repositories.map { it.directory })
        assertEquals(listOf(1, 1), repositories.map { it.modules })
    }

    @Test
    fun `a location is resolved by its workspace path`() = inTwoRepositories { service ->
        val query = "first/lib/src/main/kotlin/First.kt:1"
        val candidates = service.resolveNode(query).candidates
        assertEquals(1, candidates.size)
        assertNotEquals(query, candidates.single())
        val locations = service.workspaceLocations()
        val names = service.graph().artifacts().map { locations.pathOf(it.ref) }
        assertTrue("first/lib/src/main/kotlin/First.kt" in names)
    }

    @Test
    fun `a path without the directory of its repository is rejected with the workspace paths`() =
        inTwoRepositories { service ->
            val failure = assertFailsWith<WorkspacePathException> { service.resolveNode("settings.gradle.kts") }
            val names = "first/settings.gradle.kts, second/settings.gradle.kts"
            assertTrue(names in failure.message.orEmpty(), failure.message)
        }

    @Test
    fun `the one declaration of a shared name in the repository it is asked from is chosen by name`() =
        inTwoRepositories { service ->
            val everywhere = service.resolveNode("Shared")
            val asked = service.resolveNode("Shared", "second/lib")
            assertEquals(3, everywhere.candidates.size)
            assertEquals(null, everywhere.chosenIn)
            assertEquals("second", asked.chosenIn)
            val declared = service.declarationsNamed(asked.candidates.single()).single().declaration?.artifact
            assertEquals("second/lib/src/main/kotlin/Shared.kt", declared?.let(service.workspaceLocations()::pathOf))
        }

    @Test
    fun `two declarations inside or none or a folder outside every repository keep every candidate`() =
        inTwoRepositories { service ->
            val everywhere = service.resolveNode("Shared")
            assertEquals(everywhere, service.resolveNode("Shared", "first/lib"))
            assertEquals(everywhere, service.resolveNode("Shared", "notes"))
            assertEquals(everywhere, service.resolveNode("Shared", "sec"))
            assertEquals(service.resolveNode("First"), service.resolveNode("First", "second"))
        }

    @Test
    fun `the kind of a file is read under the directory of its repository`() = inTwoRepositories { service ->
        val locations = service.workspaceLocations()
        val image = locations.artifactsNamed("second/lib/image.png").single()
        val code = locations.artifactsNamed("first/lib/src/main/kotlin/First.kt").single()
        val kinds = service.artifactKinds(listOf(image, code, ArtifactRef(code.repository, RepoPath("missing.kt"))))
        assertEquals(ArtifactKind.BINARY, kinds[image])
        assertEquals(ArtifactKind.CODE, kinds[code])
    }
}
