package io.github.krosstack.kscope.engine.build.archive

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JarClassesTest {
    private val directory: Path = Files.createTempDirectory("kscope-jar-classes")

    @AfterTest
    fun clean() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `nested classes and the facade of a file are folded into the class they belong to`() {
        val jar = JarFixtures.write(
            directory.resolve("lib.jar"),
            "sample/Slugs.class",
            "sample/Slugs\$Inner.class",
            "sample/Slugs\$Companion\$Named.class",
            "sample/SlugsKt.class",
            "sample/Texts.class",
        )

        assertEquals(setOf("sample/Slugs", "sample/Texts"), JarClasses.stemsIn(jar))
    }

    @Test
    fun `a class in the root package has no directory and a class named Kt keeps its name`() {
        val jar = JarFixtures.write(directory.resolve("lib.jar"), "Root.class", "sample/Kt.class")

        assertEquals(setOf("Root", "sample/Kt"), JarClasses.stemsIn(jar))
    }

    @Test
    fun `the manifest, resources, descriptors and entries below META-INF are not classes`() {
        val jar = JarFixtures.write(
            directory.resolve("lib.jar"),
            "META-INF/MANIFEST.MF",
            "META-INF/versions/9/sample/Slugs.class",
            "module-info.class",
            "sample/package-info.class",
            "sample/messages.properties",
            "sample/",
            "sample/Slugs.class",
        )

        assertEquals(setOf("sample/Slugs"), JarClasses.stemsIn(jar))
    }

    @Test
    fun `a JAR without classes has none`() {
        val jar = JarFixtures.write(directory.resolve("lib.jar"), "META-INF/MANIFEST.MF")

        assertEquals(emptySet(), JarClasses.stemsIn(jar))
    }

    @Test
    fun `a file that is not a JAR and a file that is missing have no classes to read`() {
        val garbage = Files.writeString(directory.resolve("garbage.jar"), "not an archive")

        assertNull(JarClasses.stemsIn(garbage))
        assertNull(JarClasses.stemsIn(directory.resolve("missing.jar")))
    }

    @Test
    fun `the classes of an AAR are those of its classes JAR`() {
        val aar = JarFixtures.writeAar(directory.resolve("lib.aar"), "sample/Slugs.class", "sample/Slugs\$Inner.class")

        assertEquals(setOf("sample/Slugs"), JarClasses.stemsIn(aar))
    }

    @Test
    fun `an AAR without a classes JAR has no classes`() {
        val aar = JarFixtures.write(directory.resolve("lib.aar"), "AndroidManifest.xml", "res/values/values.xml")

        assertEquals(emptySet(), JarClasses.stemsIn(aar))
    }

    @Test
    fun `an AAR whose classes JAR cannot be read has no classes to read`() {
        val aar = directory.resolve("lib.aar")
        ZipOutputStream(Files.newOutputStream(aar)).use { archive ->
            archive.putNextEntry(ZipEntry("classes.jar"))
            archive.write("not an archive".toByteArray())
            archive.closeEntry()
        }

        assertEquals(emptySet(), JarClasses.stemsIn(aar))
    }
}
