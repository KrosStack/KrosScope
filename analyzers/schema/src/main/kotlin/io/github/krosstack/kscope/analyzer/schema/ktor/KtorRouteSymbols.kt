package io.github.krosstack.kscope.analyzer.schema.ktor

import io.github.krosstack.kscope.analyzer.schema.IdentityFactory
import io.github.krosstack.kscope.analyzer.schema.SchemaSymbol
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.SymbolDescriptor

private const val NAMESPACE = "ktor"

/**
 * The symbols of the routes of one file. An id is `ktor/<file>:<METHOD> <path>.`; a route that a file declares more
 * than once, under different guards for example, has the number of its occurrence as the disambiguator of the later
 * ones.
 */
internal class KtorRouteSymbols(private val identity: IdentityFactory, private val file: String) {
    fun of(routes: List<KtorRoute>): List<SchemaSymbol> {
        val occurrences = HashMap<String, Int>()
        return routes.map { route ->
            val occurrence = (occurrences[route.name] ?: 0) + 1
            occurrences[route.name] = occurrence
            symbolOf(route, occurrence)
        }
    }

    private fun symbolOf(route: KtorRoute, occurrence: Int): SchemaSymbol {
        val base = SymbolDescriptor().namespace(NAMESPACE).meta(file)
        val descriptor = if (occurrence == 1) base.term(route.name) else base.method(route.name, occurrence.toString())
        return SchemaSymbol(
            id = identity.idOf(descriptor),
            kind = KtorKinds.route,
            name = route.name,
            qualifiedName = route.name,
            signature = route.name,
            container = null,
            declaration = route.declaration,
            nameSpan = route.nameSpan,
            attributes = mapOf(KtorKinds.METHOD_ATTRIBUTE to route.method, KtorKinds.PATH_ATTRIBUTE to route.path),
            evidence = EvidenceClass.TEXTUAL,
        )
    }
}
