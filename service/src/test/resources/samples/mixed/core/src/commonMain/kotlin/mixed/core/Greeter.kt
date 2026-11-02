package mixed.core

expect fun platformName(): String

interface Greeter {
    fun greet(name: String): String
}

class PoliteGreeter(private val salutation: String = "Hello") : Greeter {
    override fun greet(name: String): String = "$salutation, $name from ${platformName()}"
}
