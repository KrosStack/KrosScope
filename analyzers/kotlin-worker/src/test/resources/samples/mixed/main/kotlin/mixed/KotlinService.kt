package mixed

import mixed.legacy.JavaSupport

/** Coordinates work between Kotlin and Java code. Does more than that. */
class KotlinService(private val support: JavaSupport) {
    var counter: Int = 0
        private set

    fun run(task: String): String {
        counter++
        return support.shout(task)
    }

    fun run(times: Int): Int = support.shout(times).length

    companion object {
        @JvmStatic
        fun create(): KotlinService = KotlinService(JavaSupport())
    }
}

fun twice(value: Int): Int = value * 2
