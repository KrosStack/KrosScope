package io.github.krosstack.kscope.engine

import io.github.krosstack.kscope.engine.discovery.CheckoutInspector
import java.nio.file.Path

/** What a directory is to Git, as registering it with a workspace needs to know. */
public sealed interface CheckoutInspection {
    /** A work tree whose top is [root] and whose history has a commit, so that it has a repository id. */
    public data class Committed(public val root: Path) : CheckoutInspection

    /** A work tree whose top is [root] and whose history has no commit yet. */
    public data class WithoutCommit(public val root: Path) : CheckoutInspection

    public data object NotARepository : CheckoutInspection

    public data class GitUnavailable(public val reason: String) : CheckoutInspection

    public companion object {
        /** Asks Git about [directory], which may lie anywhere inside the work tree. */
        public fun of(directory: Path, executable: String = "git"): CheckoutInspection =
            CheckoutInspector(executable).inspect(directory)
    }
}
