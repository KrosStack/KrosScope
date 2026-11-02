package io.github.krosstack.kscope.service.structure

import java.nio.file.Files
import java.nio.file.Path

/** Runs [block] in a temporary directory in which tests lay out builds and Git entries by hand; Git never runs. */
internal fun <T> inStructureDirectory(block: (Path) -> T): T {
    val root = Files.createTempDirectory("kscope-structure-report").toRealPath()
    try {
        return block(root)
    } finally {
        root.toFile().deleteRecursively()
    }
}

internal fun Path.writeText(relative: String, text: String = "") {
    val target = resolve(relative)
    Files.createDirectories(target.parent)
    Files.writeString(target, text)
}

internal fun Path.makeRepository(relative: String) {
    Files.createDirectories(resolve(relative).resolve(".git"))
}

/** A linked worktree [worktree] of the repository in [main], laid out the way `git worktree add` does. */
internal fun Path.makeLinkedWorktree(main: String, worktree: String) {
    val entry = resolve(main).resolve(".git/worktrees/linked")
    writeText("$main/.git/worktrees/linked/gitdir", "${resolve(worktree).toString().replace('\\', '/')}/.git\n")
    writeText("$main/.git/worktrees/linked/commondir", "../..\n")
    writeText("$worktree/.git", "gitdir: ${entry.toString().replace('\\', '/')}\n")
}
