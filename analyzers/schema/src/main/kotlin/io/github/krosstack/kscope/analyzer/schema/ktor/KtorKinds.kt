package io.github.krosstack.kscope.analyzer.schema.ktor

import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolKind

/** The kind and the attributes of the symbols of Ktor routes, which the linkers read. */
internal object KtorKinds {
    const val ROUTE_KIND = "ktor.route"
    const val METHOD_ATTRIBUTE = "ktor.method"
    const val PATH_ATTRIBUTE = "ktor.path"

    val route = SymbolKind(ROUTE_KIND, SymbolCategory.CONTRACT)
}
