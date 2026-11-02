package io.github.krosstack.kscope.analyzer.schema.ktor

/** The path of a route in its blocks: single slashes, a leading one and no trailing one but for the root. */
internal fun joinRoutePath(parts: List<String>): String =
    parts.flatMap { it.split('/') }.filter { it.isNotEmpty() }.joinToString("/", prefix = "/")
