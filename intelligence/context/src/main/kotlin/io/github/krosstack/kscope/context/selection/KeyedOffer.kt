package io.github.krosstack.kscope.context.selection

import io.github.krosstack.kscope.context.fragment.ContextOffer
import io.github.krosstack.kscope.model.context.ContextFragment

internal class KeyedOffer(val offer: ContextOffer, val nodeKey: String) {
    val variants: List<ContextFragment> get() = offer.variants
}
