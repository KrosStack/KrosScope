package io.github.krosstack.kscope.analyzer.jni.libraries

import io.github.krosstack.kscope.analyzer.jni.SymbolAnchor
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolKind
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.Visibility

internal const val LINKAGE_ATTRIBUTE = "library.linkage"

/** The symbol of one library that a native build file declares: its module, its file and its name in the id. */
internal class LibraryTargetFacts(private val anchor: SymbolAnchor) {
    fun symbolOf(target: LibraryTarget) = Symbol(
        id = anchor.idOf(SymbolDescriptor().meta(anchor.path).term(target.name)),
        kind = SymbolKind(target.kind, SymbolCategory.BUILD),
        name = target.name,
        qualifiedName = target.name,
        signature = target.signature,
        container = null,
        visibility = Visibility.PUBLIC,
        flags = emptySet(),
        origin = SymbolOrigin.Source,
        declaration = anchor.locationOf(target.declaration),
        nameLocation = anchor.locationOf(target.nameRange),
        documentation = null,
        scope = anchor.scope,
        evidenceClass = EvidenceClass.TEXTUAL,
        certainty = target.certainty,
        attributes = mapOf(LINKAGE_ATTRIBUTE to target.linkage),
    )
}
