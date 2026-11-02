package io.github.krosstack.kscope.store.codec

import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.BuildSystem
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.SourceSetRole
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.Visibility
import io.github.krosstack.kscope.store.sqlite.Binder
import io.github.krosstack.kscope.store.sqlite.Cursor
import kotlinx.serialization.KSerializer

internal class WireNames<E : Enum<E>>(serializer: KSerializer<E>, values: List<E>) {
    private val names: List<String> = values.indices.map { serializer.descriptor.getElementName(it) }
    private val byName: Map<String, E> = names.zip(values).toMap()

    fun nameOf(value: E): String = names[value.ordinal]

    fun valueOf(name: String): E = checkNotNull(byName[name]) { "Unknown stored value '$name'" }
}

internal object Wires {
    val artifactCategory = WireNames(ArtifactCategory.serializer(), ArtifactCategory.entries)
    val buildSystem = WireNames(BuildSystem.serializer(), BuildSystem.entries)
    val certainty = WireNames(Certainty.serializer(), Certainty.entries)
    val diagnosticSeverity = WireNames(DiagnosticSeverity.serializer(), DiagnosticSeverity.entries)
    val evidenceClass = WireNames(EvidenceClass.serializer(), EvidenceClass.entries)
    val ownershipKind = WireNames(OwnershipKind.serializer(), OwnershipKind.entries)
    val platform = WireNames(Platform.serializer(), Platform.entries)
    val relationKind = WireNames(RelationKind.serializer(), RelationKind.entries)
    val sourceSetRole = WireNames(SourceSetRole.serializer(), SourceSetRole.entries)
    val symbolCategory = WireNames(SymbolCategory.serializer(), SymbolCategory.entries)
    val visibility = WireNames(Visibility.serializer(), Visibility.entries)
}

internal fun <E : Enum<E>> Binder.enum(wire: WireNames<E>, value: E) = text(wire.nameOf(value))

internal fun <E : Enum<E>> Binder.enumOrNull(wire: WireNames<E>, value: E?) = textOrNull(value?.let(wire::nameOf))

internal fun <E : Enum<E>> Cursor.enum(wire: WireNames<E>): E = wire.valueOf(text())

internal fun <E : Enum<E>> Cursor.enumOrNull(wire: WireNames<E>): E? = textOrNull()?.let(wire::valueOf)
