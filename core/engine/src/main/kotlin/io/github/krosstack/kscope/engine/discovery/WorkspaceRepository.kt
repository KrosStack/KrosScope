package io.github.krosstack.kscope.engine.discovery

import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.Repository
import io.github.krosstack.kscope.spi.RepositoryCheckout
import java.nio.file.Path

/** A checkout [root] below the workspace root at [directory]. Its identity is worked out only when it is asked for. */
internal class WorkspaceRepository(
    val root: Path,
    val directory: RepoPath,
    val git: GitAccess,
    identity: () -> Repository,
) {
    val repository: Repository by lazy(identity)

    val checkout: RepositoryCheckout by lazy { RepositoryCheckout(repository, root) }

    /** Differs after every move of HEAD; empty outside a Git work tree. */
    fun headToken(): String = (git as? GitAccess.WorkTree)?.headToken().orEmpty()
}
