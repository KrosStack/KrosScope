package io.github.krosstack.kscope.engine.discovery

import io.github.krosstack.kscope.engine.RegisteredRepository
import io.github.krosstack.kscope.engine.ScanEngine
import io.github.krosstack.kscope.engine.TestWorkspace
import io.github.krosstack.kscope.engine.WorkspaceConfig
import io.github.krosstack.kscope.engine.coreRegistry
import io.github.krosstack.kscope.engine.structure.TestGit
import io.github.krosstack.kscope.engine.workspaceState
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class RegisteredNameTakenTest {
    @BeforeTest
    fun requireGit() = assumeTrue(TestGit.available())

    @Test
    fun `a registered name taken by an empty directory when the observer started joins the stamp once it is removed`() =
        TestWorkspace().use { workspace ->
            repositoryAt(workspace.root)
            val taken = Files.createDirectories(workspace.root.resolve("library"))
            val library = repositoryAt(workspace.root.resolveSibling("library"))
            val registered = listOf(RegisteredRepository("library", library))
            val config = WorkspaceConfig(workspace.root, workspace.indexDirectory, registered = { registered })
            ScanEngine(coreRegistry(), config).use { engine ->
                val state = engine.workspaceState()
                val before = state.stamp()
                assertEquals(1, state.gitDirectories.size)

                Files.delete(taken)

                assertNotEquals(before, state.stamp())
                assertEquals(2, state.gitDirectories.size)
            }
        }

    @Test
    fun `a registered name taken by a file when the observer started joins the stamp once it is removed`() =
        TestWorkspace().use { workspace ->
            repositoryAt(workspace.root)
            val taken = Files.writeString(workspace.root.resolve("library"), "not a directory")
            val library = repositoryAt(workspace.root.resolveSibling("library"))
            val registered = listOf(RegisteredRepository("library", library))
            val config = WorkspaceConfig(workspace.root, workspace.indexDirectory, registered = { registered })
            ScanEngine(coreRegistry(), config).use { engine ->
                val state = engine.workspaceState()
                val before = state.stamp()

                Files.delete(taken)

                assertNotEquals(before, state.stamp())
                assertEquals(2, state.gitDirectories.size)
            }
        }
}
