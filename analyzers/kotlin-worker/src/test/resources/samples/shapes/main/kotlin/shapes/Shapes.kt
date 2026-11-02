package shapes

typealias Names = List<String>

/** Marks code that is part of the public surface. */
annotation class Marker(val level: Int = 1, val label: String = "none", val active: Boolean = true)

enum class Color(val rgb: Int) {
    RED(0xFF0000),
    GREEN(0x00FF00) {
        override fun label(): String = "green"
    };

    open fun label(): String = name
}

object Registry {
    val items: MutableList<String> = mutableListOf()

    fun register(item: String) {
        items += item
    }
}

class Outer(private val seed: Int) {
    companion object {
        const val LIMIT = 10

        fun create(): Outer = Outer(1)
    }

    class Nested {
        fun run() {}
    }

    inner class Inner {
        fun read(): Int = seed
    }

    object Singleton {
        val id: Int = 7
    }

    interface Visitor {
        val depth: Int

        fun visit(node: Nested)

        fun finish(): Int = 0
    }
}

class Named {
    companion object Factory {
        fun make(): Named = Named()
    }
}

sealed interface Shape

data class Circle(val radius: Double) : Shape

fun interface Callback {
    fun call(value: Int)
}

@Marker(level = 2, label = "top")
class Annotated {
    @Marker(3)
    fun marked() {}

    @Deprecated("old")
    @Suppress("unused", "UNCHECKED_CAST")
    fun legacy() {}

    fun local() {
        class Hidden
        val anonymous = object {
            fun inner() {}
        }
        fun nested() {}
    }
}
