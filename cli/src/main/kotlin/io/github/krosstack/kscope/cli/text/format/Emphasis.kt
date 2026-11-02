package io.github.krosstack.kscope.cli.text.format

private const val ESCAPE = "\u001B["
private const val RESET = "${ESCAPE}0m"
private const val BOLD = "${ESCAPE}1m"
private const val DIM = "${ESCAPE}2m"
private const val RED = "${ESCAPE}31m"
private const val GREEN = "${ESCAPE}32m"
private const val YELLOW = "${ESCAPE}33m"
private const val CYAN = "${ESCAPE}36m"

/**
 * Bold and colour for text that goes to a terminal. A disabled emphasis returns the text unchanged, so captured output
 * never holds an escape code.
 */
internal class Emphasis(private val enabled: Boolean) {
    fun bold(text: String): String = wrap(BOLD, text)

    fun dim(text: String): String = wrap(DIM, text)

    fun good(text: String): String = wrap("$BOLD$GREEN", text)

    fun warning(text: String): String = wrap("$BOLD$YELLOW", text)

    fun bad(text: String): String = wrap("$BOLD$RED", text)

    fun header(text: String): String = wrap("$BOLD$CYAN", text)

    private fun wrap(code: String, text: String): String =
        if (enabled && text.isNotEmpty()) "$code$text$RESET" else text

    companion object {
        val NONE = Emphasis(false)
    }
}
