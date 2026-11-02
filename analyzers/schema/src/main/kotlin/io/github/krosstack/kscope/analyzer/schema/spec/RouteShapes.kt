package io.github.krosstack.kscope.analyzer.schema.spec

private const val TAIL = "<tail>"
private val tailcard = Regex("""\{[^{}]*\.\.\.\}""")
private val placeholder = Regex("""\{[^{}]*\}""")

/**
 * The path as it is compared between a description and a route: each parameter, whatever its name or its type
 * suffix, is `{}`; a tailcard such as `{rest...}` stays apart as `<tail>` because it takes several segments; slashes
 * are single, the first is there and the last is gone but for the root.
 */
internal fun routeShape(path: String): String =
    path.replace(tailcard, TAIL).replace(placeholder, "{}").split('/').filter { it.isNotEmpty() }
        .joinToString("/", prefix = "/")
