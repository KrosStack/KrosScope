package sample.tasktide.client

import sample.tasktide.format.DueDates

fun dueLabel(dueInDays: Int): String = DueDates.label(dueInDays)
