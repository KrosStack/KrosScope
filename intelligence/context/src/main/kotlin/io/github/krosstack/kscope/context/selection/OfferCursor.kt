package io.github.krosstack.kscope.context.selection

import io.github.krosstack.kscope.model.context.ContextFragment

internal class OfferCursor(offer: KeyedOffer, val slot: FileSlot) {
    private val variants = offer.variants
    private var remembered: RenderedFile? = null
    private var rememberedVersion = 0

    val nodeKey: String = offer.nodeKey
    var position = 0
        private set
    var fragment: ContextFragment? = variants.firstOrNull()
        private set

    val hasNext: Boolean get() = fragment != null

    fun currentRendering(): RenderedFile? = remembered.takeIf { rememberedVersion == slot.version }

    fun remember(rendered: RenderedFile): RenderedFile {
        remembered = rendered
        rememberedVersion = slot.version
        return rendered
    }

    fun advance() {
        position++
        fragment = variants.getOrNull(position)
    }
}
