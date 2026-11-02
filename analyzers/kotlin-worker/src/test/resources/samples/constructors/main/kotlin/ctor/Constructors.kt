package ctor

class Plain

class Empty()

class WithProperties(val x: Int, var y: String = "", z: Double) {
    val derived: Int = x * 2
}

class Mixed private constructor(private val seed: Int) {
    constructor(seed: Int, extra: Int) : this(seed + extra)

    constructor() : this(0)

    protected constructor(name: String, vararg more: Int) : this(name.length)
}

class Injected @Inject constructor(@Named("a") val label: String)

annotation class Inject

annotation class Named(val value: String)

open class Parent(val id: Long)

class Child(id: Long, override val name: String = "child") : Parent(id), Described

interface Described {
    val name: String
}

data class Point(val x: Int, val y: Int)

enum class Level(val weight: Int) {
    LOW(1),
    HIGH(10);

    constructor() : this(0)
}
