package mixed.app

import mixed.core.Greeter
import mixed.core.PoliteGreeter

class Application(private val greeter: Greeter = PoliteGreeter()) {
    fun run(names: List<String>): List<String> = names.map { LegacyFormatter.format(greeter.greet(it)) }
}

fun main() {
    Application().run(listOf("Ada", "Grace")).forEach(::println)
}
