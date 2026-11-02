package io.github.krosstack.kscope.context.estimate

import io.github.krosstack.kscope.context.TokenCountStore
import io.github.krosstack.kscope.context.selection.BlockCost
import io.github.krosstack.kscope.context.selection.BlockCosts
import io.github.krosstack.kscope.model.context.ContextFragment

/**
 * The measure of the block of a fragment. A block with a key is looked up in the store before it is built; a measure
 * that is found belongs to a standalone block, because only the measure of a standalone block is ever offered. A
 * block that is not found, or has no key, is built, measured and checked, and a standalone one is offered. The key
 * of a slice of a file is found first, and the key of a block that comes from the graph second.
 */
internal class FragmentMeasures(
    private val fileKeys: BlockMeasureKeys,
    private val graphKeys: DerivedBlockKeys,
    private val store: TokenCountStore,
    private val memory: BlockCosts,
) {
    fun of(fragment: ContextFragment): BlockCost {
        val key = fileKeys.of(fragment) ?: graphKeys.of(fragment) ?: return memory.of(fragment)
        val kept = store.recallMeasure(key)
        return if (kept != null) {
            BlockCost(kept, standalone = true)
        } else {
            memory.of(fragment).also { if (it.standalone) store.rememberMeasure(key, it.measure) }
        }
    }
}
