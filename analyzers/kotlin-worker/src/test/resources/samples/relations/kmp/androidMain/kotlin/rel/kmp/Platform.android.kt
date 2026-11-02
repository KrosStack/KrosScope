package rel.kmp

actual class PlatformInfo actual constructor() {
    actual val name: String = "android"

    actual fun describe(): String = name
}

actual fun currentPlatform(): String = "android"

actual typealias Clock = AndroidClock

object AndroidClock {
    fun now(): Long = 0L
}

actual class Counter actual constructor(private var start: Int) {
    actual fun next(): Int = start++
}
