package io.github.krosstack.kscope.cli.service.entry

internal object SystemdUnit {
    private const val EXEC_START = "ExecStart="
    private val WORD = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"|(\\S+)")
    private val ESCAPE = Regex("\\\\(.)|%%|\\$\\$")

    fun text(startWords: List<String>): String = listOf(
        "[Unit]",
        "Description=KrosScope service",
        "",
        "[Service]",
        EXEC_START + startWords.joinToString(" ", transform = ::quoted),
        "",
        "[Install]",
        "WantedBy=default.target",
    ).joinToString(separator = "\n", postfix = "\n")

    fun registeredCommand(unitText: String): RegisteredCommand? {
        val line = unitText.lineSequence().firstOrNull { it.startsWith(EXEC_START) } ?: return null
        val words = WORD.findAll(line.removePrefix(EXEC_START)).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }
        return RegisteredCommand.of(words.map(::unescaped).toList())
    }

    private fun quoted(word: String): String =
        word.map(::escaped).joinToString(separator = "", prefix = "\"", postfix = "\"")

    private fun escaped(character: Char): String = when (character) {
        '\\' -> "\\\\"
        '"' -> "\\\""
        '\n' -> "\\n"
        '%' -> "%%"
        '$' -> "$$"
        else -> character.toString()
    }

    private fun unescaped(word: String): String = ESCAPE.replace(word) { match ->
        match.groups[1]?.value?.let { if (it == "n") "\n" else it } ?: match.value.take(1)
    }
}
