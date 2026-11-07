package io.github.krosstack.kscope.diagram.emit

private const val LABEL_LIMIT = 60

public fun String.middleCut(): String = if (length <= LABEL_LIMIT) {
    this
} else {
    take(LABEL_LIMIT / 2) + "…" + takeLast(LABEL_LIMIT / 2 - 1)
}

public fun String.mermaidEscape(): String = map { character ->
    when {
        character.isISOControl() -> " "
        character in "\"&<>|#`\\" -> "#${character.code};"
        else -> character.toString()
    }
}.joinToString("")

public fun String.dotEscape(): String = map { character ->
    when (character) {
        '\\' -> "\\\\"
        '"' -> "\\\""
        '\n' -> "\\n"
        '\r' -> "\\r"
        '\t' -> "\\t"
        else -> if (character.isISOControl()) " " else character.toString()
    }
}.joinToString("")
