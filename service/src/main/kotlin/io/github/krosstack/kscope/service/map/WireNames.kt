package io.github.krosstack.kscope.service.map

import kotlinx.serialization.serializer

internal inline fun <reified E : Enum<E>> E.wireName(): String = serializer<E>().descriptor.getElementName(ordinal)
