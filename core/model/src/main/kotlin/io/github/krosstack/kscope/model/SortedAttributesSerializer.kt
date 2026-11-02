package io.github.krosstack.kscope.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer

internal object SortedAttributesSerializer : KSerializer<Map<String, String>> {
    private val delegate = MapSerializer(String.serializer(), String.serializer())

    override val descriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: Map<String, String>) =
        delegate.serialize(encoder, value.toSortedMap())

    override fun deserialize(decoder: Decoder): Map<String, String> = delegate.deserialize(decoder)
}
