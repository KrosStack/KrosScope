package io.github.krosstack.kscope.context.estimate

import io.github.krosstack.kscope.context.BlockMeasureKey
import io.github.krosstack.kscope.context.ContextMarkdown
import io.github.krosstack.kscope.context.fragment.ContextGraph
import io.github.krosstack.kscope.context.token.BpeBlockBoundary
import io.github.krosstack.kscope.model.context.ContextFragment
import io.github.krosstack.kscope.model.context.ContextRendering
import io.github.krosstack.kscope.model.context.TokenizerId

/**
 * Names the block of a fragment without building it. Only a whole file, a declaration and an entry have a key here:
 * their text is the slice of a file that was read against its content hash, so the hash and the range say what it is.
 * The other renderings come from the graph and are named by [DerivedBlockKeys].
 */
internal class BlockMeasureKeys(private val graph: ContextGraph, private val estimator: TokenizerId) {
    fun of(fragment: ContextFragment): BlockMeasureKey? {
        val range = fragment.range
        val artifact = graph.artifacts[fragment.artifact]
        val path = ContextMarkdown.printedPath(fragment.artifact, graph.locations)
        return if (range != null && artifact != null && derivedFromContent(fragment, path)) {
            val layout = BlockLayout.digest(path, fragment.evidenceNote)
            BlockMeasureKey(artifact.contentHash, range, fragment.rendering, estimator, layout)
        } else {
            null
        }
    }

    private fun derivedFromContent(fragment: ContextFragment, path: String): Boolean =
        fragment.sourceBacked && fragment.rendering in FROM_CONTENT && BpeBlockBoundary.valid(path) &&
            BpeBlockBoundary.valid(fragment.evidenceNote.orEmpty())

    private companion object {
        val FROM_CONTENT = setOf(ContextRendering.FILE, ContextRendering.DECLARATION, ContextRendering.ENTRY)
    }
}
