package io.github.krosstack.kscope.cli

import io.github.krosstack.kscope.cli.text.format.Hints
import io.github.krosstack.kscope.service.KrosScope

/**
 * Ends the command with exit code 1 when the workspace has no index. It runs before anything is opened for reading,
 * so that a query never creates an index file.
 */
internal fun KrosScope.requireIndex() {
    if (!isIndexed()) throw InputFailure(Hints.NO_INDEX)
}
