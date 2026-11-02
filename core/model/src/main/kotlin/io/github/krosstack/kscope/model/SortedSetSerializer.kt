package io.github.krosstack.kscope.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer

internal open class SortedSetSerializer<T>(element: KSerializer<T>, private val order: Comparator<T>) :
    KSerializer<Set<T>> {
    private val delegate = SetSerializer(element)

    override val descriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: Set<T>) =
        delegate.serialize(encoder, value.sortedWith(order).toCollection(LinkedHashSet()))

    override fun deserialize(decoder: Decoder): Set<T> = delegate.deserialize(decoder)
}

internal object ModuleTraitSetSerializer : SortedSetSerializer<ModuleTrait>(serializer<ModuleTrait>(), naturalOrder())

internal object SymbolFlagSetSerializer : SortedSetSerializer<SymbolFlag>(serializer<SymbolFlag>(), naturalOrder())
