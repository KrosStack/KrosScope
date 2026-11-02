package io.github.krosstack.kscope.benchmark.graph

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ParsedSymbolId

/** A stable text for a node, used to compare nodes found by different routes. */
internal fun NodeRef.key(): String = when (this) {
    is NodeRef.OfSymbol -> "symbol:${id.value}"
    is NodeRef.OfArtifact -> "artifact:${ref.path.value}"
    is NodeRef.OfModule -> "module:${id.value}"
    is NodeRef.OfSourceSet -> "source-set:${id.value}"
    is NodeRef.OfExternal -> "external:$coordinate"
}

/** A short text for a node in a report: a symbol is shown by its descriptor, the rest by what identifies it. */
internal fun NodeRef.label(): String = when (this) {
    is NodeRef.OfSymbol -> (id.identity() as? ParsedSymbolId.Valid)?.identity?.descriptor?.render() ?: id.value
    is NodeRef.OfArtifact -> ref.path.value
    is NodeRef.OfModule -> id.value.substringAfter("//")
    is NodeRef.OfSourceSet -> id.value.substringAfter("//")
    is NodeRef.OfExternal -> coordinate
}
