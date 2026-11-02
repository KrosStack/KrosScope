package rel.inherit

interface Repository<T> {
    fun load(id: Int): T

    fun count(): Int
}

abstract class BaseRepository<T> : Repository<T> {
    override fun count(): Int = 0

    abstract fun describe(): String

    override fun toString(): String = describe()
}

open class Named(open val name: String) : Comparable<Named> {
    override fun compareTo(other: Named): Int = name.compareTo(other.name)
}
