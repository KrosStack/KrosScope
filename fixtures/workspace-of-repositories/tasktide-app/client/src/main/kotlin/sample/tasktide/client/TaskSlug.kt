package sample.tasktide.client

import sample.tasktide.text.Slugs

fun taskSlug(title: String): String = Slugs.of(title)
