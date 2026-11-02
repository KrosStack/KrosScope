package io.github.krosstack.kscope.context.estimate

import io.github.krosstack.kscope.context.BlockMeasureKey
import io.github.krosstack.kscope.context.ContextMarkdown
import io.github.krosstack.kscope.context.fragment.ContextGraph
import io.github.krosstack.kscope.model.context.ContextFragment
import io.github.krosstack.kscope.model.context.TokenizerId

/**
 * Names the block of a fragment whose text is not a slice of a file, a signature, an outline or a generated summary,
 * without building it. The text comes from the graph, so the key holds a digest of it: a text that changes while its
 * file does not is a block of its own. A file the graph does not know has no key, and neither has a block that is not
 * standalone, because only the measure of a standalone block may be kept.
 */
internal class DerivedBlockKeys(private val graph: ContextGraph, private val estimator: TokenizerId) {
    fun of(fragment: ContextFragment): BlockMeasureKey? {
        val artifact = graph.artifacts[fragment.artifact]
        val path = ContextMarkdown.printedPath(fragment.artifact, graph.locations)
        return if (artifact != null && BlockLayout.standalone(path, fragment.evidenceNote, fragment.text)) {
            BlockMeasureKey(
                artifact.contentHash,
                fragment.range,
                fragment.rendering,
                estimator,
                BlockLayout.digest(path, fragment.evidenceNote),
                BlockLayout.textDigest(fragment.text),
            )
        } else {
            null
        }
    }
}
