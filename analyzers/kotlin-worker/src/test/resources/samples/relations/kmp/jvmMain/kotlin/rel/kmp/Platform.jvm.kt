package rel.kmp

actual class PlatformInfo actual constructor() {
    actual val name: String = "jvm"

    actual fun describe(): String = name
}

actual fun currentPlatform(): String = PlatformInfo().name

actual object Clock {
    actual fun now(): Long = System.currentTimeMillis()
}

actual class Counter actual constructor(private var start: Int) {
    actual fun next(): Int = start++
}

class LoudGreeter : BaseGreeter("!") {
    override fun greet(who: String): String = super.greet(who).uppercase()
}

fun jvmDescription(): String = describeCurrent() + currentPlatform() + Clock.now()
