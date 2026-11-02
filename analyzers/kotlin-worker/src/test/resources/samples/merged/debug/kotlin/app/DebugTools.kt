package app

class DebugOverlay {
    fun show(screen: MainScreen): String = screen.render()
}

fun shared(): Int = 2

fun debugOnly(): Boolean = true
