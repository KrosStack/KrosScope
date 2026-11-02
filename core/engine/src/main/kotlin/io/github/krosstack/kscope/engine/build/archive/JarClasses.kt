package io.github.krosstack.kscope.engine.build.archive

import java.io.IOException
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * The classes of a JAR or of an AAR as stems: the package path and the name of the top-level class, without the nested
 * classes, without the `Kt` that Kotlin appends to the facade of a file, and without the descriptors of modules and
 * packages. The classes of an AAR are those of its `classes.jar`.
 */
internal object JarClasses {
    private const val CLASS = ".class"
    private const val META_INF = "META-INF/"
    private const val FACADE = "Kt"
    private const val AAR = ".aar"
    private const val AAR_CLASSES = "classes.jar"
    private val descriptors = setOf("module-info", "package-info")

    /** The stems of the archive, or null when there is no archive that can be read at [archive]. */
    fun stemsIn(archive: Path): Set<String>? = try {
        ZipFile(archive.toFile()).use { zip ->
            if (archive.toString().endsWith(AAR)) classesOfAar(zip) else stemsOf(zip.entries().asSequence().names())
        }
    } catch (_: IOException) {
        null
    }

    private fun classesOfAar(aar: ZipFile): Set<String> {
        val classes = aar.getEntry(AAR_CLASSES) ?: return emptySet()
        return ZipInputStream(aar.getInputStream(classes)).use { jar ->
            stemsOf(generateSequence { jar.nextEntry }.names())
        }
    }

    private fun Sequence<ZipEntry>.names(): Sequence<String> = map { it.name }

    private fun stemsOf(entries: Sequence<String>): Set<String> = entries.mapNotNull(::stemOf).toSet()

    private fun stemOf(entry: String): String? {
        if (!entry.endsWith(CLASS) || entry.startsWith(META_INF)) return null
        val name = entry.substringAfterLast('/').removeSuffix(CLASS).substringBefore('$')
        val directory = entry.substringBeforeLast('/', "")
        return if (name.isEmpty() || name in descriptors) null else join(directory, facadeOf(name))
    }

    private fun facadeOf(name: String): String = name.removeSuffix(FACADE).ifEmpty { name }

    private fun join(directory: String, name: String): String = if (directory.isEmpty()) name else "$directory/$name"
}
