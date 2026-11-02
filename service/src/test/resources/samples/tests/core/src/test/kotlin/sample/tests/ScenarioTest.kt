package sample.tests

import org.junit.Test

class ScenarioTest {
    @Test
    fun everythingTogether() {
        val greeter = Greeter()
        greeter.greet("a")
        greeter.shout("b")
        Formatter().pad("c")
    }
}
