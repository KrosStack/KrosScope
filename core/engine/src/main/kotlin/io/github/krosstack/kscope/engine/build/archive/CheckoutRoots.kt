package io.github.krosstack.kscope.engine.build.archive

import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.spi.RepositoryCheckout
import java.nio.file.InvalidPathException
import java.nio.file.Path

/** The checkout directory of each repository, to find what a build left in or beside it. */
internal class CheckoutRoots(checkouts: List<RepositoryCheckout>) {
    private val roots: Map<RepositoryId, Path> = checkouts.associate { it.repository.id to it.root }

    fun resolve(repository: RepositoryId?, path: String): Path? = try {
        roots[repository]?.resolve(path)?.normalize()
    } catch (_: InvalidPathException) {
        null
    }
}
