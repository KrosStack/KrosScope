package rel.kmp

expect class PlatformInfo() {
    val name: String

    fun describe(): String
}

expect fun currentPlatform(): String

expect object Clock {
    fun now(): Long
}

expect class Counter(start: Int) {
    fun next(): Int
}

interface Greeter {
    val prefix: String

    fun greet(who: String): String
}

open class BaseGreeter(override val prefix: String) : Greeter {
    override fun greet(who: String): String = "$prefix $who"
}

fun describeCurrent(): String = PlatformInfo().describe() + currentPlatform() + Clock.now()

fun greetAll(greeter: Greeter, names: List<String>): List<String> = names.map { greeter.greet(it) }
