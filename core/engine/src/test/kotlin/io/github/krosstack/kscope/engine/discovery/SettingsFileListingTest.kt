package io.github.krosstack.kscope.engine.discovery

import io.github.krosstack.kscope.engine.TestWorkspace
import io.github.krosstack.kscope.engine.WorkspaceConfig
import io.github.krosstack.kscope.engine.structure.TestGit
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsFileListingTest {
    private val settingsFile = WorkspaceConfig.SETTINGS_FILE_NAME

    private fun listed(workspace: TestWorkspace): List<String> {
        val git = GitAccess.probe(workspace.root)
        return FileLister(workspace.root, workspace.indexDirectory, git).list().paths
    }

    private fun commitAll(workspace: TestWorkspace) {
        TestGit.output(workspace.root, "init", "-q")
        TestGit.output(workspace.root, "add", "-A")
        TestGit.output(workspace.root, "commit", "-q", "-m", "initial")
    }

    @Test
    fun `the settings file of the root is named kscope dot toml`() {
        assertEquals("kscope.toml", settingsFile)
    }

    @Test
    fun `outside git the settings file of the root is not listed and the same name below the root is`() =
        TestWorkspace("plain").use { workspace ->
            workspace.write(settingsFile, "format = 1\n")
            workspace.write("tools/$settingsFile", "format = 1\n")

            val paths = listed(workspace)

            assertFalse(settingsFile in paths, "the settings file of the root was listed: $paths")
            assertTrue("tools/$settingsFile" in paths, "a file of that name elsewhere is an ordinary file: $paths")
        }

    @Test
    fun `in a git work tree the settings file of the root is not listed whether it is tracked or not`() {
        assumeTrue(TestGit.available())
        TestWorkspace("plain").use { workspace ->
            workspace.write("tools/$settingsFile", "format = 1\n")
            commitAll(workspace)
            workspace.write(settingsFile, "format = 1\n")
            assertFalse(settingsFile in listed(workspace), "an untracked settings file was listed")

            TestGit.output(workspace.root, "add", settingsFile)
            TestGit.output(workspace.root, "commit", "-q", "-m", "settings")
            val paths = listed(workspace)

            assertFalse(settingsFile in paths, "a tracked settings file was listed: $paths")
            assertTrue("tools/$settingsFile" in paths, "a file of that name elsewhere is an ordinary file: $paths")
        }
    }

    @Test
    fun `editing the settings file produces no unit and no artifact`() = TestWorkspace("plain").use { workspace ->
        workspace.engine().use { engine ->
            engine.update()
            workspace.write(settingsFile, "format = 1\n\n[resources]\nworkers = 2\n")

            val report = engine.update()

            assertEquals(0, report.unitsProduced)
            assertTrue(report.unitsSkipped > 0)
            assertTrue(engine.graph().artifacts().none { it.ref.path.value == settingsFile })
        }
    }
}
