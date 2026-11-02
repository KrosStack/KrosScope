package io.github.krosstack.kscope.engine.structure

import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/** Reads the `.git` entry of a directory as text. Git does not run, and only the pointer in the entry is followed. */
internal class GitCheckouts(private val base: Path) {
    fun at(directory: Path): GitCheckout? {
        val entry = directory.resolve(GitMarkerDetector.entryName())
        return when {
            Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS) ->
                checkout(directory, CheckoutKind.REPOSITORY, entry, "${GitMarkerDetector.entryName()} directory")

            Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS) -> fromPointer(directory, entry)

            else -> null
        }
    }

    fun enclosing(directory: Path): Pair<Path, GitCheckout>? =
        generateSequence(directory) { it.parent }.firstNotNullOfOrNull { candidate ->
            at(candidate)?.let { candidate to it }
        }

    fun otherWorktrees(checkoutDirectory: Path, repositoryId: String): List<WorktreeFinding> {
        val common = Path.of(repositoryId)
        val main = common.parent?.takeIf { common.fileName?.toString() == GitMarkerDetector.entryName() }
        val all = listOfNotNull(main?.let { it to "main checkout of the repository" }) + linkedWorktrees(common)
        return all.filter { (directory, _) -> Paths.identity(directory) != Paths.identity(checkoutDirectory) }
            .map { (directory, evidence) ->
                WorktreeFinding(Paths.display(base, directory), Files.isDirectory(directory), evidence)
            }
            .sortedBy { it.directory }
    }

    private fun linkedWorktrees(common: Path): List<Pair<Path, String>> {
        val listing = GitMarkerDetector.worktreeListing(common)
        if (!Files.isDirectory(listing)) return emptyList()
        return try {
            Files.newDirectoryStream(listing).use { entries ->
                entries.sortedBy { it.fileName.toString() }.take(MAX_WORKTREES).mapNotNull { entry ->
                    readLine(entry.resolve(POINTER_FILE))?.let { Path.of(it).parent }
                        ?.let { it to "listed in ${Paths.display(base, entry)}" }
                }
            }
        } catch (_: IOException) {
            emptyList()
        }
    }

    private fun fromPointer(directory: Path, entry: Path): GitCheckout? {
        val target = readLine(entry)?.let(GitMarkerDetector::pointerTarget) ?: return null
        val pointedTo = directory.resolve(target).normalize()
        val kind = GitMarkerDetector.kindOfPointer(pointedTo)
        val common = if (kind == CheckoutKind.LINKED_WORKTREE) commonDirectory(pointedTo) else pointedTo
        val evidence = "${GitMarkerDetector.entryName()} file points to ${Paths.display(base, pointedTo)}"
        return checkout(directory, kind, common, evidence)
    }

    private fun commonDirectory(worktreeEntry: Path): Path =
        readLine(worktreeEntry.resolve(COMMON_DIR_FILE))?.let { worktreeEntry.resolve(it).normalize() }
            ?: checkNotNull(worktreeEntry.parent?.parent)

    private fun checkout(directory: Path, kind: CheckoutKind, common: Path, evidence: String) =
        GitCheckout(Paths.display(base, directory), kind, Paths.identity(common), evidence)

    private fun readLine(file: Path): String? = try {
        if (Files.isRegularFile(file)) Files.newBufferedReader(file).use { it.readLine() }?.take(MAX_LINE) else null
    } catch (_: IOException) {
        null
    }

    private companion object {
        const val POINTER_FILE = "gitdir"
        const val COMMON_DIR_FILE = "commondir"
        const val MAX_WORKTREES = 200
        const val MAX_LINE = 4_096
    }
}
