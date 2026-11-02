package flags

abstract class Base {
    abstract fun required(): Int

    open fun optional() {}

    protected open val inherited: Int = 0

    internal fun hidden() {}

    private fun secret() {}

    suspend fun waiting(): Int = 0

    external fun native(): Int

    @Deprecated("use something else")
    fun old() {}

    final override fun toString(): String = "base"

    companion object {
        @JvmStatic
        fun shared() {}

        const val NAME = "base"
    }
}

interface Contract {
    val limit: Int

    val computed: Int
        get() = 1

    fun mandatory()

    fun provided() {}

    private fun internalHelper() {}
}

open class Impl : Contract {
    override val limit: Int = 3

    override fun mandatory() {}
}

internal class InternalOnly

private class FilePrivate
