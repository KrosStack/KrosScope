package rel.mixed

class Service(val label: String, var counter: Int = 0) {
    constructor() : this("default")

    fun run(task: String): String = task

    fun run(times: Int): Int = times

    companion object {
        const val LIMIT: Int = 10

        @JvmStatic
        fun create(): Service = Service()

        fun instance(): Service = Service()
    }
}

object Registry {
    fun register(item: String): Int = item.length
}

enum class Mode {
    FAST,
    SLOW,
}

class Implicit

interface Handler {
    fun handle(input: String): Int
}

fun twice(value: Int): Int = value * 2

val String.extra: Int get() = length
