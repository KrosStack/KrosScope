package io.github.krosstack.kscope.analyzer.kotlin.worker.declarations

private const val MAX_DOCUMENTATION_LENGTH = 500

private val inlineTag = Regex("""\{@\w+\s*([^}]*)}""")
private val htmlTag = Regex("""</?\w+[^>]*>""")
private val kdocLink = Regex("""\[([A-Za-z_][\w.#]*)]""")
private val sentenceEnd = Regex("""[.!?](\s|$)""")
private val whitespace = Regex("""\s+""")

/** The first sentence of a KDoc or Javadoc comment, for lexical retrieval; null when it has no description. */
internal fun firstSentence(comment: String): String? {
    val paragraph = descriptionLines(comment).takeWhile { it.isNotEmpty() }.joinToString(" ")
    val text = paragraph.replace(inlineTag, "$1").replace(htmlTag, " ").replace(kdocLink, "$1")
        .replace(whitespace, " ").trim()
    val end = sentenceEnd.find(text)?.range?.first?.plus(1) ?: text.length
    return text.substring(0, end).take(MAX_DOCUMENTATION_LENGTH).ifEmpty { null }
}

private fun descriptionLines(comment: String): List<String> = comment.removePrefix("/**").removeSuffix("*/").lines()
    .map { it.trim().trimStart('*').trim() }
    .dropWhile { it.isEmpty() }
    .takeWhile { !it.startsWith("@") }
