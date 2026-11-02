package io.github.krosstack.kscope.engine.discovery

import io.github.krosstack.kscope.engine.RegisteredRepository
import io.github.krosstack.kscope.engine.ScanEngine
import io.github.krosstack.kscope.engine.TestWorkspace
import io.github.krosstack.kscope.engine.WorkspaceConfig
import io.github.krosstack.kscope.engine.coreRegistry
import io.github.krosstack.kscope.engine.structure.TestGit
import io.github.krosstack.kscope.model.RepositoryId
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Path
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PlainRootRegistrationTest {
    @BeforeTest
    fun requireGit() = assumeTrue(TestGit.available())

    @Test
    fun `a plain root with a source file keeps its files when a repository is registered`() =
        TestWorkspace().use { workspace ->
            workspace.write("App.kt", "class App\n")
            val library = repositoryAt(workspace.root.resolveSibling("library"))
            val registered = listOf(RegisteredRepository("library", library))
            val config = WorkspaceConfig(workspace.root, workspace.indexDirectory, registered = { registered })
            ScanEngine(coreRegistry(), config).use { engine ->
                engine.update()
                assertEquals(listOf("", "library"), directoriesIndexed(engine))
                assertEquals(setOf("App.kt", "README.md"), pathsIndexed(engine))
            }
        }

    @Test
    fun `a plain root with a source file stays indexed when the registered checkout is missing`() =
        TestWorkspace().use { workspace ->
            workspace.write("App.kt", "class App\n")
            val library = repositoryAt(workspace.root.resolveSibling("library"))
            val registered = listOf(RegisteredRepository("library", library))
            val config = WorkspaceConfig(workspace.root, workspace.indexDirectory, registered = { registered })
            ScanEngine(coreRegistry(), config).use { engine ->
                engine.update()
                library.toFile().deleteRecursively()
                engine.update()
                assertEquals(listOf(""), directoriesIndexed(engine))
                assertEquals(setOf("App.kt"), pathsIndexed(engine))
            }
        }

    @Test
    fun `a scan hands over the checkouts of the registered repositories that it indexed`() =
        TestWorkspace().use { workspace ->
            workspace.write("App.kt", "class App\n")
            val library = repositoryAt(workspace.root.resolveSibling("library"))
            val registered = listOf(RegisteredRepository("library", library))
            val accepted = mutableListOf<Map<RepositoryId, Path>>()
            val config = WorkspaceConfig(
                workspace.root,
                workspace.indexDirectory,
                registered = { registered },
                checkoutsAccepted = { accepted += it },
            )
            ScanEngine(coreRegistry(), config).use { engine ->
                engine.update()
                library.toFile().deleteRecursively()
                engine.update()
            }
            assertEquals(listOf(listOf(library), emptyList()), accepted.map { it.values.toList() })
        }

    private fun directoriesIndexed(engine: ScanEngine) =
        engine.graph().structure().repositories.map { it.directory.value }.sorted()

    private fun pathsIndexed(engine: ScanEngine) = engine.graph().artifacts().map { it.ref.path.value }.toSet()
}
