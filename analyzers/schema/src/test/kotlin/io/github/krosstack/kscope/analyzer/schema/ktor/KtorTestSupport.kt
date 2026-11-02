package io.github.krosstack.kscope.analyzer.schema.ktor

internal const val ROUTING_IMPORT = "import io.ktor.server.routing.*\n"

/** The names of the routes that the scanner finds in [code], which follows [header]. */
internal fun routeNames(code: String, header: String = ROUTING_IMPORT): List<String> =
    KtorRouteScanner(header + code).scan().map { it.name }
