package io.github.krosstack.kscope.analyzer.jni

import io.github.krosstack.kscope.analyzer.jni.natives.NativeFunction
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolKind
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.Visibility

private val functionKind = SymbolKind(NATIVE_FUNCTION_KIND, SymbolCategory.CONTRACT)

/** The symbol of one native function: its module, its source set if it has one, its file and its role in the id. */
internal class NativeFunctionFacts(private val anchor: SymbolAnchor) {
    fun symbolOf(function: NativeFunction) = Symbol(
        id = anchor.idOf(SymbolDescriptor().meta(anchor.path).meta(function.role.value).term(function.name)),
        kind = functionKind,
        name = function.name,
        qualifiedName = function.name,
        signature = function.signature,
        container = null,
        visibility = Visibility.PUBLIC,
        flags = emptySet(),
        origin = SymbolOrigin.Source,
        declaration = anchor.locationOf(function.declaration),
        nameLocation = anchor.locationOf(function.nameRange),
        documentation = null,
        scope = anchor.scope,
        evidenceClass = EvidenceClass.TEXTUAL,
        certainty = Certainty.EXACT,
        attributes = mapOf(ROLE_ATTRIBUTE to function.role.value),
    )
}
