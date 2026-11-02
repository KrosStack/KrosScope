package sample.tasktide.text

object Slugs {
    fun of(title: String): String = title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
}
