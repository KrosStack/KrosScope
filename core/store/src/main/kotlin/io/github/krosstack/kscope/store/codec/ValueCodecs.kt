package io.github.krosstack.kscope.store.codec

import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.TargetId
import io.github.krosstack.kscope.model.VariantId
import io.github.krosstack.kscope.store.sqlite.Binder
import io.github.krosstack.kscope.store.sqlite.Cursor
import kotlinx.serialization.encodeToString

internal fun scopeColumns(): List<String> = listOf("scope_source_set TEXT", "scope_target TEXT", "scope_variant TEXT")

internal fun Binder.scope(scope: Scope) {
    textOrNull(scope.sourceSet?.value)
    textOrNull(scope.target?.value)
    textOrNull(scope.variant?.value)
}

internal fun Cursor.scope(): Scope = Scope(
    sourceSet = textOrNull()?.let(::SourceSetId),
    target = textOrNull()?.let(::TargetId),
    variant = textOrNull()?.let(::VariantId),
)

internal fun Binder.attributes(attributes: Map<String, String>) =
    text(KscopeJson.encodeToString<Map<String, String>>(attributes.toSortedMap()))

internal fun Cursor.attributes(): Map<String, String> = KscopeJson.decodeFromString<Map<String, String>>(text())

internal inline fun <reified T> Binder.json(value: T) = text(KscopeJson.encodeToString(value))

internal inline fun <reified T> Cursor.json(): T = KscopeJson.decodeFromString<T>(text())
