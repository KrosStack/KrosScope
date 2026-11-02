package io.github.krosstack.kscope.analyzer.jni

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolFlag
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolIdentity
import io.github.krosstack.kscope.model.SymbolKind
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.Visibility

/** What a test says about a Kotlin or Java declaration that stands for a native method. */
internal data class ExternalSpec(
    val packageName: String,
    val types: List<String>,
    val method: String,
    val signature: String,
    val kind: String = "kotlin.function",
    val flags: Set<SymbolFlag> = setOf(SymbolFlag.EXTERNAL),
    val parameters: String = "",
    val path: String = "bridge/src/main/kotlin/sample/Checksum.kt",
    val sourceSet: String = "main",
)

/** Builds the symbols of declarations and of the objects around them. */
internal object Declarations {
    private fun idOf(descriptor: SymbolDescriptor, sourceSet: String = "main"): SymbolId =
        SymbolIdentity.workspace(NativeModel.module, sourceSet, descriptor).toSymbolId()

    private fun typeDescriptor(packageName: String, types: List<String>): SymbolDescriptor {
        val root = packageName.split('.').filter { it.isNotEmpty() }
            .fold(SymbolDescriptor()) { descriptor, segment -> descriptor.namespace(segment) }
        return types.fold(root) { descriptor, type -> descriptor.type(type) }
    }

    fun external(spec: ExternalSpec, container: Symbol? = null, origin: SymbolOrigin = SymbolOrigin.Source): Symbol {
        val owner = typeDescriptor(spec.packageName, spec.types)
        return symbolOf(
            Draft(
                id = idOf(owner.method(spec.method, spec.parameters), spec.sourceSet),
                container = container?.id,
                kind = SymbolKind(spec.kind, SymbolCategory.CALLABLE),
                name = spec.method,
                signature = spec.signature,
                flags = spec.flags,
                origin = origin,
                path = spec.path,
                sourceSet = spec.sourceSet,
            ),
        )
    }

    fun type(packageName: String, types: List<String>, kind: String, signature: String? = null): Symbol = symbolOf(
        Draft(
            id = idOf(typeDescriptor(packageName, types)),
            container = null,
            kind = SymbolKind(kind, SymbolCategory.TYPE),
            name = types.last(),
            signature = signature,
            flags = emptySet(),
            origin = SymbolOrigin.Source,
            path = "bridge/src/main/kotlin/${types.first()}.kt",
            sourceSet = "main",
        ),
    )

    private fun symbolOf(draft: Draft): Symbol {
        val artifact = ArtifactRef(NativeModel.repository, RepoPath(draft.path))
        return Symbol(
            id = draft.id,
            kind = draft.kind,
            name = draft.name,
            qualifiedName = draft.name,
            signature = draft.signature,
            container = draft.container,
            visibility = Visibility.PUBLIC,
            flags = draft.flags,
            origin = draft.origin,
            declaration = SourceLocation(artifact, TextRange(3, 1, 3, 40)),
            nameLocation = SourceLocation(artifact, TextRange(3, 18, 3, 23)),
            documentation = null,
            scope = Scope(sourceSet = SourceSetId("repo//:bridge@${draft.sourceSet}")),
            evidenceClass = EvidenceClass.COMPILER,
            certainty = Certainty.EXACT,
        )
    }
}

private data class Draft(
    val id: SymbolId,
    val container: SymbolId?,
    val kind: SymbolKind,
    val name: String,
    val signature: String?,
    val flags: Set<SymbolFlag>,
    val origin: SymbolOrigin,
    val path: String,
    val sourceSet: String,
)
