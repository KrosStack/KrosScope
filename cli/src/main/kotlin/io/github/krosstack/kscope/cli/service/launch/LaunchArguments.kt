package io.github.krosstack.kscope.cli.service.launch

internal object LaunchArguments {
    private const val HEADER = "# Written by kscope service install."
    private const val SPECIAL = "\\\"'#"

    fun render(arguments: List<String>): String =
        (listOf(HEADER) + arguments.map(::quoted)).joinToString(separator = "\n", postfix = "\n")

    fun parse(text: String): List<String> =
        text.lines().filter { it.isNotBlank() && !it.startsWith("#") }.map(::unquoted)

    private fun quoted(value: String): String =
        if (value.isEmpty() || value.any { it.isWhitespace() || it in SPECIAL }) {
            value.map(::escaped).joinToString(separator = "", prefix = "\"", postfix = "\"")
        } else {
            value
        }

    private fun escaped(character: Char): String = when (character) {
        '\\' -> "\\\\"
        '"' -> "\\\""
        '\n' -> "\\n"
        '\r' -> "\\r"
        '\t' -> "\\t"
        '\u000c' -> "\\f"
        else -> character.toString()
    }

    private fun unquoted(line: String): String = when {
        line.length >= 2 && line.startsWith('"') && line.endsWith('"') -> unescaped(line.substring(1, line.length - 1))
        else -> line
    }

    private fun unescaped(text: String): String = buildString {
        var index = 0
        while (index < text.length) {
            val character = text[index++]
            if (character == '\\' && index < text.length) append(unescape(text[index++])) else append(character)
        }
    }

    private fun unescape(character: Char): Char = when (character) {
        'n' -> '\n'
        'r' -> '\r'
        't' -> '\t'
        'f' -> '\u000c'
        else -> character
    }
}
