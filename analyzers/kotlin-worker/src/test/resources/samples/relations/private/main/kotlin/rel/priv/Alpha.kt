package rel.priv

private const val LIMIT = 10

private fun label(value: Int): String = "alpha-$value"

fun alphaTitle(): String = label(LIMIT)
