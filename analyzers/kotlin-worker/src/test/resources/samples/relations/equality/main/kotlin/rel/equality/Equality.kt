package rel.equality

data class Id(val value: Int)

@JvmInline
value class Handle(val raw: Long)

class Keyed(val key: Int) {
    override fun equals(other: Any?): Boolean = other is Keyed && other.key == key

    override fun hashCode(): Int = key
}

class Plain

class Comparisons {
    fun data(a: Id, b: Id): Boolean = a == b

    fun dataNegated(a: Id, b: Id): Boolean = a != b

    fun value(a: Handle, b: Handle): Boolean = a == b

    fun declared(a: Keyed, b: Keyed): Boolean = a == b

    fun declaredNegated(a: Keyed, b: Keyed): Boolean = a != b

    fun inherited(a: Plain, b: Plain): Boolean = a == b
}
