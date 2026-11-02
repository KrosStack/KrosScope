package io.github.krosstack.kscope.analyzer.schema.generator.script

private const val TRIPLE_QUOTE = "\"\"\""
private const val TRIPLE_APOSTROPHE = "'''"
private const val QUOTE = "\""
private const val APOSTROPHE = "'"
private val templatePrefix = Regex("""^\$(?:\{([^}]*)}|([A-Za-z_][A-Za-z0-9_]*))""")

private val bracedAnchors = mapOf(
    "rootDir" to PathAnchor.BUILD_ROOT,
    "rootProject.rootDir" to PathAnchor.BUILD_ROOT,
    "rootProject.projectDir" to PathAnchor.BUILD_ROOT,
    "projectDir" to PathAnchor.MODULE,
    "project.projectDir" to PathAnchor.MODULE,
)
private val plainAnchors = mapOf("rootDir" to PathAnchor.BUILD_ROOT, "projectDir" to PathAnchor.MODULE)

private class Literal(val content: String, val allowsTemplates: Boolean)

/**
 * Reads the string literals of a build script, in Kotlin or in Groovy. Only a literal counts: a string with a template
 * that is not one of the prefixes below, or with an escape, is not read. A path may start with `$rootDir`,
 * `${rootDir}`, `${rootProject.rootDir}` or `${rootProject.projectDir}`, the directory of the build root, or with
 * `$projectDir`, `${projectDir}` or `${project.projectDir}`, the directory of the module; only a slash may follow a
 * prefix. In the single quotes of Groovy a `$` is no template.
 */
internal object ScriptLiterals {
    /** The text of a literal that holds nothing to compute: no template and no escape. */
    fun plain(token: String): String? =
        literalOf(token)?.content?.takeIf { it.isNotEmpty() && '$' !in it && '\\' !in it }

    fun path(token: String): ScriptPath? {
        val literal = literalOf(token)?.takeIf { it.content.isNotEmpty() && '\\' !in it.content }
        val prefix = literal?.takeIf { it.allowsTemplates }?.let { templatePrefix.find(it.content) }
        return when {
            literal == null -> null
            prefix != null -> anchored(literal.content, prefix)
            '$' in literal.content -> null
            else -> ScriptPath(literal.content, PathAnchor.PLAIN, literal.content)
        }
    }

    private fun anchored(content: String, prefix: MatchResult): ScriptPath? {
        val braced = prefix.groups[1]?.value
        val anchor = if (braced != null) bracedAnchors[braced] else plainAnchors[prefix.groups[2]?.value.orEmpty()]
        val rest = content.substring(prefix.range.last + 1)
        val isClean = '$' !in rest && (rest.isEmpty() || rest.startsWith('/'))
        return if (anchor != null && isClean) ScriptPath(content, anchor, rest.removePrefix("/")) else null
    }

    private fun literalOf(token: String): Literal? = when {
        isWrapped(token, TRIPLE_QUOTE) -> Literal(token.removeSurrounding(TRIPLE_QUOTE), true)
        isWrapped(token, TRIPLE_APOSTROPHE) -> Literal(token.removeSurrounding(TRIPLE_APOSTROPHE), false)
        isWrapped(token, QUOTE) -> Literal(token.removeSurrounding(QUOTE), true)
        isWrapped(token, APOSTROPHE) -> Literal(token.removeSurrounding(APOSTROPHE), false)
        else -> null
    }

    private fun isWrapped(token: String, quote: String): Boolean =
        token.length >= quote.length * 2 && token.startsWith(quote) && token.endsWith(quote)
}
