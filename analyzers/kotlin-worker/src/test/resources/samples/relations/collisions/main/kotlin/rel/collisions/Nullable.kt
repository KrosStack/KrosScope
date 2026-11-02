package rel.collisions

fun pick(value: Int): String = plainSide()

fun pick(value: Int?): String = nullableSide()

fun plainSide(): String = "plain"

fun nullableSide(): String = "nullable"

fun callsNull(): String = pick(null)

fun callsInt(): String = pick(1)

fun unique(value: Int): String = plainSide()

fun callsUnique(): String = unique(2)
