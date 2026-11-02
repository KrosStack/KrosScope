package rel.merged

class DebugOverlay {
    fun label(): String = "debug"

    fun show(screen: MainScreen): String = screen.render() + helperInMain()
}

fun debugOnly(): Int = mainOnly()
