package io.github.krosstack.kscope.analyzer.schema.spec

import io.github.krosstack.kscope.analyzer.schema.ktor.KtorKinds
import io.github.krosstack.kscope.model.Symbol

/** The route symbols of a workspace by the method and the shape of their path, each list in the order of the ids. */
internal class RouteIndex(routes: List<Symbol>) {
    private val byKey: Map<String, List<Symbol>> = routes.sortedBy { it.id.value }.groupBy {
        keyOf(it.attributes[KtorKinds.METHOD_ATTRIBUTE], it.attributes[KtorKinds.PATH_ATTRIBUTE])
    }

    fun find(method: String, path: String): List<Symbol> = byKey[keyOf(method, path)].orEmpty()

    private fun keyOf(method: String?, path: String?): String = "${method.orEmpty()} ${routeShape(path.orEmpty())}"
}
