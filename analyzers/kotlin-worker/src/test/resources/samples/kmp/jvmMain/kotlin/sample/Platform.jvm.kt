package sample

actual class PlatformInfo {
    actual val name: String = "jvm"

    actual fun describe(): String = "JVM"
}

actual fun currentPlatform(): String = "jvm"

actual object Clock {
    actual fun now(): Long = System.currentTimeMillis()
}

class JvmGreeter : Greeter {
    override fun greet(who: String): String = "Hello $who from ${currentPlatform()}"
}
