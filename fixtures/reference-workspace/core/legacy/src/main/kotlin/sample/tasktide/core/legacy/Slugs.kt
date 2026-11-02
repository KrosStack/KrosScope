package sample.tasktide.core.legacy

private val nonAlphanumeric = Regex("[^a-z0-9]+")

fun slugify(text: String): String = text.lowercase().replace(nonAlphanumeric, "-").trim('-')
