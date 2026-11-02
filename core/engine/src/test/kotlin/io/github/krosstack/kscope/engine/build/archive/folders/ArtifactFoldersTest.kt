package io.github.krosstack.kscope.engine.build.archive.folders

import io.github.krosstack.kscope.engine.build.archive.JarFixtures
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArtifactFoldersTest {
    private val base: Path = Files.createTempDirectory("kscope-artifact-folders")
    private val coordinate = checkNotNull(MavenCoordinate.of("org.example.tools", "core", "1.0"))
    private val mavenJar = "org/example/tools/core/1.0/core-1.0.jar"

    @AfterTest
    fun clean() {
        base.toFile().deleteRecursively()
    }

    private fun found(vararg folders: Path): String? = ArtifactFolders(folders.toList()).find(coordinate)?.path
        ?.let { base.relativize(it).toString().replace(File.separatorChar, '/') }

    @Test
    fun `the Maven layout puts the group as directories`() {
        JarFixtures.write(base.resolve("m2/$mavenJar"))

        assertEquals("m2/$mavenJar", found(base.resolve("m2")))
    }

    @Test
    fun `the Gradle cache keeps the group dotted and the archive below a hash directory`() {
        JarFixtures.write(base.resolve("g/files-2.1/org.example.tools/core/1.0/abc123/core-1.0.jar"))

        assertEquals("g/files-2.1/org.example.tools/core/1.0/abc123/core-1.0.jar", found(base.resolve("g")))
    }

    @Test
    fun `a Gradle user home, its modules-2 directory and files-2_1 itself are all folders of the Gradle cache`() {
        val cached = "caches/modules-2/files-2.1/org.example.tools/core/1.0/abc123/core-1.0.jar"
        JarFixtures.write(base.resolve("home/$cached"))

        assertEquals("home/$cached", found(base.resolve("home")))
        assertEquals("home/$cached", found(base.resolve("home/caches")))
        assertEquals("home/$cached", found(base.resolve("home/caches/modules-2")))
        assertEquals("home/$cached", found(base.resolve("home/caches/modules-2/files-2.1")))
    }

    @Test
    fun `of several hash directories the first in name order that holds the archive is taken`() {
        val cache = base.resolve("g/files-2.1/org.example.tools/core/1.0")
        JarFixtures.write(cache.resolve("fff/core-1.0.jar"))
        JarFixtures.write(cache.resolve("aaa/core-1.0-sources.jar"))
        JarFixtures.write(cache.resolve("bbb/core-1.0.jar"))

        assertEquals("g/files-2.1/org.example.tools/core/1.0/bbb/core-1.0.jar", found(base.resolve("g")))
    }

    @Test
    fun `a JAR is taken before an AAR of the same coordinate`() {
        JarFixtures.writeAar(base.resolve("m2/org/example/tools/core/1.0/core-1.0.aar"))
        assertEquals("m2/org/example/tools/core/1.0/core-1.0.aar", found(base.resolve("m2")))

        JarFixtures.write(base.resolve("m2/$mavenJar"))
        assertEquals("m2/$mavenJar", found(base.resolve("m2")))
    }

    @Test
    fun `classifier archives and POM files are not the archive`() {
        val directory = base.resolve("m2/org/example/tools/core/1.0")
        JarFixtures.write(directory.resolve("core-1.0-sources.jar"))
        Files.writeString(directory.resolve("core-1.0.pom"), "<project/>")

        assertNull(found(base.resolve("m2")))
    }

    @Test
    fun `folders are tried in the order they were registered`() {
        JarFixtures.write(base.resolve("a/$mavenJar"))
        JarFixtures.write(base.resolve("b/$mavenJar"))

        assertEquals("b/$mavenJar", found(base.resolve("b"), base.resolve("a")))
        assertEquals("a/$mavenJar", found(base.resolve("a"), base.resolve("b")))
    }

    @Test
    fun `a folder that is missing or is a file holds nothing`() {
        Files.writeString(base.resolve("file"), "x")

        assertNull(found(base.resolve("missing"), base.resolve("file")))
    }
}
