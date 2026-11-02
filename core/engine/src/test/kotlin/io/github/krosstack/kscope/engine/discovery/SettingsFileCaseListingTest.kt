package io.github.krosstack.kscope.engine.discovery

import io.github.krosstack.kscope.engine.TestWorkspace
import io.github.krosstack.kscope.engine.WorkspaceConfig
import io.github.krosstack.kscope.engine.structure.TestGit
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Only the file that the settings reader opens is left out of the listing, whatever the case of its name. */
class SettingsFileCaseListingTest {
    private val lower = WorkspaceConfig.SETTINGS_FILE_NAME
    private val upper = "KSCOPE.TOML"

    private fun ignoresLetterCase(directory: Path): Boolean {
        val probe = Files.writeString(directory.resolve("case-probe.tmp"), "probe")
        try {
            return Files.exists(directory.resolve("CASE-PROBE.TMP"))
        } finally {
            Files.delete(probe)
        }
    }

    private fun modes(): List<Boolean> = if (TestGit.available()) listOf(false, true) else listOf(false)

    private fun listed(workspace: TestWorkspace, withGit: Boolean): List<String> {
        if (withGit) TestGit.output(workspace.root, "add", "-A")
        return FileLister(workspace.root, workspace.indexDirectory, GitAccess.probe(workspace.root)).list().paths
    }

    private fun inWorkspace(withGit: Boolean, files: List<String>, check: (TestWorkspace, Boolean) -> Unit) =
        TestWorkspace().use { workspace ->
            if (withGit) TestGit.output(workspace.root, "init", "-q")
            files.forEach { workspace.write(it, "format = 1\n") }
            check(workspace, ignoresLetterCase(workspace.root.parent))
        }

    @Test
    fun `the settings file in its own name is never listed`() {
        for (withGit in modes()) {
            inWorkspace(withGit, listOf(lower, "README.md")) { workspace, _ ->
                assertEquals(listOf("README.md"), listed(workspace, withGit), "with git: $withGit")
            }
        }
    }

    @Test
    fun `an upper case name with no settings file beside it is listed where it is another file`() {
        for (withGit in modes()) {
            inWorkspace(withGit, listOf(upper)) { workspace, ignoresCase ->
                val paths = listed(workspace, withGit)

                assertFalse(lower in paths, "with git: $withGit")
                assertEquals(!ignoresCase, upper in paths, "with git: $withGit, ignoring case: $ignoresCase")
            }
        }
    }

    @Test
    fun `where both names are two files only the upper case one is listed`() {
        for (withGit in modes()) {
            inWorkspace(withGit, listOf(lower, upper)) { workspace, ignoresCase ->
                assumeTrue(!ignoresCase, "this file system treats the two names as one file")

                assertEquals(listOf(upper), listed(workspace, withGit), "with git: $withGit")
            }
        }
    }

    @Test
    fun `a settings file below the root is an ordinary file in any case`() {
        for (withGit in modes()) {
            inWorkspace(withGit, listOf("tools/$lower", "docs/$upper")) { workspace, _ ->
                val paths = listed(workspace, withGit)

                assertTrue("tools/$lower" in paths && "docs/$upper" in paths, "with git: $withGit, listed: $paths")
            }
        }
    }
}
