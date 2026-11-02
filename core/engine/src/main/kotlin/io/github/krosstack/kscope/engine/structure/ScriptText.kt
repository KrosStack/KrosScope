package io.github.krosstack.kscope.engine.structure

/**
 * Splits the text of a Kotlin or Groovy script into code and string literals and drops the comments. It reads
 * characters only: a literal ends at its closing quote, a one-line literal also at the end of its line.
 */
internal class ScriptText private constructor(private val text: String, private val nestedComments: Boolean) {
    private val pieces = mutableListOf<ScriptPiece>()
    private val code = StringBuilder()
    private var position = 0

    private fun read(): List<ScriptPiece> {
        while (position < text.length) {
            val quote = QUOTES.firstOrNull { text.startsWith(it, position) }
            when {
                text.startsWith("//", position) -> skipLineComment()
                text.startsWith("/*", position) -> skipBlockComment()
                quote != null -> readLiteral(quote)
                else -> code.append(text[position++])
            }
        }
        closeCode()
        return pieces
    }

    private fun skipLineComment() {
        position = text.indexOf('\n', position).takeIf { it >= 0 } ?: text.length
        code.append(' ')
    }

    private fun skipBlockComment() {
        var depth = 0
        do {
            when {
                text.startsWith("/*", position) && (depth == 0 || nestedComments) -> {
                    depth++
                    position += 2
                }

                text.startsWith("*/", position) -> {
                    depth--
                    position += 2
                }

                else -> position++
            }
        } while (depth > 0 && position < text.length)
        code.append(' ')
    }

    private fun readLiteral(quote: String) {
        closeCode()
        val start = position + quote.length
        val end = literalEnd(start, quote)
        pieces += ScriptPiece(text.substring(start, end), literal = true)
        position = if (text.startsWith(quote, end)) end + quote.length else end
    }

    private fun literalEnd(start: Int, quote: String): Int {
        val oneLine = quote.length == 1
        var index = start
        while (index < text.length && !closes(index, quote, oneLine)) {
            index += if (oneLine && text[index] == '\\') 2 else 1
        }
        return index.coerceAtMost(text.length)
    }

    private fun closes(index: Int, quote: String, oneLine: Boolean): Boolean =
        text.startsWith(quote, index) || (oneLine && text[index] == '\n')

    private fun closeCode() {
        pieces += ScriptPiece(code.toString(), literal = false)
        code.clear()
    }

    companion object {
        private val QUOTES = listOf("\"\"\"", "'''", "\"", "'")

        fun pieces(text: String, nestedComments: Boolean): List<ScriptPiece> = ScriptText(text, nestedComments).read()
    }
}
