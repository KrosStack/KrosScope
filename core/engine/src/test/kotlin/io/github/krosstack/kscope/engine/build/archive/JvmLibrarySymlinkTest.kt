package io.github.krosstack.kscope.engine.build.archive

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class JvmLibrarySymlinkTest {
    private val workspace = JvmLibraryWorkspace()
    private val libs = workspace.base.resolve("application/client/libs")

    @AfterTest
    fun clean() = workspace.close()

    private fun link(link: Path, target: Path) {
        Files.createDirectories(link.parent)
        val created = runCatching { Files.createSymbolicLink(link, target) }.isSuccess
        assumeTrue(created, "this file system cannot create symbolic links")
    }

    private fun linkedFiles(): List<String?> {
        workspace.draft.dependsOnFile(workspace.client, "client/libs/")
        return workspace.links().relationships.map { it.attributes["file"] }
    }

    @Test
    fun `a symbolic link to a JAR file counts as that file`() {
        val outside = JarFixtures.write(workspace.base.resolve("shared/text-1.0.0.jar"))
        link(libs.resolve("text-1.0.0.jar"), outside)

        assertEquals(listOf("text-1.0.0.jar"), linkedFiles())
    }

    @Test
    fun `a symbolic link to a JAR file in a subdirectory counts as that file`() {
        val outside = JarFixtures.write(workspace.base.resolve("shared/text-1.0.0.jar"))
        link(libs.resolve("nested/text-1.0.0.jar"), outside)

        assertEquals(listOf("text-1.0.0.jar"), linkedFiles())
    }

    @Test
    fun `a symbolic link to a directory is not walked`() {
        val outside = workspace.base.resolve("shared")
        JarFixtures.write(outside.resolve("text-1.0.0.jar"))
        link(libs.resolve("linked"), outside)

        assertEquals(emptyList(), linkedFiles())
    }
}
