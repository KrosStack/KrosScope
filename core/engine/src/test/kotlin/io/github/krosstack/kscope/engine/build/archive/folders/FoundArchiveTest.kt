package io.github.krosstack.kscope.engine.build.archive.folders

import io.github.krosstack.kscope.engine.build.archive.JarFixtures
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FoundArchiveTest {
    private val base: Path = Files.createTempDirectory("kscope-found-archive")
    private val coordinate = checkNotNull(MavenCoordinate.of("org.example.tools", "core", "1.0"))

    @AfterTest
    fun clean() {
        base.toFile().deleteRecursively()
    }

    @Test
    fun `the archive found says its path, size and modified time`() {
        val jar = JarFixtures.write(base.resolve("m2/org/example/tools/core/1.0/core-1.0.jar"), "a/B.class")

        val archive = checkNotNull(ArtifactFolders(listOf(base.resolve("m2"))).find(coordinate))

        assertEquals(Files.size(jar), archive.sizeBytes)
        assertEquals(Files.getLastModifiedTime(jar).toMillis(), archive.modifiedMillis)
        assertTrue(archive.fingerprint.contains("core-1.0.jar"))
        assertTrue(archive.fingerprint.contains(archive.sizeBytes.toString()))
    }

    @Test
    fun `without folders there is nothing to look in`() {
        assertTrue(ArtifactFolders(emptyList()).isEmpty)
        assertNull(ArtifactFolders(emptyList()).find(coordinate))
    }
}
