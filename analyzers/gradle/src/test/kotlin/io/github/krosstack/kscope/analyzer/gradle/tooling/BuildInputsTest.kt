package io.github.krosstack.kscope.analyzer.gradle.tooling

import io.github.krosstack.kscope.analyzer.gradle.ProviderWorkspace
import io.github.krosstack.kscope.analyzer.gradle.WorkspaceRequests
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.RepositoryId
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class BuildInputsTest {
    private val directory = Files.createTempDirectory("kscope-build-inputs")
    private val producer = ProducerRef("kscope.build.gradle.tooling", "1")
    private val assets = PluginAssets.extract(directory.resolve("model"))
    private val repository = WorkspaceRequests.repository.id

    @AfterTest
    fun cleanUp() {
        directory.toFile().deleteRecursively()
    }

    private fun fingerprint(workspace: ProviderWorkspace, id: RepositoryId = repository, root: Path = workspace.root) =
        BuildInputs(id, root, directory).fingerprint(producer, WorkspaceRequests.of(workspace.root).artifacts, assets)

    @Test
    fun `two checkouts with the same build files and layout have different fingerprints`() {
        ProviderWorkspace().use { first ->
            ProviderWorkspace().use { second ->
                first.givenSimpleBuild()
                second.givenSimpleBuild()

                assertNotEquals(fingerprint(first), fingerprint(second))
                assertEquals(fingerprint(first), fingerprint(first))
            }
        }
    }

    @Test
    fun `the repository is part of the fingerprint`() {
        ProviderWorkspace().use { workspace ->
            workspace.givenSimpleBuild()

            assertNotEquals(fingerprint(workspace), fingerprint(workspace, id = RepositoryId("another-repository")))
        }
    }

    @Test
    fun `a location written with dot segments has the fingerprint of the normalized one`() {
        ProviderWorkspace().use { workspace ->
            workspace.givenSimpleBuild()
            val detour = workspace.root.resolve("app").resolve("..")

            assertEquals(fingerprint(workspace), fingerprint(workspace, root = detour))
        }
    }
}
