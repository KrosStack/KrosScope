package sample.tasktide.core.model

private const val MILLIS_PER_DAY = 86_400_000L
private const val EPOCH_SHIFT_DAYS = 719_468L
private const val DAYS_PER_ERA = 146_097L

fun Long.toIsoDate(): String {
    val shifted = floorDiv(MILLIS_PER_DAY) + EPOCH_SHIFT_DAYS
    val era = shifted.floorDiv(DAYS_PER_ERA)
    val dayOfEra = shifted - era * DAYS_PER_ERA
    val yearOfEra = (dayOfEra - dayOfEra / 1_460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
    val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
    val monthIndex = (5 * dayOfYear + 2) / 153
    val day = dayOfYear - (153 * monthIndex + 2) / 5 + 1
    val month = if (monthIndex < 10) monthIndex + 3 else monthIndex - 9
    val year = yearOfEra + era * 400 + if (month <= 2) 1 else 0
    return "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}
