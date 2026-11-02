package io.github.krosstack.kscope.engine.structure

import java.nio.file.Files
import java.nio.file.Path

/** A temporary directory in which tests lay out builds, projects and Git entries by hand; Git itself never runs. */
internal class StructureWorkspace : AutoCloseable {
    val root: Path = Files.createTempDirectory("kscope-structure").toRealPath()

    fun file(relative: String, text: String = "") {
        val target = root.resolve(relative)
        Files.createDirectories(target.parent)
        Files.writeString(target, text)
    }

    fun repository(relative: String) {
        Files.createDirectories(root.resolve(relative).resolve(".git"))
    }

    /** A linked worktree [worktree] of the repository in [main], laid out the way `git worktree add` does. */
    fun linkedWorktree(main: String, worktree: String, name: String = "linked") {
        val common = root.resolve(main).resolve(".git")
        val entry = common.resolve("worktrees").resolve(name)
        val checkout = root.resolve(worktree)
        file("$main/.git/worktrees/$name/gitdir", "${checkout.toString().replace('\\', '/')}/.git\n")
        file("$main/.git/worktrees/$name/commondir", "../..\n")
        file("$worktree/.git", "gitdir: ${entry.toString().replace('\\', '/')}\n")
        check(Files.isDirectory(common))
    }

    override fun close() {
        root.toFile().deleteRecursively()
    }
}
