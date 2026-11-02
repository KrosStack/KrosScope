package io.github.krosstack.kscope.cli.service.home

internal object ServiceFileText {
    private const val HEADER = "# Written by kscope service add and remove."
    private const val HEX_RADIX = 16
    private const val HEX_DIGITS = 4

    fun render(workspaces: List<String>, limits: ServiceLimits = ServiceLimits()): String = buildString {
        appendLine(HEADER)
        if (workspaces.isEmpty()) {
            appendLine("${ServiceFile.KEY} = []")
        } else {
            appendLine("${ServiceFile.KEY} = [")
            workspaces.forEach { appendLine("  ${quoted(it)},") }
            appendLine("]")
        }
        limits.idleMinutes?.let { appendLine("${ServiceFileLimits.IDLE_KEY} = $it") }
        limits.memoryMib?.let { appendLine("${ServiceFileLimits.MEMORY_KEY} = $it") }
    }

    private fun quoted(value: String): String = buildString {
        append('"')
        for (character in value) {
            when {
                character == '"' -> append("\\\"")
                character == '\\' -> append("\\\\")
                character.isISOControl() -> append("\\u").append(unicodeDigits(character))
                else -> append(character)
            }
        }
        append('"')
    }

    private fun unicodeDigits(character: Char): String = character.code.toString(HEX_RADIX).padStart(HEX_DIGITS, '0')
}
