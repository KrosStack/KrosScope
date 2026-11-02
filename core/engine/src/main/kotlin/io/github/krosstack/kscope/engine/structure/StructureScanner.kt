package io.github.krosstack.kscope.engine.structure

import io.github.krosstack.kscope.engine.discovery.GitAccess
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * Looks at the layout of a directory without changing anything: it asks Git for ignored paths and reads the names of
 * the remaining files, the `.git` entries and the settings scripts of Gradle builds as text. It descends at most
 * [maxDepth] directories below the root, skips the directories that a scan skips, never follows a symbolic link, and
 * stops after [maxDirectories] directories.
 */
public class StructureScanner internal constructor(
    maxDepth: Int,
    maxDirectories: Int,
    private val gitExecutable: String,
    private val gitRunner: GitIgnoreRunner = GitIgnoreProcessRunner(),
) {
    private val bounds = WalkBounds(maxDepth, maxDirectories)

    public constructor(maxDepth: Int, maxDirectories: Int = DEFAULT_MAX_DIRECTORIES) :
        this(maxDepth, maxDirectories, GitAccess.DEFAULT_EXECUTABLE)

    init {
        require(maxDepth >= 0) { "The depth must not be negative." }
    }

    public fun scan(directory: Path): StructureFindings {
        val root = directory.toRealPath()
        val checkouts = GitCheckouts(root)
        val enclosing = checkouts.enclosing(root)
        val ignored = GitIgnoreRules.load(root, enclosing?.first, bounds, gitExecutable, gitRunner)
        val walk = Walk(root, checkouts, bounds.maxDirectories, ignored)
        Files.walkFileTree(root, emptySet(), bounds.maxDepth + 1, walk)
        return StructureFindings(
            checkout = enclosing?.second,
            worktrees = enclosing?.let { (checkoutDirectory, found) ->
                checkouts.otherWorktrees(checkoutDirectory, found.repositoryId)
            }.orEmpty(),
            builds = walk.builds(),
            projects = walk.projects.sortedWith(compareBy({ it.directory }, { it.ecosystem })),
            repositories = walk.repositories.sortedBy { it.directory },
            directoriesVisited = walk.directories,
            ignoredDirectoriesSkipped = walk.ignoredDirectories,
            ignoreRulesNotice = ignored.notice,
            truncated = walk.truncated,
        )
    }

    public companion object {
        public const val DEFAULT_DEPTH: Int = 4
        public const val DEFAULT_MAX_DIRECTORIES: Int = 20_000
    }
}

/** How far a walk below the inspected directory may go, for the report and for the paths that Git is asked about. */
internal data class WalkBounds(val maxDepth: Int, val maxDirectories: Int = StructureScanner.DEFAULT_MAX_DIRECTORIES)

private class Walk(
    private val root: Path,
    private val checkouts: GitCheckouts,
    private val limit: Int,
    private val ignored: GitIgnoreRules,
) : SimpleFileVisitor<Path>() {
    private val settings = mutableMapOf<String, MutableList<String>>()
    val projects = mutableListOf<ProjectFinding>()
    val repositories = mutableListOf<RepositoryFinding>()
    var directories = 0
    var ignoredDirectories = 0
    var truncated = false

    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult = when {
        dir != root && !ignored.reached(dir) -> {
            truncated = true
            FileVisitResult.SKIP_SUBTREE
        }

        dir != root && ignored.contains(dir) -> {
            ignoredDirectories++
            FileVisitResult.SKIP_SUBTREE
        }

        dir != root && SkippedDirectories.contains(dir.fileName.toString()) -> {
            noteGitEntry(dir)
            FileVisitResult.SKIP_SUBTREE
        }

        directories >= limit -> {
            truncated = true
            FileVisitResult.TERMINATE
        }

        else -> {
            directories++
            FileVisitResult.CONTINUE
        }
    }

    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
        if (attrs.isSymbolicLink || !StructureMarkers.isMarker(file) || hidden(file)) return FileVisitResult.CONTINUE
        val name = file.fileName.toString()
        val directory = file.parent
        when {
            GitMarkerDetector.isEntry(name) -> if (directory != root) noteRepository(directory)

            attrs.isDirectory -> Unit

            ProjectMarkerDetector.isGradleSettings(name) ->
                settings.getOrPut(Paths.display(root, directory)) { mutableListOf() }.add(name)

            else -> ProjectMarkerDetector.otherEcosystem(name)?.let {
                projects += ProjectFinding(Paths.display(root, directory), it.label, name)
            }
        }
        return FileVisitResult.CONTINUE
    }

    override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE

    private fun hidden(marker: Path): Boolean {
        if (ignored.reached(marker)) return ignored.contains(marker)
        truncated = true
        return true
    }

    fun builds(): List<BuildFinding> = settings.entries.sortedBy { it.key }.map { (directory, names) ->
        val file = names.min()
        BuildFinding(directory, file, IncludedBuilds.declaredIn(root.resolve(directory).resolve(file), root))
    }

    private fun noteGitEntry(dir: Path) {
        if (GitMarkerDetector.isEntry(dir.fileName.toString())) dir.parent?.takeIf { it != root }?.let(::noteRepository)
    }

    private fun noteRepository(directory: Path) {
        checkouts.at(directory)?.let { repositories += RepositoryFinding(Paths.display(root, directory), it) }
    }
}

internal object SkippedDirectories {
    private val names = setOf(".git", ".gradle", ".idea", ".kscope", "build", "out", "node_modules")

    fun contains(name: String): Boolean = name.lowercase() in names
}
