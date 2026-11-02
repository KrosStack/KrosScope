package sample.cmp.app

import sample.cmp.resources.Res
import sample.cmp.resources.farewell
import sample.cmp.ui.Greeting

class Main {
    val greeting = Greeting()
    val bye = Res.string.farewell
    val file = Res.getUri("files/notes.txt")
}
