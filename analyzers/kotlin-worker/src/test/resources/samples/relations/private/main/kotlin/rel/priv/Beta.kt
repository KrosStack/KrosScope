package rel.priv

private const val LIMIT = 20

private fun label(value: Int): String = "beta-$value"

fun betaTitle(): String = label(LIMIT)
