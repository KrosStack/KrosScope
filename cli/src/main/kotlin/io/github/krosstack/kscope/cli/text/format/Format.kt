package io.github.krosstack.kscope.cli.text.format

import java.util.Locale

private const val PERCENT = 100L
private const val ELLIPSIS = "..."
private const val SCORE_PLACES = 3
private const val FINE_SCORE_PLACES = 4
private val WHITESPACE = Regex("\\s+")

/** Numbers and counts as a person reads them. The output does not depend on the default locale. */
internal object Format {
    fun count(value: Long): String = String.format(Locale.ROOT, "%,d", value)

    fun count(value: Int): String = count(value.toLong())

    fun decimal(value: Double, places: Int): String = String.format(Locale.ROOT, "%.${places}f", value)

    /** A ranking score, which is only meaningful next to the scores of the same result. */
    fun score(value: Double): String = decimal(value, SCORE_PLACES)

    fun fineScore(value: Double): String = decimal(value, FINE_SCORE_PLACES)

    fun noun(count: Long, singular: String, plural: String = "${singular}s"): String =
        "${count(count)} ${if (count == 1L) singular else plural}"

    fun noun(count: Int, singular: String, plural: String = "${singular}s"): String =
        noun(count.toLong(), singular, plural)

    fun percent(done: Long, total: Long): String = "${if (total <= 0) 0 else done * PERCENT / total}%"

    /** [text] on one line, cut to [limit] characters with an ellipsis. */
    fun clip(text: String, limit: Int): String {
        val oneLine = text.trim().replace(WHITESPACE, " ")
        return if (oneLine.length <= limit) oneLine else oneLine.take(limit - ELLIPSIS.length).trimEnd() + ELLIPSIS
    }

    /** At most [limit] of [items], then how many more there are, as one line. */
    fun listing(items: List<String>, limit: Int): String = if (items.size <= limit) {
        items.joinToString(", ")
    } else {
        items.take(limit).joinToString(", ") + ", +${items.size - limit} more"
    }
}
