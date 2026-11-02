package io.github.krosstack.kscope.engine.structure

import java.nio.file.Path

/** What a `.git` entry tells about the directory that holds it. */
public enum class CheckoutKind { REPOSITORY, LINKED_WORKTREE, SUBMODULE }

/** The names and the text format of the files that Git leaves in a checkout; nothing here runs Git. */
internal object GitMarkerDetector {
    private const val ENTRY_NAME = ".git"
    private const val POINTER_PREFIX = "gitdir:"
    private const val WORKTREES = "worktrees"
    private const val MODULES = "modules"

    fun isEntry(fileName: String): Boolean = fileName == ENTRY_NAME

    fun entryName(): String = ENTRY_NAME

    fun pointerTarget(line: String): String? =
        line.trim().takeIf { it.startsWith(POINTER_PREFIX) }?.removePrefix(POINTER_PREFIX)?.trim()

    fun kindOfPointer(pointedTo: Path): CheckoutKind {
        val names = pointedTo.map { it.toString() }
        return when {
            names.dropLast(1).lastOrNull() == WORKTREES -> CheckoutKind.LINKED_WORKTREE
            MODULES in names -> CheckoutKind.SUBMODULE
            else -> CheckoutKind.REPOSITORY
        }
    }

    fun worktreeListing(commonDirectory: Path): Path = commonDirectory.resolve(WORKTREES)
}
