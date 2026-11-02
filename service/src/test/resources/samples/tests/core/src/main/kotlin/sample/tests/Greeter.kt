package sample.tests

class Greeter {
    fun greet(name: String): String = "Hello, $name"

    fun shout(name: String): String = greet(name).uppercase()
}
