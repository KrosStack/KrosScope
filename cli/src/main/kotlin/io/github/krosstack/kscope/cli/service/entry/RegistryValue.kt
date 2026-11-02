package io.github.krosstack.kscope.cli.service.entry

internal object RegistryValue {
    const val NAME = "KrosScope"
    private const val FILE_HEADER = "Windows Registry Editor Version 5.00"
    private const val FILE_KEY = "HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val BYTE_ORDER_MARK = "\uFEFF"
    private val VALUE_LINE = Regex("(?m)^\\s*$NAME\\s+REG_SZ\\s+(.*?)\\s*$")
    private val WORD = Regex("\"([^\"]*)\"|(\\S+)")

    fun commandLine(words: List<String>): String = words.joinToString(" ") { "\"$it\"" }

    fun registrationFile(commandLine: String): ByteArray {
        val value = commandLine.replace("\\", "\\\\").replace("\"", "\\\"")
        val lines = listOf(FILE_HEADER, "", "[$FILE_KEY]", "\"$NAME\"=\"$value\"", "")
        val text = BYTE_ORDER_MARK + lines.joinToString(separator = "\r\n", postfix = "\r\n")
        return text.toByteArray(Charsets.UTF_16LE)
    }

    fun registeredCommand(queryOutput: String): RegisteredCommand? {
        val value = VALUE_LINE.find(queryOutput)?.groupValues?.get(1) ?: return null
        val words = WORD.findAll(value).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }.toList()
        return RegisteredCommand.of(words)
    }
}
