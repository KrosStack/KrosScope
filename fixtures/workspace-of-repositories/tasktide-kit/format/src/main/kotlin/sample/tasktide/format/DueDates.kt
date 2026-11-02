package sample.tasktide.format

object DueDates {
    fun label(dueInDays: Int): String = when {
        dueInDays < 0 -> "overdue"
        dueInDays == 0 -> "due today"
        else -> "due in $dueInDays days"
    }
}
