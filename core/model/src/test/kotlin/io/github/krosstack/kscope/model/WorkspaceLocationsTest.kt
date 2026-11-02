package io.github.krosstack.kscope.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkspaceLocationsTest {
    private val library = Repository(RepositoryId("lib"), "library", null, null, RepoPath("library"))
    private val application = Repository(RepositoryId("app"), "application", null, null, RepoPath("application"))
    private val several = WorkspaceLocations(listOf(library, application))
    private val single = WorkspaceLocations(listOf(Repository(RepositoryId("only"), "only", null, null)))

    private fun ref(repository: String, path: String) = ArtifactRef(RepositoryId(repository), RepoPath(path))

    @Test
    fun `a file of a workspace of several repositories is named by directory and path`() {
        assertEquals("library/model/Record.kt", several.pathOf(ref("lib", "model/Record.kt")))
        assertEquals("application/settings.gradle.kts", several.pathOf(ref("app", "settings.gradle.kts")))
    }

    @Test
    fun `a file of a workspace of one repository is named by its path`() {
        assertEquals("src/Main.kt", single.pathOf(ref("only", "src/Main.kt")))
        assertEquals("src/Main.kt", WorkspaceLocations.SINGLE.pathOf(ref("only", "src/Main.kt")))
    }

    @Test
    fun `a workspace path names the file of the repository whose directory starts it`() {
        assertEquals(listOf(ref("lib", "model/Record.kt")), several.artifactsNamed("library/model/Record.kt"))
        assertEquals(emptyList(), several.artifactsNamed("model/Record.kt"))
        assertEquals(emptyList(), several.artifactsNamed("libraryx/model/Record.kt"))
    }

    @Test
    fun `a path in a workspace of one repository names the file of that repository`() {
        assertEquals(listOf(ref("only", "src/Main.kt")), single.artifactsNamed("src/Main.kt"))
    }

    @Test
    fun `a path that leaves out the directory is looked up in each repository in directory order`() {
        assertEquals(
            listOf(ref("app", "settings.gradle.kts"), ref("lib", "settings.gradle.kts")),
            several.withinEachRepository("settings.gradle.kts"),
        )
    }

    @Test
    fun `only a workspace of several repositories has directories`() {
        assertTrue(several.severalRepositories)
        assertFalse(single.severalRepositories)
        assertEquals("library", several.directoryOf(RepositoryId("lib")))
    }
}
