package sample

/** The platform the code runs on. */
expect class PlatformInfo {
    val name: String

    fun describe(): String
}

expect fun currentPlatform(): String

expect object Clock {
    fun now(): Long
}

interface Greeter {
    fun greet(who: String): String
}
