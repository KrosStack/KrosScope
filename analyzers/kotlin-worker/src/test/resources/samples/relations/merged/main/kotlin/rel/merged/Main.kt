package rel.merged

class MainScreen {
    fun render(): String = DebugOverlay().label() + helperInMain()
}

fun helperInMain(): Int = 1

fun mainOnly(): Int = debugOnly()
