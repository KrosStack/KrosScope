package io.github.krosstack.kscope.cli.service.entry

internal object LaunchAgentPlist {
    const val LABEL = "io.github.krosstack.kscope"
    private const val DOCTYPE = "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" " +
        "\"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">"
    private val STRING = Regex("<string>(.*?)</string>")

    fun text(startWords: List<String>): String = buildList {
        add("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        add(DOCTYPE)
        add("<plist version=\"1.0\">")
        add("<dict>")
        add("  <key>Label</key>")
        add("  <string>$LABEL</string>")
        add("  <key>ProgramArguments</key>")
        add("  <array>")
        startWords.forEach { add("    <string>${escaped(it)}</string>") }
        add("  </array>")
        add("  <key>RunAtLoad</key>")
        add("  <true/>")
        add("</dict>")
        add("</plist>")
    }.joinToString(separator = "\n", postfix = "\n")

    fun registeredCommand(plistText: String): RegisteredCommand? {
        val arguments = plistText.substringAfter("<key>ProgramArguments</key>", "").substringBefore("</array>")
        return RegisteredCommand.of(STRING.findAll(arguments).map { unescaped(it.groupValues[1]) }.toList())
    }

    private fun escaped(text: String): String = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun unescaped(text: String): String = text.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
}
