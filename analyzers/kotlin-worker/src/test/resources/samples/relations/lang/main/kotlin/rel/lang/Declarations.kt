package rel.lang

import kotlin.reflect.KClass

interface Shape {
    val area: Int

    fun scale(factor: Int): Shape
}

open class Base(val id: Int) {
    open fun hello(): Int = 1

    open val tag: String = "base"
}

class Child(id: Int, val extra: Int = 0) : Base(id), Shape {
    constructor() : this(0, 1)

    override val area: Int get() = extra

    override fun scale(factor: Int): Shape = Child(id * factor)

    override fun hello(): Int = super.hello() + 1
}

data class Point(val x: Int, var y: Int)

enum class Color(val rgb: Int) {
    RED(1),
    GREEN(2),
}

object Registry {
    const val LIMIT: Int = 10

    var count: Int = 0

    fun register(): Int = ++count
}

class Plain

typealias Names = List<String>

annotation class Marker(val target: KClass<*>, val level: Int = 1)

class Box<T>(val value: T) {
    operator fun get(index: Int): T = value

    operator fun plus(other: Box<T>): Box<T> = this

    operator fun iterator(): Iterator<T> = listOf(value).iterator()
}

fun over(value: Int): Int = value

fun over(value: String): String = value

val String.shoutLength: Int get() = length

fun String.shout(): String = uppercase()

infix fun Int.pair(other: Int): Pair<Int, Int> = this to other

fun helper(): Int = 1

class Invokable {
    operator fun invoke(): Int = 1
}

class Trigger(val action: () -> Int, val invokable: Invokable) {
    fun fire(): Int = action() + invokable()
}

class Backed {
    var amount: Int = 0
        get() = field + 1
        set(value) {
            field = value
        }
}

class Counter {
    companion object {
        const val START: Int = 1

        fun make(): Counter = Counter()
    }
}

class Holder(private val base: Base) : Base(base.id)
