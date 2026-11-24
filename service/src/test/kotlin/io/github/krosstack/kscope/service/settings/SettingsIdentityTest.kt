package io.github.krosstack.kscope.service.settings

import io.github.krosstack.kscope.service.TestGit
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The settings file changes how a scan runs and nothing the index holds. */
class SettingsIdentityTest {
    private val settingsFile = "kscope.toml"
    private val bothResources = "format = 1\n\n[resources]\nworkers = 2\nworker-heap-mib = 512\n"

    private fun dumpOfRebuild(workspace: SettingsWorkspace) = workspace.open { scope ->
        scope.scan(rebuild = true)
        scope.canonicalDump()
    }

    @Test
    fun `the index of a workspace without a settings file is what it was before the file existed`() {
        assumeTrue(TestGit.available(), "Git is needed for the notice that the facts hold")
        SettingsWorkspace().use { workspace ->
            assertEquals(PINNED_SHA256, sha256Of(dumpOfRebuild(workspace)))
        }
    }

    @Test
    fun `no file an empty file and a file that sets both resources give the same index`() {
        SettingsWorkspace().use { workspace ->
            val without = dumpOfRebuild(workspace)

            workspace.write(settingsFile, "")
            val empty = dumpOfRebuild(workspace)

            workspace.write(settingsFile, bothResources)
            val withResources = dumpOfRebuild(workspace)

            assertEquals(without, empty)
            assertEquals(without, withResources)
        }
    }

    @Test
    fun `the artifacts and the search documents are the same with and without the file`() {
        SettingsWorkspace().use { workspace ->
            val without = workspace.open { scope ->
                scope.scan(rebuild = true)
                scope.artifactPaths() to scope.searchDocuments()
            }

            workspace.write(settingsFile, bothResources)
            val with = workspace.open { scope ->
                scope.scan(rebuild = true)
                scope.artifactPaths() to scope.searchDocuments()
            }

            assertEquals(without, with)
            assertFalse(settingsFile in with.first)
            assertTrue(with.first.isNotEmpty())
        }
    }

    private companion object {
        const val PINNED_SHA256 = "4cda710ce19c7b217f6b04d9182a43e6d2b9c3aafd56daeaec9c27f3dbc248be"
    }
}
