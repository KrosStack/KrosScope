package io.github.krosstack.kscope.service.lookup

import io.github.krosstack.kscope.engine.WorkspaceConfig
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.NotIndexedException
import io.github.krosstack.kscope.service.OpenOptions
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.sql.SQLException

public class IndexLookup private constructor(
    stop: Path?,
    private val ready: (Path) -> Boolean,
    private val acceptsUnreadable: Boolean,
) {
    public constructor(stop: Path? = null, ready: (Path) -> Boolean = { false }) : this(stop, ready, false)

    private val stop: Path? = stop?.toAbsolutePath()?.normalize()

    public fun forStatus(): IndexLookup = IndexLookup(stop, { false }, acceptsUnreadable = true)

    public fun nearest(folder: Path): Path? = upwards(folder).firstOrNull { ready(it) || holdsIndex(it) }

    public fun below(folder: Path): List<Path> {
        val children = subfolders(folder.toAbsolutePath().normalize())
        return (children + children.flatMap(::subfolders)).filter(::holdsIndex).sortedBy { it.toString() }
    }

    public fun covering(folder: Path): Path? {
        val start = folder.toAbsolutePath().normalize()
        return nearest(start)?.takeIf { it != start && covers(it, start) }
    }

    private fun upwards(folder: Path): Sequence<Path> =
        generateSequence(folder.toAbsolutePath().normalize()) { current -> current.parent.takeIf { !stopsAt(current) } }

    private fun stopsAt(folder: Path): Boolean = stop != null && (folder == stop || sameFolder(folder, stop))

    private fun sameFolder(first: Path, second: Path): Boolean = try {
        Files.isSameFile(first, second)
    } catch (_: IOException) {
        false
    }

    private fun holdsIndex(folder: Path): Boolean {
        val file = folder.resolve(WorkspaceConfig.DEFAULT_INDEX_DIRECTORY).resolve(KrosScope.INDEX_FILE_NAME)
        return Files.isRegularFile(file) && KrosScope.open(folder, QUIET).use {
            if (acceptsUnreadable) it.holdsAnyIndex() else it.isIndexed()
        }
    }

    private fun subfolders(folder: Path): List<Path> = try {
        Files.newDirectoryStream(folder).use { entries -> entries.filter(::searched) }
    } catch (_: IOException) {
        emptyList()
    }

    private fun searched(entry: Path): Boolean {
        val name = entry.fileName.toString()
        return !name.startsWith(".") && name !in OUTPUT_NAMES && Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)
    }

    private fun covers(root: Path, folder: Path): Boolean = try {
        KrosScope.open(root, QUIET).use { scope ->
            scope.repositoryHolding(root.relativize(folder).joinToString("/")) != null
        }
    } catch (_: SQLException) {
        false
    } catch (_: IOException) {
        false
    } catch (_: NotIndexedException) {
        false
    }

    private companion object {
        val OUTPUT_NAMES = setOf("build", "out", "node_modules")
        val QUIET = OpenOptions(tokenCache = false)
    }
}
