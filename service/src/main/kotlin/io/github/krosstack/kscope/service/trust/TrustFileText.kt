package io.github.krosstack.kscope.service.trust

import java.util.Locale

internal object TrustFileText {
    const val KEY = "trusted-workspaces"

    fun render(workspaces: Collection<String>): String {
        val lines = workspaces.distinct().sorted().joinToString("") { "  ${quoted(it)},\n" }
        return if (lines.isEmpty()) "$KEY = []\n" else "$KEY = [\n$lines]\n"
    }

    private fun quoted(value: String): String = "\"" + value.map(::escaped).joinToString("") + "\""

    private fun escaped(character: Char): String = when {
        character == '\\' -> "\\\\"
        character == '"' -> "\\\""
        character.code < SPACE || character.code == DELETE -> "\\u%04x".format(Locale.ROOT, character.code)
        else -> character.toString()
    }

    private const val SPACE = 0x20
    private const val DELETE = 0x7f
}
