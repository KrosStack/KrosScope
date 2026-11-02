package over

import kotlin.collections.Map as KMap

fun render(value: Int): String = value.toString()

fun render(value: String): String = value

fun render(value: List<String>?): String = value.orEmpty().joinToString()

fun render(vararg values: Int): String = values.joinToString()

fun render(block: () -> Unit) {
    block()
}

fun render(block: (Int) -> String, other: Int): String = block(other)

fun render(block: String.() -> Unit) {}

fun render(callback: suspend () -> Unit?) {}

fun render(index: java.util.Map<String, Int>) {}

fun render(values: KMap<String, Int>) {}

fun render(array: Array<out String>) {}

fun String.shout(): String = uppercase()

fun Int.shout(): String = toString()

fun List<String>?.shout(suffix: String): String = orEmpty().joinToString(suffix)

val String.shoutLength: Int
    get() = length

val Int.shoutLength: Int
    get() = this

var <T> List<T>.lastItem: T
    get() = last()
    set(value) {}

class Box<T>(private val value: T) {
    fun <R> map(transform: (T) -> R): Box<R> = Box(transform(value))

    operator fun get(index: Int): T = value

    fun List<T>.firstBox(): Box<T> = Box(first())

    val T.self: T
        get() = this
}

fun <T : Comparable<T>> List<T>.second(): T = sorted()[1]

fun `with spaces`(`odd name`: Int): Int = `odd name`
