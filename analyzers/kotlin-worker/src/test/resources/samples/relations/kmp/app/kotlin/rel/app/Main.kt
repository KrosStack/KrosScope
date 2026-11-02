package rel.app

import rel.kmp.Clock
import rel.kmp.Counter
import rel.kmp.LoudGreeter
import rel.kmp.greetAll

fun main() {
    val greeter = LoudGreeter()
    greetAll(greeter, listOf("a"))
    Clock.now()
    Counter(1).next()
}
