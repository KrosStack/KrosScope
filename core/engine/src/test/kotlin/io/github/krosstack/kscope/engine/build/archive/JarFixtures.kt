package io.github.krosstack.kscope.engine.build.archive

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Writes small JAR and AAR files to stand in for the output of a build. */
internal object JarFixtures {
    fun write(jar: Path, vararg entries: String): Path {
        Files.createDirectories(jar.parent)
        Files.newOutputStream(jar).use { entriesTo(it, entries.asList()) }
        return jar
    }

    /** An AAR whose `classes.jar` holds the class files [classEntries]. */
    fun writeAar(aar: Path, vararg classEntries: String): Path {
        Files.createDirectories(aar.parent)
        val classes = ByteArrayOutputStream().also { entriesTo(it, classEntries.asList()) }.toByteArray()
        ZipOutputStream(Files.newOutputStream(aar)).use { archive ->
            archive.putNextEntry(ZipEntry("AndroidManifest.xml"))
            archive.closeEntry()
            archive.putNextEntry(ZipEntry("classes.jar"))
            archive.write(classes)
            archive.closeEntry()
        }
        return aar
    }

    private fun entriesTo(stream: OutputStream, entries: List<String>) {
        ZipOutputStream(stream).use { archive ->
            entries.forEach { entry ->
                archive.putNextEntry(ZipEntry(entry))
                archive.closeEntry()
            }
        }
    }
}
