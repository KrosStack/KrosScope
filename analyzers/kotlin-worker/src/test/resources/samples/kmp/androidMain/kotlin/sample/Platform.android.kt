package sample

actual class PlatformInfo {
    actual val name: String = "android"

    actual fun describe(): String = "Android"
}

actual fun currentPlatform(): String = "android"

actual typealias Clock = AndroidClock

object AndroidClock {
    fun now(): Long = 0L
}
