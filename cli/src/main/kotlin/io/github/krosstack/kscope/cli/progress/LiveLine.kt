package io.github.krosstack.kscope.cli.progress

/** The one status line of a terminal: each text replaces the one before, and the line can be wiped. */
internal class LiveLine(private val screen: ProgressScreen) {
    private var width = 0

    fun show(text: String) {
        val room = screen.columns - 1
        val line = if (text.length <= room) text else text.take(room - ELLIPSIS.length) + ELLIPSIS
        screen.write("\r" + line.padEnd(width))
        width = line.length
    }

    fun clear() {
        if (width == 0) return
        screen.write("\r" + " ".repeat(width) + "\r")
        width = 0
    }

    private companion object {
        const val ELLIPSIS = "..."
    }
}
