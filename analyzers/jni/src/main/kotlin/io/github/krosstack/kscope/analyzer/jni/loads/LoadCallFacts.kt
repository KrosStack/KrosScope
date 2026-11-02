package io.github.krosstack.kscope.analyzer.jni.loads

import io.github.krosstack.kscope.analyzer.jni.SymbolAnchor
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolKind
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.Visibility

internal const val LOAD_LIBRARY_KIND = "jvm.load-library"
internal const val LOAD_CALL_ATTRIBUTE = "jvm.load-call"

private val loadKind = SymbolKind(LOAD_LIBRARY_KIND, SymbolCategory.CONTRACT)

/** The symbol of one call that loads a native library: its module, its source set, its file and its library. */
internal class LoadCallFacts(private val anchor: SymbolAnchor) {
    fun symbolOf(call: LoadCall) = Symbol(
        id = anchor.idOf(SymbolDescriptor().meta(anchor.path).meta("load-library").term(call.library)),
        kind = loadKind,
        name = call.library,
        qualifiedName = call.library,
        signature = "${call.call}(\"${call.argument}\")",
        container = null,
        visibility = Visibility.PUBLIC,
        flags = emptySet(),
        origin = SymbolOrigin.Source,
        declaration = anchor.locationOf(call.declaration),
        nameLocation = anchor.locationOf(call.argumentRange),
        documentation = null,
        scope = anchor.scope,
        evidenceClass = EvidenceClass.TEXTUAL,
        certainty = Certainty.EXACT,
        attributes = mapOf(LOAD_CALL_ATTRIBUTE to call.call),
    )
}
