package io.github.krosstack.kscope.engine.discovery

import io.github.krosstack.kscope.engine.WorkspaceConfig
import io.github.krosstack.kscope.engine.scope.ScanScope
import io.github.krosstack.kscope.engine.scope.ScopeFilter
import io.github.krosstack.kscope.model.Diagnostic
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/** [candidates] are the files before the scope narrowed them: what a pattern of the scope can match. */
internal class FileListing(val paths: List<String>, val notices: List<Diagnostic>, val candidates: List<String> = paths)

/**
 * Lists the files of a workspace through Git when possible and through a directory walk otherwise. The settings file
 * of the root is not part of the workspace that is indexed: it changes how a scan runs, never what it finds, so
 * editing it must invalidate nothing. Exactly the file that the settings reader opens is left out: `kscope.toml` in
 * the root, and a root entry that differs from that name only in letter case when it is the same file on this file
 * system, which is so where the file system ignores case. The [scope] narrows the listing and never widens it. A
 * checkout below the root keeps a file of that name: the rule is about the settings file of the workspace, so
 * `list(excludesSettingsFile = false)` keeps it.
 */
internal class FileLister(
    private val root: Path,
    indexDirectory: Path,
    private val git: GitAccess,
    scope: ScanScope = ScanScope.ALL,
    private val isBuildFile: (String) -> Boolean = { false },
) {
    private val filter = ScopeFilter(scope)

    private val indexPrefix: String? = indexDirectory.toAbsolutePath().normalize().let { index ->
        val base = root.toAbsolutePath().normalize()
        if (index != base && index.startsWith(base)) index.toRepoPathString(base) else null
    }

    private val settingsFile: Path = WorkspaceConfig.settingsFile(root)

    // Java calls two Windows paths equal when they differ only in letter case, even in a directory that the file
    // system keeps case-sensitive, and then isSameFile answers true without looking at the files. The extra segment
    // keeps the two paths apart, so that the files themselves are compared.
    private val settingsFileToCompare: Path = settingsFile.resolveSibling(".").resolve(settingsFile.fileName)

    fun list(excludesSettingsFile: Boolean = true): FileListing {
        val listing = when (git) {
            is GitAccess.WorkTree -> fromGit(git)
            GitAccess.NotARepository -> FileListing(WorkspaceWalker.relativeFiles(root), emptyList())
            is GitAccess.Unavailable -> FileListing(WorkspaceWalker.relativeFiles(root), listOf(git.notice))
        }
        val paths = listing.paths.filter(::isRepresentable).filterNot(::isInsideIndex)
            .filterNot { excludesSettingsFile && isSettingsFile(it) }
            .distinct().sorted()
        val admitted = paths.filter { filter.admits(it, isBuildFile) }
        return FileListing(admitted, listing.notices, paths)
    }

    private fun fromGit(workTree: GitAccess.WorkTree): FileListing = when (val files = workTree.listFiles()) {
        is GitFileList.Listed -> FileListing(files.paths.filter(::isRegularFile), emptyList())
        is GitFileList.Failed -> FileListing(WorkspaceWalker.relativeFiles(root), listOf(files.notice))
    }

    private fun isRegularFile(relativePath: String): Boolean =
        Files.isRegularFile(root.resolve(relativePath), LinkOption.NOFOLLOW_LINKS)

    private fun isRepresentable(relativePath: String): Boolean = BACKSLASH !in relativePath

    private fun isSettingsFile(relativePath: String): Boolean = when {
        relativePath == WorkspaceConfig.SETTINGS_FILE_NAME -> true
        '/' in relativePath -> false
        !relativePath.equals(WorkspaceConfig.SETTINGS_FILE_NAME, ignoreCase = true) -> false
        else -> isSameFileAsSettingsFile(root.resolve(relativePath))
    }

    private fun isSameFileAsSettingsFile(candidate: Path): Boolean = try {
        Files.isSameFile(candidate, settingsFileToCompare)
    } catch (_: IOException) {
        false
    }

    private fun isInsideIndex(relativePath: String): Boolean {
        val index = indexPrefix ?: return false
        return relativePath.equals(index, ignoreCase = true) || relativePath.startsWith("$index/", ignoreCase = true)
    }

    private companion object {
        const val BACKSLASH = '\\'
    }
}
